package spatial.models

import argon.core._
import argon.nodes._
import forge._
import spatial.aliases._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._
import argon.util._
import spatial.SpatialConfig

import scala.io.Source

abstract class AreaModel {
  val FILE_NAME: String
  @stateful def SRAMArea(width: Int, depth: Int): Area
  def RegArea(n: Int, bits: Int): Area = model("Reg")("b"->bits, "d"->1) * n
  def MuxArea(n: Int, bits: Int): Area = model("Mux")("n"->n, "b"->bits)

  final def FIELDS: Array[String] = SpatialConfig.target.FIELDS
  final def DSP_CUTOFF: Int = SpatialConfig.target.DSP_CUTOFF
  final implicit def AREA_CONFIG: AreaConfig[Double] = SpatialConfig.target.AREA_CONFIG
  final implicit def MODEL_CONFIG: AreaConfig[NodeModel] = SpatialConfig.target.MODEL_CONFIG

  lazy val NoArea: Area = AreaMap.zero[Double]

  var models: Map[String,Model] = Map.empty
  def model(name: String)(args: (String,Double)*): Area = {
    models.get(name).map(_.eval(args:_*)).getOrElse{
      val params = if (args.isEmpty) "" else " [optional parameters: " + args.map(_._1).mkString(", ") + "]"
      miss(s"$name (csv)" + params)
      NoArea
    }
  }

  private var missing: Set[String] = Set[String]()
  var recordMissing: Boolean = true
  def silence(): Unit = {
    recordMissing = false
  }
  @stateful def reportMissing(): Unit = {
    if (missing.nonEmpty) {
      warn(s"The target device ${SpatialConfig.target.name} was missing one or more area models.")
      missing.foreach{str => warn(s"  $str") }
      warn(s"Models marked (csv) can be added to $FILE_NAME.")
      warn("")
      state.logWarning()
    }
  }
  @inline def miss(str: String): Unit = if (recordMissing) { missing += str }

  def reset(): Unit = {
    missing = Set.empty
  }

  def loadModels(): Map[String,Model] = {
    val from = Source.fromResource("models/" + FILE_NAME)//Source.fromFile(SPATIAL_HOME + "/spatial/core/resources/models/" + FILE_NAME)
    val lines = from.getLines()
    val headings = lines.next().split(",").map(_.trim)
    val nParams  = headings.lastIndexWhere(_.startsWith("Param")) + 1
    val indices  = headings.zipWithIndex.filter{case (head,i) => FIELDS.contains(head) }.map(_._2)
    lines.map{line =>
      val parts = line.split(",").map(_.trim)
      val name  = parts.head
      val params = parts.slice(1,nParams).filterNot(_ == "")
      val entries = indices.map{i => LinearModel.fromString(parts(i)) }
      name -> new AreaMap[NodeModel](name, params, FIELDS.zip(entries).toMap)
    }.toMap
  }

  def init(): Unit = {
    models = loadModels()
  }

  @stateful protected def areaOfMemory(nbits: Int, dims: Seq[Int], instance: Memory): Area = {
    dbg(s"$instance")
    val totalElements = dims.product
    val bufferDepth = instance.depth

    val controlResourcesPerBank: Area = if (bufferDepth == 1) NoArea else MuxArea(bufferDepth, nbits)

    // TODO: This seems suspicious - check later
    instance match {
      case DiagonalMemory(strides, banks, _, isAccum) =>
        dbg(s"Word width:       $nbits")
        dbg(s"# of banks:       $banks")
        dbg(s"# of buffers:     $bufferDepth")

        val depth = Math.ceil(totalElements.toDouble/banks).toInt
        val memResourcesPerBank = SRAMArea(width = nbits, depth)
        val resourcesPerBuffer = (memResourcesPerBank + controlResourcesPerBank) * banks

        dbg(s"Resources / Bank: $memResourcesPerBank")
        dbg(s"Buffer resources: $resourcesPerBuffer")

        resourcesPerBuffer * bufferDepth

      case BankedMemory(banking,_,isAccum) =>
        val banks  = banking.map(_.banks)
        val nBanks = banks.product

        dbg(s"Word width:       $nbits")
        dbg(s"# of banks:       $nBanks")
        dbg(s"# of buffers:     $bufferDepth")

        val bankDepth = dims.zip(banks).map{case (dim,bank) => Math.ceil(dim.toDouble/bank).toInt }.product
        val memResourcesPerBank = SRAMArea(width = nbits, bankDepth)
        val resourcesPerBuffer = (memResourcesPerBank + controlResourcesPerBank) * nBanks

        dbg(s"Resources / Bank: $memResourcesPerBank")
        dbg(s"Buffer resources: $resourcesPerBuffer")

        resourcesPerBuffer * bufferDepth
    }
  }

  @stateful protected def areaOfSRAM(nbits: Int, dims: Seq[Int], instances: Seq[Memory]): Area = {
    instances.map{instance => areaOfMemory(nbits, dims, instance) }.fold(NoArea){_+_}
  }

  @stateful def nDups(e: Exp[_]): Int = duplicatesOf(e).length
  @stateful def nStages(e: Exp[_]): Int = childrenOf((e,-1)).length

  @stateful def apply(e: Exp[_], inHwScope: Boolean, inReduce: Boolean): Area = getDef(e) match {
    case Some(d) => areaOf(e, d, inHwScope, inReduce)
    case None => NoArea
  }
  @stateful final def areaOf(e: Exp[_], d: Def, inHwScope: Boolean, inReduce: Boolean): Area = {
    if (!inHwScope) NoArea else if (inReduce) areaInReduce(e, d) else areaOfNode(e, d)
  }

  @stateful def areaInReduce(e: Exp[_], d: Def): Area = areaOfNode(e, d)

  @stateful private def areaOfControl(ctrl: Exp[_]): Area = {
    if (isInnerControl(ctrl)) NoArea // TODO: Is this correct?
    else if (isSeqPipe(ctrl)) model("Sequential")("n" -> nStages(ctrl))
    else if (isMetaPipe(ctrl)) model("MetaPipe")("n" -> nStages(ctrl))
    else if (isStreamPipe(ctrl)) model("Stream")("n" -> nStages(ctrl))
    else NoArea
  }

  @stateful def areaOfNode(lhs: Exp[_], rhs: Def): Area = rhs match {
    /** Non-synthesizable nodes **/
    case _:PrintIf          => NoArea
    case _:PrintlnIf        => NoArea
    case _:AssertIf         => NoArea
    case _:ToString[_]      => NoArea
    case _:StringConcat     => NoArea
    case _:VarRegNew[_]     => NoArea
    case _:VarRegRead[_]    => NoArea
    case _:VarRegWrite[_]   => NoArea
    //case FixRandom(_)       => NoArea  // TODO: This is synthesizable now?
    //case FltRandom(_)       => NoArea  // TODO: This is synthesizable now?

    /** Zero area cost **/
    case FieldApply(_,_)    => NoArea
    case VectorApply(_,_)   => NoArea
    case VectorSlice(_,_,_) => NoArea
    case VectorConcat(_)    => NoArea
    case DataAsBits(_)      => NoArea
    case BitsAsData(_,_)    => NoArea
    case FixLsh(_,_)        => NoArea // Non-constant shift should get transformed into a loop in Spatial
    case FixRsh(_,_)        => NoArea // Non-constant shift should get transformed into a loop in Spatial
    case FixURsh(_,_)       => NoArea // Non-constant shift should get transformed into a loop in Spatial
    case _:SwitchCase[_]    => NoArea // Doesn't correspond to anything in hardware
    case _:DRAMNew[_,_]     => NoArea // No hardware cost
    case GetDRAMAddress(_)  => NoArea // No hardware cost
    case FixConvert(_)      => NoArea

    /** Memories **/
    case CounterNew(start,end,step,p) =>
      val par = boundOf(p).toInt
      val bits: Double = end match {case Exact(c) => log2(c.toDouble) + (if (isPow2(c.toDouble)) 1 else 0); case _ => 32.0 }
      model("Counter")("b" -> bits, "p" -> par)

    case CounterChainNew(ctrs) => model("CounterChain")("n" -> ctrs.length)

    // LUTs
    case lut @ LUTNew(dims,elems) => model("LUT")("s" -> dims.product, "b" -> lut.bT.length) // TODO
    case _:LUTLoad[_] => NoArea // TODO

    // Streams
    // TODO: Need models for streams
    case StreamInNew(bus) if bus.isInstanceOf[DRAMBus[_]]  => NoArea
    case StreamOutNew(bus) if bus.isInstanceOf[DRAMBus[_]] => NoArea
    //case _:StreamInNew[_]       => NoArea
    //case _:StreamOutNew[_]      => NoArea
    case _:StreamRead[_]        => NoArea
    case _:ParStreamRead[_]     => NoArea
    case _:StreamWrite[_]       => NoArea
    case _:ParStreamWrite[_]    => NoArea
    case _:BufferedOutWrite[_]  => NoArea

    // TODO: Account for compressing data when this is supported
    // TODO: Account for parallelization
    // FIFOs
    case fifo:FIFONew[_] => duplicatesOf(lhs).map{
      case BankedMemory(_,depth,isAccum) => model("FIFO")("d" -> depth, "b" -> fifo.bT.length)
      case _ => NoArea
    }.fold(NoArea){_+_}
    case _:FIFOPeek[_]        => NoArea
    case _:FIFOEnq[_]         => NoArea
    case _:ParFIFOEnq[_]      => NoArea
    case _:FIFODeq[_]         => NoArea
    case _:ParFIFODeq[_]      => NoArea
    case _:FIFONumel[_]       => NoArea
    case _:FIFOAlmostEmpty[_] => NoArea
    case _:FIFOAlmostFull[_]  => NoArea
    case _:FIFOEmpty[_]       => NoArea
    case _:FIFOFull[_]        => NoArea

    // FILOs
    case filo:FILONew[_] => duplicatesOf(lhs).map{
      case BankedMemory(_,depth,isAccum) => model("FIFO")("d" -> depth, "b" -> filo.bT.length)
      case _ => NoArea
    }.fold(NoArea){_+_}
    case _:FILOPeek[_]        => NoArea
    case _:FILOPush[_]        => NoArea
    case _:ParFILOPush[_]     => NoArea
    case _:FILOPop[_]         => NoArea
    case _:ParFILOPop[_]      => NoArea
    case _:FILONumel[_]       => NoArea
    case _:FILOAlmostEmpty[_] => NoArea
    case _:FILOAlmostFull[_]  => NoArea
    case _:FILOEmpty[_]       => NoArea
    case _:FILOFull[_]        => NoArea

    // SRAMs
    case op:SRAMNew[_,_]   => areaOfSRAM(op.bT.length,dimsOf(lhs),duplicatesOf(lhs))
    case _:SRAMLoad[_]     => NoArea
    case _:ParSRAMLoad[_]  => NoArea
    case _:SRAMStore[_]    => NoArea
    case _:ParSRAMStore[_] => NoArea

    // LineBuffer
    // TODO: Confirm this model for SRAM, or change
    case op:LineBufferNew[_]    => areaOfSRAM(op.bT.length,dimsOf(lhs),duplicatesOf(lhs))
    case _:LineBufferEnq[_]     => NoArea
    case _:ParLineBufferEnq[_]  => NoArea
    case _:LineBufferLoad[_]    => NoArea
    case _:ParLineBufferLoad[_] => NoArea

    // Regs
    case reg:RegNew[_] => duplicatesOf(lhs).map{
      case BankedMemory(_,depth,isAccum) => model("Reg")("d" -> depth, "b" -> reg.bT.length)
      case _ => NoArea
    }.fold(NoArea){_+_}
    case _:RegRead[_]  => NoArea
    case _:RegWrite[_] => NoArea
    case _:RegReset[_] => NoArea

    // Register File
    case rf:RegFileNew[_,_] =>
      val rank = dimsOf(lhs).length
      val size = dimsOf(lhs).product
      duplicatesOf(lhs).map{
        case BankedMemory(_,depth,isAccum) if rank == 1 => model("RegFile1D")("b" -> rf.bT.length, "d" -> depth, "c" -> size)
        case BankedMemory(_,depth,isAccum) if rank == 2 =>
          val r = dimsOf(lhs).head
          var c = dimsOf(lhs).apply(1)
          model("RegFile2D")("b" -> rf.bT.length, "d" -> depth, "r" -> r, "c" -> c)
        case _ =>
          miss(rank + "D RegFile (rule)")
          NoArea
      }.fold(NoArea){_+_}

    case _:RegFileLoad[_]       => NoArea
    case _:ParRegFileLoad[_]    => NoArea
    case _:RegFileStore[_]      => NoArea
    case _:ParRegFileStore[_]   => NoArea
    case _:RegFileShiftIn[_]    => NoArea
    case _:ParRegFileShiftIn[_] => NoArea

    /** Primitives **/
    // Bit
    case Not(_)     => model("BitNot")()
    case And(_,_)   => model("BitAnd")()
    case Or(_,_)    => model("BitOr")()
    case XOr(_,_)   => model("BitXOr")()
    case XNor(_,_)  => model("BitEql")()

    // Fixed point
    case FixNeg(_)   => model("FixNeg")("b" -> nbits(lhs))
    case FixInv(_)   => model("FixInv")("b" -> nbits(lhs))
    case FixAdd(_,_) => model("FixAdd")("b" -> nbits(lhs))
    case FixSub(_,_) => model("FixSub")("b" -> nbits(lhs))
    case FixMul(_,_) => nbits(lhs) match {
      case n if n < DSP_CUTOFF => model("FixMulSmall")("b" -> n)
      case n                   => model("FixMulBig")("b" -> n)
    }
    case FixDiv(_,_) => model("FixDiv")("b" -> nbits(lhs))
    case FixMod(_,_) => model("FixMod")("b" -> nbits(lhs))
    case FixLt(_,_)  => model("FixLt")("b" -> nbits(lhs))
    case FixLeq(_,_) => model("FixLt")("b" -> nbits(lhs))
    case FixNeq(_,_) => model("FixNeq")("b" -> nbits(lhs))
    case FixEql(_,_) => model("FixEql")("b" -> nbits(lhs))
    case FixAnd(_,_) => model("FixAnd")("b" -> nbits(lhs))
    case FixOr(_,_)  => model("FixOr")("b" -> nbits(lhs))
    case FixXor(_,_) => model("FixXor")("b" -> nbits(lhs))
    case FixAbs(_)   => model("FixAbs")("b" -> nbits(lhs))

    // Saturating and/or unbiased math
    case SatAdd(_,_)    => model("SatAdd")("b" -> nbits(lhs))
    case SatSub(_,_)    => model("SatSub")("b" -> nbits(lhs))
    case SatMul(_,_)    => model("SatMul")("b" -> nbits(lhs))
    case SatDiv(_,_)    => model("SatDiv")("b" -> nbits(lhs))
    case UnbMul(_,_)    => model("UnbMul")("b" -> nbits(lhs))
    case UnbDiv(_,_)    => model("UnbDiv")("b" -> nbits(lhs))
    case UnbSatMul(_,_) => model("UnbSatMul")("b" -> nbits(lhs))
    case UnbSatDiv(_,_) => model("UnbSatDiv")("b" -> nbits(lhs))

    // Floating point
    case FltNeg(_)   => lhs.tp match {
      case FloatType()    => model("FloatNeg")()
      case FltPtType(s,e) => model("FltNeg")("s" -> s, "e" -> e)
    }
    case FltAbs(_)   => lhs.tp match {
      case FloatType()    => model("FloatAbs")()
      case FltPtType(s,e) => model("FltAbs")("s" -> s, "e" -> e)
    }
    case FltAdd(_,_) => lhs.tp match {
      case FloatType()    => model("FloatAdd")()
      case FltPtType(s,e) => model("FltAdd")("s" -> s, "e" -> e)
    }
    case FltSub(_,_) => lhs.tp match {
      case FloatType()    => model("FloatSub")()
      case FltPtType(s,e) => model("FltSub")("s" -> s, "e" -> e)
    }
    case FltMul(_,_) => lhs.tp match {
      case FloatType()    => model("FloatMul")()
      case FltPtType(s,e) => model("FltMul")("s" -> s, "e" -> e)
    }
    case FltDiv(_,_) => lhs.tp match {
      case FloatType()    => model("FloatDiv")()
      case FltPtType(s,e) => model("FltDiv")("s" -> s, "e" -> e)
    }
    case FltLt(a,_)  => lhs.tp match {
      case FloatType()    => model("FloatLt")()
      case FltPtType(s,e) => model("FltLt")("s" -> s, "e" -> e)
    }
    case FltLeq(a,_) => lhs.tp match {
      case FloatType()    => model("FloatLeq")()
      case FltPtType(s,e) => model("FltLeq")("s" -> s, "e" -> e)
    }
    case FltNeq(a,_) => lhs.tp match {
      case FloatType()    => model("FloatNeq")()
      case FltPtType(s,e) => model("FltNeq")("s" -> s, "e" -> e)
    }
    case FltEql(a,_) => lhs.tp match {
      case FloatType()    => model("FloatEql")()
      case FltPtType(s,e) => model("FltEql")("s" -> s, "e" -> e)
    }
    case FltLog(_)   => lhs.tp match {
      case FloatType()    => model("FloatLog")()
      case FltPtType(s,e) => model("FltLog")("s" -> s, "e" -> e)
    }
    case FltExp(_)   => lhs.tp match {
      case FloatType()    => model("FloatExp")()
      case FltPtType(s,e) => model("FltExp")("s" -> s, "e" -> e)
    }
    case FltSqrt(_)  => lhs.tp match {
      case FloatType()    => model("FloatSqrt")()
      case FltPtType(s,e) => model("FltSqrt")("s" -> s, "e" -> e)
    }

    // Conversions
    //case FltConvert(_) => NoArea // TODO
    case FltPtToFixPt(x) => x.tp match {
      case FloatType() => lhs.tp match {
        case FixPtType(_,i,f) => model("FloatToFix")("b"->i,"f"->f)
      }
      case FltPtType(s,e) => lhs.tp match {
        case FixPtType(_,i,f) => model("FltToFix")("s"->s,"e"->e,"b"->i,"f"->f)
      }
    }
    case FixPtToFltPt(x) => lhs.tp match {
      case FloatType() => x.tp match {
        case FixPtType(_,i,f) => model("FixToFloat")("b"->i,"f"->f)
      }
      case FltPtType(s,e) => x.tp match {
        case FixPtType(_,i,f) => model("FixToFlt")("s"->s,"e"->e,"b"->i,"f"->f)
      }
    }

    // Other
    case Mux(_,_,_) => model("Mux")("b" -> nbits(lhs))
    case _:Min[_] | _:Max[_] => lhs.tp match {
      case FixPtType(_,_,_) => model("FixLt")("b" -> nbits(lhs)) + model("Mux")("b" -> nbits(lhs))
      case FloatType()      => model("FloatLt")() + model("Mux")("b" -> nbits(lhs))
      case DoubleType()     => model("DoubleLt")() + model("Mux")("b" -> nbits(lhs))
      case tp =>
        miss(u"Mux on $tp (rule)")
        NoArea
    }
    case DelayLine(depth, _)   => areaOfDelayLine(depth,nbits(lhs),1)


    /** Control Structures **/
    case _:Hwblock             => areaOfControl(lhs)
    case _:UnitPipe            => areaOfControl(lhs)
    case _:ParallelPipe        => model("Parallel")("n" -> nStages(lhs))
    case _:OpForeach           => areaOfControl(lhs)
    case _:OpReduce[_]         => areaOfControl(lhs)
    case _:OpMemReduce[_,_]    => areaOfControl(lhs)
    case _:UnrolledForeach     => areaOfControl(lhs)
    case _:UnrolledReduce[_,_] => areaOfControl(lhs)
    case s:Switch[_] => lhs.tp match {
      case Bits(bt) => model("SwitchMux")("n" -> s.cases.length, "b" -> bt.length)
      case _        => model("Switch")("n" -> s.cases.length)
    }

    case _ =>

      NoArea
  }

  /**
    * Returns the area resources for a delay line with the given width (in bits) and length (in cycles)
    * Models delays as registers for short delays, BRAM for long ones
    **/
  @stateful def areaOfDelayLine(length: Int, width: Int, par: Int): Area = {
    val nregs = width*length
    // TODO: Should fix this cutoff point to something more real
    val area = if (nregs < 256) RegArea(length, width)*par
    else areaOfSRAM(width*par, List(length), List(BankedMemory(Seq(NoBanking(1)),1,false)))

    dbg(s"Delay line (w x l): $width x $length (${width*length}) [par = $par]")
    dbg(s"  $area")
    area
  }

  @stateful def summarize(area: Area): Area
}
