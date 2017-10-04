package spatial.codegen.pirgen

import argon.core._
import argon.nodes._
import argon.transform.ForwardTransformer
import spatial.aliases._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._

import scala.collection.mutable

trait PIRHackyRetimer extends ForwardTransformer with PIRHackyModelingTraversal { retimer =>
  override val name = "Hacky PIR Retimer"
  override def shouldRun = spatialConfig.enablePIRSim

  def requiresRetiming(x: Exp[_], inReduce: Boolean): Boolean = latencyModel.requiresRegisters(x, inReduce)
  def retimingDelay(x: Exp[_], inReduce: Boolean): Int = {
    if (latencyModel.requiresRegisters(x, inReduce)) latencyOf(x).toInt else 0
  }

  // track register info for each retimed reader
  // size represents the total buffer size between reader and input symbol
  // if buffers are split, the size of the register for this reader may actually be smaller
  class ReaderInfo(val size: Int) {
    // register read IR node (not strictly necessary to have only one but it avoids bloating the IR with redundant reads)
    var read: Exp[_] = _
  }

  def valueDelay[T](size: Int, data: Exp[T])(implicit ctx: SrcCtx): Exp[T] = data.tp match {
    case Bits(bits) =>
      implicit val mT: Type[T] = data.tp
      implicit val bT: Bits[T] = bits.asInstanceOf[Bits[T]]
      Delays.delayLine[T](size, data)
    case _ => throw new Exception("Unexpected register type")

  }

  // track reader dependencies associated with an input
  class InputInfo {
    // map a reader symbol to the buffer it will read from
    val readers = mutable.HashMap[Exp[_], ReaderInfo]()
    // group readers by the size of the register they read from
    object RegSizeOrdering extends Ordering[Int] { def compare(a: Int, b: Int) = b compare a }

    // retime symbol readers, sharing allocated buffers when possible
    def retimeReaders[U](input: Exp[U]) {
      // group and sort all the register sizes dependent symbols read from
      val readerGroups: Map[Int,List[Exp[_]]] = readers.toList.groupBy(_._2.size).mapValues(_.map(_._1))

      val sizes = readerGroups.keySet.toList.sorted
      // calculate register allocation sizes after coalescing
      val sizesCoalesced = (0 :: sizes).sliding(2).map{ case List(a, b) => b - a}.toList
      // map a reader's total register size to the size of the immediate register it will read from after coalescing
      val regSizeMap = sizes.zip(sizesCoalesced).toMap

      dbgs(c"Allocating registers for node $input:")
      val regReads = mutable.ListBuffer[Exp[_]](input)
      // add sequential reads/writes between split registers after coalescing
      sizes.foreach{ totalSize =>
        implicit val ctx: SrcCtx = input.ctx

        val readersList = readerGroups(totalSize)
        val size = regSizeMap(totalSize) // Look up mapping for this size
        val read = valueDelay(size, f(regReads.last))

        readersList.foreach{ reader =>
          // dbgs(c"  Register: ${str(reg)}, size: $size, reader: $reader")
          dbgs(c"  Delay: $size, reader: $reader")
          // readers(reader).reg = reg
          readers(reader).read = read
        }
        regReads += read
      }
    }
  }

  private def retimeReduce[T:Type](block: Block[T])(implicit ctx: SrcCtx): Exp[T] = inlineBlockWith(block, {stms =>
    stms.foreach{
      case stm@TP(lhs, rhs) if Bits.unapply(lhs.tp).isDefined =>
        visitStm(stm)
        val delay = valueDelay(1, f(lhs))(lhs.ctx)
        register(lhs -> delay)

      case stm => visitStm(stm)
    }
    val result = typ[T] match { case UnitType => unit; case _ => f(block.result) }
    result.asInstanceOf[Exp[T]]
  })


  private def retimeBlock[T:Type](block: Block[T], cchain: Option[Exp[CounterChain]])(implicit ctx: SrcCtx): Exp[T] = inlineBlockWith(block, {stms =>
    dbgs(c"Retiming block $block")

    // perform recursive search of inputs to determine cumulative symbol latency
    val (symLatency,delays) = pipeDelaysHack(block, cchain)
    def delayOf(x: Exp[_]): Int = symLatency.getOrElse(x, 0L).toInt

    symLatency.foreach{case (s,l) => dbgs(c"  ${str(s)} [$l]")}

    dbgs("Calculating delays for each node: ")
    // enumerate symbol reader dependencies and calculate required buffer sizes
    val inputRetiming = mutable.HashMap[Exp[_], InputInfo]()
    stms.foreach{ case TP(reader, d) =>
      dbgs(c"${str(reader)}")
      // Ignore non-bit based types and constants
      val inputs = exps(d).filterNot(isGlobal(_)).filter{e => e.tp == UnitType || Bits.unapply(e.tp).isDefined }
      val inputLatencies = inputs.map{sym => delayOf(sym) }

      val criticalPath = if (inputLatencies.isEmpty) 0 else inputLatencies.max

      dbgs("  " + inputs.zip(inputLatencies).map{case (in, latency) => c"$in: $latency"}.mkString(", ") + s" (max: $criticalPath)")

      // calculate buffer register size for each input symbol
      val sizes = inputs.zip(inputLatencies).map{case (in, latency) => delays.getOrElse(in,0L).toInt + criticalPath - latency }

      // discard symbols for which no register insertion is needed
      val inputsSizes = inputs.zip(sizes).filter{ case (_, size) => size != 0 }
      inputsSizes.foreach{ case (input, size) =>
        dbgs(c"  ${str(input)} [size: $size]}")
        inputRetiming.getOrElseUpdate(input, new InputInfo()).readers.getOrElseUpdate(reader, new ReaderInfo(size))
      }
    }

    // record which inputs have been buffered so retiming occurs only once
    val retimedInputs = mutable.Set[Exp[_]]()
    // traverse the IR, inserting registers allocated above
    stms.foreach{ case stm @ TP(reader, d) =>
      // save substitution rules for restoration after transformation
      val subRules = mutable.Map[Exp[_], Exp[_]]()

      val inputs = syms(d.inputs)
      inputs.foreach{ input =>
        // input might not need any buffers if its readers don't need to be retimed
        if (inputRetiming.contains(input)) {
          // retime all readers of this input and mark the input itself as retimed
          if (!retimedInputs.contains(input)) {
            inputRetiming(input).retimeReaders(input)
            retimedInputs += input
          }
          // insert buffer register for this reader
          if (inputRetiming(input).readers.contains(reader)) {
            val info = inputRetiming(input).readers(reader)
            dbgs(c"Buffering input $input to reader $reader")
            subRules(input) = transformExp(input)(mtyp(input.tp))
            register(input, info.read)
          }
        }
      }
      visitStm(stm)
      // restore substitution rules since future instances of this input may not be retimed in the same way
      subRules.foreach{ case (a, b) => register(a,b) }
    }
    if (delays.getOrElse(block.result,0L) > 0) {
      val delay = valueDelay(delays(block.result).toInt, f(block.result))(block.result.ctx)
      register(block.result -> delay)
    }

    val result = typ[T] match { case UnitType => unit; case _ => f(block.result) }
    result.asInstanceOf[Exp[T]]
  })



  var retimeBlocks: List[Boolean] = Nil
  var retimeReduce: List[Boolean] = Nil
  var ctx: Option[SrcCtx] = None
  var cchain: Option[Exp[CounterChain]] = None

  def withRetime[A](wrap: List[Boolean], reduce: List[Boolean], srcCtx: SrcCtx, cc: Option[Exp[CounterChain]])(x: => A) = {
    val prevRetime = retimeBlocks
    val prevReduce = retimeReduce
    val prevCtx = ctx
    val prevChain = cchain

    retimeBlocks = wrap
    retimeReduce = reduce
    ctx = Some(srcCtx)
    cchain = cc
    val result = x

    retimeBlocks = prevRetime
    retimeReduce = prevReduce
    ctx = prevCtx
    cchain = prevChain
    result
  }

  override protected def inlineBlock[T](b: Block[T]): Exp[T] = {
    val doWrap = retimeBlocks.headOption.getOrElse(false)
    val doWrapReduce = retimeReduce.headOption.getOrElse(false)
    if (retimeBlocks.nonEmpty) retimeBlocks = retimeBlocks.drop(1)
    if (retimeReduce.nonEmpty) retimeReduce = retimeReduce.drop(1)
    dbgs(c"Transforming Block $b [$retimeBlocks]")
    if (doWrapReduce) {
      retimeReduce(b)(mtyp(b.tp),ctx.get)
    }
    else if (doWrap) {
      retimeBlock(b,cchain)(mtyp(b.tp),ctx.get)
    }
    else super.inlineBlock(b)
  }

  private def transformCtrl[T:Type](lhs: Sym[T], rhs: Op[T])(implicit ctx: SrcCtx): Exp[T] = rhs match {
    case op: OpReduce[_] if isInnerControl(lhs) =>
      val cchain = Some(op.cchain)
      // TODO: Fix retiming of map part of this
      val retimeEnables = List(true,false,false,false)
      val reduceEnables = List(false,false,true,false)
      withRetime(retimeEnables, reduceEnables, ctx, cchain) { super.transform(lhs, rhs) }

    case op: OpMemReduce[_,_] =>
      val cchain = None
      val retimeEnables = List(false,false,false,false,false)
      val reduceEnables = List(false,false,false,true,false)
      withRetime(retimeEnables, reduceEnables, ctx, cchain) { super.transform(lhs, rhs) }

    case _ if isInnerControl(lhs) =>
      val cchain = rhs.expInputs.find{case cc@Def(_:CounterChainNew) => true; case _ => false}.map(_.asInstanceOf[Exp[CounterChain]])
      val retimeEnables = rhs.blocks.map{_ => true }.toList
      val reduceEnables = rhs.blocks.map{_ => false }.toList
      withRetime(retimeEnables, reduceEnables, ctx, cchain) { super.transform(lhs, rhs) }

    case _ => super.transform(lhs, rhs)
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
