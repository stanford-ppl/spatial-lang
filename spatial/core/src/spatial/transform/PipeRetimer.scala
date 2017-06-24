package spatial.transform

import argon.core._
import argon.nodes._
import argon.transform.ForwardTransformer
import spatial.analysis.ModelingTraversal
import spatial.aliases._
import spatial.metadata._
import spatial.models._
import spatial.nodes._
import spatial.utils._
import spatial.SpatialConfig

import scala.collection.immutable.SortedSet

trait PipeRetimer extends ForwardTransformer with ModelingTraversal {
  override val name = "Pipeline Retimer"
  override def shouldRun = !SpatialConfig.enablePIR

  var retimeBlocks: List[Boolean] = Nil
  var ctx: Option[SrcCtx] = None

  def requiresRegisters(x: Exp[_]) = latencyModel.requiresRegisters(x)
  def retimingDelay(x: Exp[_]): Int = if (requiresRegisters(x)) latencyOf(x).toInt else 0

  def bitBasedInputs(d: Def): Seq[Exp[_]] = exps(d).filterNot(isGlobal(_)).filter{e => Bits.unapply(e.tp).isDefined }.distinct

  def delayLine[T](size: Int, data: Exp[T])(implicit ctx: SrcCtx): Exp[T] = data.tp match {
    case Bits(bits) =>
      implicit val mT = data.tp
      implicit val bT = bits.asInstanceOf[Bits[T]]
      val line = Delays.delayLine[T](size, data)
      line.name = data.name.map{_ + s"_d$size"}
      line
    case _ => throw new Exception("Unexpected register type")
  }

  case class ValueDelay(input: Exp[_], delay: Int, size: Int, hierarchy: Int, private val create: () => Exp[_]) {
    private var reg: Option[Exp[_]] = None
    def value() = if (reg.isDefined) reg.get else { val r = create(); reg = Some(r); r }
  }
  implicit object ValueDelayOrdering extends Ordering[ValueDelay] {
    override def compare(x: ValueDelay, y: ValueDelay) = implicitly[Ordering[Int]].compare(y.delay,x.delay)
  }

  private def retimeBlock[T:Type](block: Block[T])(implicit ctx: SrcCtx): Exp[T] = {
    val result = exps(block)
    val scope = blockNestedContents(block).flatMap(_.lhs)
                  .filterNot(s => isGlobal(s))
                  .filter{e => e.tp == UnitType || Bits.unapply(e.tp).isDefined }
                  .map(_.asInstanceOf[Exp[_]]).toSet

    // The position AFTER the given node
    val latencies = pipeLatencies(result, scope)._1
    def delayOf(x: Exp[_]): Int = latencies.getOrElse(x, 0L).toInt

    dbgs("")
    dbgs("")
    dbgs("Sym Delays:")
    latencies.foreach{case (s,l) =>
      symDelay(s) = l - latencyOf(s)
      dbgs(c"  [${l-latencyOf(s)}]: ${str(s)}")
    }

    var delayLines: Map[Exp[_], SortedSet[ValueDelay]] = Map.empty
    var delayConsumers: Map[Exp[_], List[ValueDelay]] = Map.empty
    var hierarchy: Int = 0

    def registerDelays(reader: Exp[_], inputs: Seq[Exp[_]]): Unit = {
      delayConsumers.getOrElse(reader, Nil)
        .filter{line => inputs.contains(line.input) }
        .foreach{line => register(line.input, line.value()) }
    }

    def addDelayLine(input: Exp[_], reader: Exp[_], delay: Int) = {
      if (delay < 0) {
        error("Compiler bug? Attempting to create a negative delay between input: ")
        error(c"  ${str(input)}")
        error("and consumer: ")
        error(c"  ${str(reader)}")
        state.logError()
      }

      val existing = delayLines.getOrElse(input, SortedSet[ValueDelay]())
      val line: ValueDelay = existing.find{_.delay <= delay} match {
        case Some(prev) =>
          // If this statement references delay lines in an outer scope, force creation of
          // the outer delay lines *now* to avoid issues with lazily creating delay lines
          // in inner blocks that are also used in outer blocks
          if (prev.hierarchy < hierarchy) prev.value()

          val size = delay - prev.delay
          if (size > 0) {
            ValueDelay(input, delay, size, hierarchy, () => delayLine(size, prev.value())(input.ctx))
          } else prev

        case None =>
          ValueDelay(input, delay, delay, hierarchy, () => delayLine(delay, f(input))(input.ctx))
      }
      delayLines += input -> (existing + line)

      val prevReadByThisReader = delayConsumers.getOrElse(reader, Nil)
      delayConsumers += reader -> (line +: prevReadByThisReader)
    }

    def computeDelayLines(block: Block[_]) = {
      val consumerDelays = blockContents(block).flatMap{case TP(reader, d) =>
        val criticalPath = delayOf(reader) - latencyOf(reader)  // All inputs should arrive at this offset
        // Ignore non-bit based values
        val inputs = bitBasedInputs(d)

        dbgs(c"${str(reader)}: ${delayOf(reader)}")
        dbgs(c"  " + inputs.map{in => c"in: ${delayOf(in)}"}.mkString(", ") + "[max: " + criticalPath + "]")
        inputs.flatMap{in =>
          val delay = retimingDelay(in) + criticalPath - delayOf(in)
          dbgs(c"  ${str(in)} [delay: $delay]")
          if (delay != 0) Some(in -> (reader, delay)) else None
        }
      }
      val inputDelays = consumerDelays.groupBy(_._1).mapValues(_.map(_._2))
      inputDelays.foreach{case (input, consumers) =>
        val consumerGroups = consumers.groupBy(_._2).mapValues(_.map(_._1))
        val delays = consumerGroups.keySet.toList.sorted  // Presort to maximize coalescing
        delays.foreach{delay =>
          val readers = consumerGroups(delay)
          readers.foreach{reader => addDelayLine(input, reader, delay.toInt) }
        }
      }
    }

    def inBlock[A](block: Block[_])(func: => A): A = {
      val prevDelayLines = delayLines
      val prevDelayConsumers = delayConsumers
      hierarchy += 1

      computeDelayLines(block)
      val result = func

      hierarchy -= 1
      delayLines = prevDelayLines
      delayConsumers = prevDelayConsumers
      result
    }

    def retimeSwitch[A:Type](switch: Exp[A], op: Switch[A]): Unit = {
      val Switch(body, selects, cases) = op
      dbgs(c"Retiming switch $switch = $op")
      val body2 = stageHotBlock {
        cases.foreach{
          case cas @ Op(SwitchCase(caseBody)) =>
            implicit val ctx: SrcCtx = cas.ctx
            dbgs(c"Retiming case ${str(cas)}")
            val caseBody2: Block[A] = isolateSubstScope { stageSealedBlock {
              retimeStms(caseBody)
              val size = delayConsumers.getOrElse(switch, Nil).find(_.input == cas).map(_.size)
              if (size.isDefined && size.get > 0) {
                dbgs(s"Adding retiming delay of size $size at end of case $cas")
                delayLine(size.get, f(caseBody.result))
              }
              else {
                dbgs(s"No retiming delay required for case $cas")
                f(caseBody.result)
              }
            }}
            val effects = caseBody2.effects andAlso Simple
            val cas2 = stageEffectful(SwitchCase(caseBody2), effects)(ctx)
            transferMetadata(cas, cas2)
            register(cas -> cas2)
        }
        f(cases.last)
      }
      val switch2 = isolateSubstScope {
        registerDelays(switch, selects)
        implicit val ctx: SrcCtx = switch.ctx
        Switches.op_switch(body2, f(selects), f(cases))
      }
      transferMetadata(switch, switch2)
      register(switch -> switch2)
    }

    def retimeStms[A:Type](block: Block[A]): Exp[A] = inBlock(block) {
      inlineBlockWith(block, {stms =>
        stms.foreach{
          case TP(switch, op: Switch[_]) =>
            retimeSwitch(switch, op)(mtyp(switch.tp))

          case stm @ TP(reader, d) =>
            val inputs = bitBasedInputs(d)
            val reader2 = isolateSubstScope {
              registerDelays(reader, inputs)
              visitStm(stm)
              f(reader)
            }
            register(reader -> reader2)
        }

        f(block.result)
      })
    }

    isolateSubstScope{ retimeStms(block) }
  }


  def withRetime[A](wrap: List[Boolean], srcCtx: SrcCtx)(x: => A) = {
    val prevRetime = retimeBlocks
    val prevCtx = ctx

    retimeBlocks = wrap
    ctx = Some(srcCtx)
    val result = x

    retimeBlocks = prevRetime
    ctx = prevCtx
    result
  }

  override protected def inlineBlock[T](b: Block[T]): Exp[T] = {
    val doWrap = retimeBlocks.headOption.getOrElse(false)
    if (retimeBlocks.nonEmpty) retimeBlocks = retimeBlocks.drop(1)
    dbgs(c"Transforming Block $b [$retimeBlocks]")
    if (doWrap) {
      retimeBlock(b)(mtyp(b.tp),ctx.get)
    }
    else super.inlineBlock(b)
  }

  private def transformCtrl[T:Type](lhs: Sym[T], rhs: Op[T])(implicit ctx: SrcCtx): Exp[T] = {
    // Switches aren't technically inner controllers from PipeRetimer's point of view.
    if (isInnerControl(lhs) && !isSwitch(rhs)) {
      val retimeEnables = rhs.blocks.map{_ => true }.toList
      withRetime(retimeEnables, ctx) { super.transform(lhs, rhs) }
    }
    else super.transform(lhs, rhs)
  }

  override def transform[T:Type](lhs: Sym[T], rhs: Op[T])(implicit ctx: SrcCtx): Exp[T] = rhs match {
    case _:Hwblock =>
      inHwScope = true
      val lhs2 = transformCtrl(lhs, rhs)
      inHwScope = false
      lhs2

    case _ => transformCtrl(lhs, rhs)
  }

}
