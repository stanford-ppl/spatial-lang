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

import scala.collection.immutable.SortedSet

case class PipeRetimer(var IR: State, latencyModel: LatencyModel) extends ForwardTransformer with ModelingTraversal {
  override val name = "Pipeline Retimer"
  override def shouldRun = !spatialConfig.enablePIR

  var retimeBlocks: List[Boolean] = Nil
  var ctx: Option[SrcCtx] = None

  var delayLines: Map[Exp[_], SortedSet[ValueDelay]] = Map.empty
  var delayConsumers: Map[Exp[_], List[ValueDelay]] = Map.empty
  var latencies: Map[Exp[_], Double] = Map.empty
  var cycles: Set[Exp[_]] = Set.empty
  var hierarchy: Int = 0
  var inInnerScope: Boolean = false

  def delayOf(x: Exp[_]): Double = latencies.getOrElse(x, 0.0)

  def inBlock[A](block: Block[_])(func: => A): A = {
    val prevDelayLines = delayLines
    val prevDelayConsumers = delayConsumers
    val prevLatencies = latencies
    val prevCycles = cycles
    val prevInnerScope = inInnerScope
    hierarchy += 1
    inInnerScope = true

    val scope = blockContents(block) //.filter(_.lhs.exists(e => Bits.unapply(e.tp).isDefined || e.tp == UnitType))
    val lines = computeDelayLines(scope)
    lines.foreach{case (reader, line) => addDelayLine(reader, line) }

    val result = func

    hierarchy -= 1
    delayLines = prevDelayLines
    delayConsumers = prevDelayConsumers
    latencies = prevLatencies
    cycles = prevCycles
    inInnerScope = prevInnerScope
    result
  }

  def scrubNoise(x: Double): Double = {if ( (x*1000) % 1 == 0) x else if ( (x*1000) % 1 < 0.5) (x*1000).toInt.toDouble/1000.0 else ((x*1000).toInt + 1).toDouble/1000.0 } // Round out to nearest 1/1000 because numbers like 1.1999999997 - 0.2 < 1.0 and screws things up
  def requiresRegisters(x: Exp[_]): Boolean = latencyModel.requiresRegisters(x, cycles.contains(x))
  def retimingDelay(x: Exp[_]): Double = if (requiresRegisters(x)) latencyOf(x, cycles.contains(x)) else 0.0

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

  case class ValueDelay(input: Exp[_], delay: Int, size: Int, hierarchy: Int, prev: Option[ValueDelay], private val create: () => Exp[_]) {
    private var reg: Option[Exp[_]] = None
    def alreadyExists: Boolean = reg.isDefined

    def value(): Exp[_] = {
      if (reg.isDefined) {
        val r = reg.get
        //dbgs(s"Exist delay line ${str(r)}")
        r
      }
      else {
        val r = create()
        reg = Some(r)
        //dbgs(s"Create delay line ${str(r)}")
        r
      }
    }
  }
  implicit object ValueDelayOrdering extends Ordering[ValueDelay] {
    override def compare(x: ValueDelay, y: ValueDelay): Int = implicitly[Ordering[Int]].compare(y.delay,x.delay)
  }

  private def registerDelays(reader: Exp[_], inputs: Seq[Exp[_]]): Unit = {
    delayConsumers.getOrElse(reader, Nil)
      .filter{line => inputs.contains(line.input) }
      .map{line => line.input -> line }
      .toMap    // Unique-ify by line input - makes sure we only register one substitution per input
      .foreach{case (in, line) =>
        val dly = line.value()
        logs(s"  $in -> $dly [${line.size}]")
        register(in, dly)
      }
  }

  private def addDelayLine(reader: Exp[_], line: ValueDelay) = {
    logs(s"Add {in:${line.input}, reader:$reader, delay:${line.delay}, size:${line.size}")

    val existing = delayLines.getOrElse(line.input, SortedSet[ValueDelay]())

    delayLines += line.input -> (existing + line)

    val prevReadByThisReader = delayConsumers.getOrElse(reader, Nil)
    delayConsumers += reader -> (line +: prevReadByThisReader)
  }

  private def computeDelayLines(scope: Seq[Stm]): Seq[(Exp[_], ValueDelay)] = {
    val innerScope = scope.flatMap(_.rhs.blocks.flatMap(blk => exps(blk))).toSet

    def createValueDelay(input: Exp[_], reader: Exp[_], delay: Int): ValueDelay = {
      if (delay < 0) {
        bug("Compiler bug? Attempting to create a negative delay between input: ")
        bug(c"  ${str(input)}")
        bug("and consumer: ")
        bug(c"  ${str(reader)}")
        state.logBug()
      }
      // Retime inner block results as if we were already in the inner hierarchy
      val h = if (innerScope.contains(input)) hierarchy + 1 else hierarchy
      val existing = delayLines.getOrElse(input, SortedSet[ValueDelay]())
      existing.find{_.delay <= delay} match {
        case Some(prev) =>
          val size = delay - prev.delay
          if (size > 0) {
            logs(s"    Extending existing line of ${prev.delay}")
            ValueDelay(input, delay, size, h, Some(prev), () => delayLine(size, prev.value())(input.ctx))
          }
          else {
            logs(s"    Using existing line of ${prev.delay}")
            prev
          }

        case None =>
          logs(s"    Created new delay line of $delay")
          ValueDelay(input, delay, delay, h, None, () => delayLine(delay, f(input))(input.ctx))
      }
    }

    val consumerDelays = scope.flatMap{case TP(reader, d) =>
      val inReduce = cycles.contains(reader)
      val criticalPath = scrubNoise(delayOf(reader) - latencyOf(reader, inReduce))  // All inputs should arrive at this offset

      // Ignore non-bit based values
      val inputs = bitBasedInputs(d) //diff d.blocks.flatMap(blk => exps(blk))

      dbgs(c"[$criticalPath = ${delayOf(reader)} - ${latencyOf(reader,inReduce)}] ${str(reader)}")
      //logs(c"  " + inputs.map{in => c"in: ${delayOf(in)}"}.mkString(", ") + "[max: " + criticalPath + "]")
      inputs.flatMap{in =>
        val latency_required = scrubNoise(criticalPath)    // Target latency required upon reaching this reader
        val latency_achieved = scrubNoise(delayOf(in))                       // Latency already achieved at the output of this in (assuming latency_missing is already injected)
        val latency_missing  = scrubNoise(retimingDelay(in) - builtInLatencyOf(in))                                   // Latency of this input that still requires manual register injection
        val latency_actual   = scrubNoise(latency_achieved - latency_missing)
        val delay = latency_required.toInt - latency_actual.toInt
        dbgs(c"..[${latency_required - latency_actual} (-> ${delay}) = ${latency_required} - (${latency_achieved} - ${latency_missing}) (-> ${latency_required.toInt} - ${latency_actual.toInt})] ${str(in)}")
        if (delay.toInt != 0) Some(in -> (reader, delay.toInt)) else None
      }
    }
    val inputDelays = consumerDelays.groupBy(_._1).mapValues(_.map(_._2)).toSeq
    inputDelays.flatMap{case (input, consumers) =>
      val consumerGroups = consumers.groupBy(_._2).mapValues(_.map(_._1))
      val delays = consumerGroups.keySet.toList.sorted  // Presort to maximize coalescing
      delays.flatMap{delay =>
        val readers = consumerGroups(delay)
        readers.map{reader =>
          dbgs(c"  Creating value delay on $input for reader $reader with delay $delay: ")
          logs(s"  Creating value delay on $input for reader $reader with delay $delay: ")
          reader -> createValueDelay(input, reader, delay.toInt)
        }
      }
    }
  }

  // This is needed to allow multiple blocks to use the same delay line.
  // Delay lines are created lazily, which can lead to issues where multiple blocks claim the same symbol.
  // To avoid this, we instead create the symbol in the outermost block
  // Since we don't control when stageBlock is called, we have to do this before symbol mirroring.
  // Note that this requires checking *blockNestedContents*, not just blockContents
  private def precomputeDelayLines(d: Def): Unit = {
    hierarchy += 1
    if (d.blocks.nonEmpty) logs(s"  Precomputing delay lines for $d")
    d.blocks.foreach{block =>
      val scope = blockNestedContents(block)
      val lines = computeDelayLines(scope).map(_._2)
      // Create all of the lines that need to be visible outside this block
      (lines ++ lines.flatMap(_.prev)).filter(_.hierarchy < hierarchy).foreach{line =>
        if (!line.alreadyExists) {
          val dly = line.value()
          logs(s"    ${line.input} -> $dly [size: ${line.size}, h: ${line.hierarchy}] (cur h: $hierarchy)")
        }
      }
    }
    hierarchy -= 1
  }
  override def mirror(lhs: Seq[Sym[_]], rhs: Def): Seq[Exp[_]] = {
    if (inInnerScope) precomputeDelayLines(rhs)
    super.mirror(lhs, rhs)
  }



  private def retimeStms[A:Type](block: Block[A]): Exp[A] = inBlock(block) {
    inlineBlockWith(block, {stms =>
      stms.foreach{
        case TP(switch, op: Switch[_]) => retimeSwitch(switch, op)(mtyp(switch.tp))
        case stm => retimeStm(stm)
      }
      f(block.result)
    })
  }

  private def retimeStm(stm: Stm): Unit = stm match {
    case TP(reader, d) =>
      logs(s"Retiming $reader = $d")
      val inputs = bitBasedInputs(d)
      val reader2 = isolateSubstScope {
        registerDelays(reader, inputs)
        visitStm(stm)
        f(reader)
      }
      logs(s"  => ${str(reader2)}")
      register(reader -> reader2)
  }

  private def retimeSwitchCase[A:Type](cas: Exp[A], op: SwitchCase[A], switch: Exp[A]): Unit = {
    val SwitchCase(body) = op
    implicit val ctx: SrcCtx = cas.ctx
    precomputeDelayLines(op)
    dbgs(c"Retiming case ${str(cas)}")
    // Note: Don't call inBlock here - it's already being called in retimeStms
    val caseBody2: Block[A] = isolateSubstScope { stageSealedBlock {
      retimeStms(body)
      val size = delayConsumers.getOrElse(switch, Nil).find(_.input == cas).map(_.size).getOrElse(0) +
                 delayConsumers.getOrElse(cas, Nil).find(_.input == body.result).map(_.size).getOrElse(0)
      if (size > 0) {
        dbgs(s"Adding retiming delay of size $size at end of case $cas")
        delayLine(size, f(body.result))
      }
      else {
        dbgs(s"No retiming delay required for case $cas")
        f(body.result)
      }
    }}
    val effects = caseBody2.effects andAlso Simple
    val cas2 = stageEffectful(SwitchCase(caseBody2), effects)(ctx)
    transferMetadata(cas, cas2)
    register(cas -> cas2)
  }

  private def retimeSwitch[A:Type](switch: Exp[A], op: Switch[A]): Unit = {
    val Switch(body, selects, cases) = op
    precomputeDelayLines(op)
    dbgs(c"Retiming switch $switch = $op")
    val body2 = inBlock(body){ stageHotBlock {
      inlineBlockWith(body, {stms =>
        stms.foreach{
          case TP(cas, sc: SwitchCase[_]) => retimeSwitchCase(cas, sc, switch)(mtyp(sc.mT))
          case stm => retimeStm(stm)
        }
        f(body.result)
      })
    }}
    val switch2 = isolateSubstScope {
      registerDelays(switch, selects)
      implicit val ctx: SrcCtx = switch.ctx
      Switches.op_switch(body2, f(selects), f(cases))
    }
    transferMetadata(switch, switch2)
    register(switch -> switch2)
  }

  private def retimeBlock[T:Type](block: Block[T])(implicit ctx: SrcCtx): Exp[T] = {
    val scope = blockNestedContents(block).flatMap(_.lhs)
                  .filterNot(s => isGlobal(s))
                  //.filter{e => e.tp == UnitType || Bits.unapply(e.tp).isDefined }
                  .map(_.asInstanceOf[Exp[_]]).toSet

    val result = (block +: scope.toSeq.flatMap{case s@Def(d) => d.blocks; case _ => Nil}).flatMap{b => exps(b) }

    dbgs(s"Retiming block $block:")
    //scope.foreach{e => dbgs(s"  ${str(e)}") }
    //dbgs(s"Result: ")
    //result.foreach{e => dbgs(s"  ${str(e)}") }
    // The position AFTER the given node
    val (newLatencies, newCycles) = pipeLatencies(result, scope)
    latencies ++= newLatencies
    cycles ++= newCycles.flatMap(_.symbols)

    newLatencies.toList.sortBy(_._2).foreach{case (s,l) =>
      dbgs(s"[$l] ${str(s)}")
    }

    dbgs("")
    dbgs("")
    dbgs("Sym Delays:")
    newLatencies.toList.map{case (s,l) => s -> (scrubNoise(l - latencyOf(s, inReduce = cycles.contains(s)))) }
                       .sortBy(_._2)
                       .foreach{case (s,l) =>
                         symDelay(s) = l
                         dbgs(c"  [$l = ${newLatencies(s)} - ${latencyOf(s, inReduce = cycles.contains(s))}]: ${str(s)} [cycle = ${cycles.contains(s)}]")
                       }

    isolateSubstScope{ retimeStms(block) }
  }


  def withRetime[A](wrap: List[Boolean], srcCtx: SrcCtx)(x: => A): A = {
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
    dbgs(c"Transforming Block $b [$retimeBlocks => $doWrap]")
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
    else rhs match {
      case _:StateMachine[_] => withRetime(List(true,false,true), ctx){ super.transform(lhs, rhs) }
      case _ => withRetime(Nil, ctx){ super.transform(lhs, rhs) }
    }
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
