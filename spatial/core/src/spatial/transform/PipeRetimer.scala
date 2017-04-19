package spatial.transform

import scala.collection.mutable

import argon.transform.ForwardTransformer
import spatial._
import spatial.models._

trait PipeRetimer extends ForwardTransformer { retimer =>
  val IR: SpatialExp
  import IR._

  override val name = "Pipeline Retimer"
  lazy val latencyOf = new LatencyModel{val IR: retimer.IR.type = retimer.IR }

  protected override def preprocess[S: Type](block: Block[S]) = {
    // latencyOf.updateModel(target.latencyModel) // TODO: Update latency model with target-specific values
    super.preprocess(block)
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
    val readers = mutable.HashMap[Sym[_], ReaderInfo]()
    // group readers by the size of the register they read from
    object RegSizeOrdering extends Ordering[Int] { def compare(a: Int, b: Int) = b compare a }
    val readerSizes = mutable.SortedMap[Int, mutable.Set[Sym[_]]]()(RegSizeOrdering)

    // retime symbol readers, sharing allocated buffers when possible
    def retimeReaders[U](input: Sym[U]) {
      def regAlloc[T](s: Exp[T], size: Int)(implicit ctx: SrcCtx): Exp[ShiftReg[T]] = s.tp match {
        case Bits(bits) =>
          val init = unwrap(bits.zero)(s.tp)
          shift_reg_alloc[T](size, init)(s.tp, bits, ctx)
        case _ => throw new Exception("Unexpected register type")
      }

      def regRead[T](reg: Exp[ShiftReg[T]])(implicit ctx: SrcCtx): Exp[T] = reg.tp.typeArguments.head match {
        case tp @ Bits(bits) =>
          shift_reg_read(reg)(mtyp(tp), mbits(bits), ctx)
        case _ => throw new Exception("Unexpected register type")
      }

      def regWrite[T](reg: Exp[ShiftReg[_]], s: Exp[T])(implicit ctx: SrcCtx): Exp[Void] = s.tp match {
        case Bits(bits) =>
          shift_reg_write(reg.asInstanceOf[Exp[ShiftReg[T]]], s, bool(true))(s.tp, bits, ctx)
        case _ => throw new Exception("Unexpected register type")
      }

      // group and sort all the register sizes dependent symbols read from
      val sizes = readers.map{ case (_, info) => info.size }.toList.sorted
      // calculate register allocation sizes after coalescing
      val sizesCoalesced = (0 :: sizes).sliding(2).map{ case List(a, b) => b - a}.toList
      // map a reader's total register size to the size of the immediate register it will read from after coalescing
      val regSizeMap = sizes.zip(sizesCoalesced).toMap
      readers.foreach{ case (reader, info) =>
        readerSizes.getOrElseUpdate(regSizeMap(info.size), mutable.Set[Sym[_]]()) += reader
      }

      dbg(tab + c"Allocating registers for input $input")
      val regReads = mutable.ListBuffer[Exp[_]](input)
      // add sequential reads/writes between split registers after coalescing
      readerSizes.foreach{ case (size, readersSet) =>
        implicit val ctx: SrcCtx = input.ctx
        val reg = regAlloc(input, size)
        regWrite(reg, f(regReads.last))
        val read = regRead(reg)
        readersSet.foreach{ reader =>
          dbg(tab + c"  Register: $reg, size: $size, reader: $reader")
          readers(reader).reg = reg
          readers(reader).read = read
        }
        regReads += read
      }
    }
  }


  private def retimeBlock[T:Type](block: Block[T])(implicit ctx: SrcCtx): Exp[T] = inlineBlock(block, {stms =>
    val tab = "  "
    dbg(c"Retiming block $block")
    // cache symbol latencies to avoid recalculating if they've already been encountered during the traversal
    val latencyCache = mutable.HashMap[Sym[_], Int]()

    // perform recursive search of inputs to determine cumulative symbol latency
    def symLatency(sym: Sym[_]): Int = if (block.inputs.contains(sym)) 0 else {
      val inputs = syms(defOf(sym).inputs)
      val inputLatency = if (inputs.isEmpty) 0 else {
        inputs.map{ s => latencyCache.getOrElseUpdate(s, symLatency(s))}.max
      }
      inputLatency + latencyOf(sym).toInt
    }

    // enumerate symbol reader dependencies and calculate required buffer sizes
    val inputRetiming = mutable.HashMap[Sym[_], InputInfo]()
    stms.foreach{ case TP(reader, d) =>
      // inputs that are written to need to be filtered
      val inputs = syms(d.inputs).toSet.diff(effectsOf(reader).writes)
      val inputLatencies = inputs.map{ sym => symLatency(sym) }
      // calculate buffer register size for each input symbol
      val sizes = inputLatencies.map{ latency => inputLatencies.max - latency }
      // discard symbols for which no register insertion is needed
      val inputsSizes = inputs.zip(sizes).filter{ case (_, size) => size != 0 }
      inputsSizes.foreach{ case (input, size) =>
        inputRetiming.getOrElseUpdate(input, new InputInfo()).readers.getOrElseUpdate(reader, new ReaderInfo(size))
      }
    }

    // record which inputs have been buffered so retiming occurs only once
    val retimedInputs = mutable.Set[Sym[_]]()
    // traverse the IR, inserting registers allocated above
    stms.foreach{ case stm @ TP(reader, d) =>
      // save substitution rules for restoration after transformation
      val subRules = mutable.Map[Sym[_], Exp[_]]()

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
            dbg(tab + c"Buffering input $input to reader $reader")
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

  override def transform[T:Type](lhs: Sym[T], rhs: Op[T])(implicit ctx: SrcCtx): Exp[T] = {
    if (isInnerControl(lhs)) {
      val retimeEnables = rhs.blocks.map{_ => true }.toList
      withRetime(retimeEnables, ctx) { super.transform(lhs, rhs) }
    }
    else
      super.transform(lhs, rhs)
  }
}
