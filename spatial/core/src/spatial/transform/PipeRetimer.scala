package spatial.transform

import scala.collection.mutable
import argon.transform.ForwardTransformer
import spatial._
import spatial.analysis.ModelingTraversal
import spatial.models._

trait PipeRetimer extends ForwardTransformer with ModelingTraversal { retimer =>
  val IR: SpatialExp
  import IR._

  override val name = "Pipeline Retimer"
  override def shouldRun = !SpatialConfig.enablePIR

  def requiresRetiming(x: Exp[_]) = latencyModel.requiresRegisters(x)
  def retimingDelay(x: Exp[_]): Int = {
    if (latencyModel.requiresRegisters(x)) latencyOf(x).toInt else 0
  }

  // track register info for each retimed reader
  // size represents the total buffer size between reader and input symbol
  // if buffers are split, the size of the register for this reader may actually be smaller
  class ReaderInfo(val size: Int) {
    // register this reader symbol reads from
    var reg: Exp[ShiftReg[_]] = _
    // register read IR node (not strictly necessary to have only one but it avoids bloating the IR with redundant reads)
    var read: Exp[_] = _
  }


  // track reader dependencies associated with an input
  class InputInfo {
    // map a reader symbol to the buffer it will read from
    val readers = mutable.HashMap[Exp[_], ReaderInfo]()
    // group readers by the size of the register they read from
    object RegSizeOrdering extends Ordering[Int] { def compare(a: Int, b: Int) = b compare a }

    // retime symbol readers, sharing allocated buffers when possible
    def retimeReaders[U](input: Exp[U]) {
      def valueDelay[T](size: Int, data: Exp[T])(implicit ctx: SrcCtx): Exp[T] = data.tp match {
        case Bits(bits) =>
          value_delay_alloc[T](size, data)(data.tp, bits, ctx)
        case _ => throw new Exception("Unexpected register type")

      }

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
        // val reg = regAlloc(input, size)
        // regWrite(reg, f(regReads.last))
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


  private def retimeBlock[T:Type](block: Block[T])(implicit ctx: SrcCtx): Exp[T] = inlineBlock(block, {stms =>
    dbg(c"Retiming block $block")

    // perform recursive search of inputs to determine cumulative symbol latency
    val symLatency = pipeLatencies(block)._1
    def delayOf(x: Exp[_]): Int = symLatency.getOrElse(x, 0L).toInt

    symLatency.foreach{case (s,l) => dbgs(c"  ${str(s)} [$l]")}

    dbgs("Calculating delays for each node: ")
    // enumerate symbol reader dependencies and calculate required buffer sizes
    val inputRetiming = mutable.HashMap[Exp[_], InputInfo]()
    stms.foreach{ case TP(reader, d) =>
      dbgs(c"${str(reader)}: ${delayOf(reader)}")
      val criticalPath = delayOf(reader) - latencyOf(reader)

      // Ignore non-bit based types and constants
      val inputs = exps(d).filterNot(isGlobal(_)).filter{e => Bits.unapply(e.tp).isDefined }

      dbgs("  " + inputs.map{in => c"$in: ${delayOf(in)}"}.mkString(", ") + s" (max: $criticalPath)")

      // calculate buffer register size for each input symbol
      val sizes = inputs.map{in => retimingDelay(in) + criticalPath - delayOf(in) }

      // discard symbols for which no register insertion is needed
      inputs.zip(sizes).filter{case (_, size) => size != 0 }.foreach{ case (input, size) =>
        dbgs(c"  ${str(input)} [size: $size]}")
        inputRetiming.getOrElseUpdate(input, new InputInfo()).readers.getOrElseUpdate(reader, new ReaderInfo(size.toInt))
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

    val result = typ[T] match {
      case VoidType => void
      case _ => f(block.result)
    }
    result.asInstanceOf[Exp[T]]
  })



  var retimeBlocks: List[Boolean] = Nil
  var ctx: Option[SrcCtx] = None

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

  override def apply[T:Type](b: Block[T]): Exp[T] = {
    val doWrap = retimeBlocks.headOption.getOrElse(false)
    if (retimeBlocks.nonEmpty) retimeBlocks = retimeBlocks.drop(1)
    dbgs(c"Transforming Block $b [$retimeBlocks]")
    if (doWrap) {
      val result = retimeBlock(b)(mtyp(b.tp),ctx.get)
      result
    }
    else super.apply(b)
  }

  private def transformCtrl[T:Type](lhs: Sym[T], rhs: Op[T])(implicit ctx: SrcCtx): Exp[T] = {
    if (isInnerControl(lhs)) {
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
