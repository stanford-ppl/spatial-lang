package spatial.models

import argon.core._
import argon.nodes._
import forge._
import spatial.aliases._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._
import argon.util._

import scala.io.Source
import scala.util.Try


trait LatencyModel {
  val FILE_NAME: String
  var clockRate = 150.0f        // Frequency in MHz
  var baseCycles = 43000        // Number of cycles required for startup
  var addRetimeRegisters = true // Enable adding registers after specified comb. logic
  var modelVerbosity = 1

  def silence(): Unit = {
    modelVerbosity = -1
    recordMissing = false
  }

  var target: FPGATarget = _
  final def LFIELDS: Array[String] = target.LFIELDS
  final def DSP_CUTOFF: Int = target.DSP_CUTOFF
  final implicit def LATENCY_CONFIG: LatencyConfig[Double] = target.LATENCY_CONFIG
  final implicit def MODEL_CONFIG: LatencyConfig[NodeModel] = target.LMODEL_CONFIG

  final def NoLatency: Latency = LatencyMap.zero[Double]

  private var missing: Set[String] = Set[String]()
  var recordMissing: Boolean = true

  private var needsInit: Boolean = true
  @stateful def init(): Unit = {
    if (needsInit) {
      target = spatialConfig.target
      models = loadModels()
      needsInit = false
    }
  }

  @stateful def reportMissing(): Unit = {
    if (missing.nonEmpty) {
      warn(s"The target device ${spatialConfig.target.name} was missing one or more latency models.")
      missing.foreach{str => warn(s"  $str") }
      //warn(s"Models marked (csv) can be added to $FILE_NAME.")
      warn("")
      state.logWarning()
    }
  }
  @inline private def miss(str: String): Unit = if (recordMissing) { missing += str }

  def reset(): Unit = { missing = Set.empty }

  var models: Map[String,LModel] = Map.empty
  def model(name: String)(args: (String,Double)*): Latency = {
    models.get(name).map(_.eval(args:_*)).getOrElse{
      val params = if (args.isEmpty) "" else " [optional parameters: " + args.mkString(", ") + "]"
      miss(s"$name (csv)" + params)
      NoLatency
    }
  }

  @stateful def loadModels(): Map[String,(LModel)] = {
    val resource = Try(Source.fromResource("models/" + FILE_NAME).getLines())
    val direct = Try{
      val SPATIAL_HOME = sys.env("SPATIAL_HOME")
      Source.fromFile(SPATIAL_HOME + "/spatial/core/resources/models/" + FILE_NAME).getLines()
    }
    val file: Option[Iterator[String]] = {
      if (resource.isSuccess) Some(resource.get)
      else if (direct.isSuccess) Some(direct.get)
      else None
    }

    file.map{lines =>
      val headings = lines.next().split(",").map(_.trim)
      val nParams  = headings.lastIndexWhere(_.startsWith("Param")) + 1
      val indices  = headings.zipWithIndex.filter{case (head,i) => LFIELDS.contains(head) }.map(_._2)
      val fields   = indices.map{i => headings(i) }
      val missing = LFIELDS diff fields
      if (missing.nonEmpty) {
        warn(s"Area model file $FILE_NAME for target ${target.name} was missing expected fields: ")
        warn(missing.mkString(", "))
      }
      val models = lines.flatMap{line =>
        val parts = line.split(",").map(_.trim)
        if (parts.nonEmpty) {
          val name = parts.head
          val params = parts.slice(1, nParams).filterNot(_ == "")
          val entries = indices.map { i => if (i < parts.length) LinearModel.fromString(parts(i)) else Right(0.0) }
          Some(name -> new LatencyMap[NodeModel](name, params, fields.zip(entries).toMap))
        }
        else None
      }.toMap

      /*models.foreach{case (k,v) =>
        if (k != "") {
          println(s"$k: $v")
        }
      }*/

      models
    }.getOrElse{
      warn(s"Area model file $FILE_NAME for target ${target.name} was not found.")
      Map.empty
    }
  }

  @stateful def apply(s: Exp[_], inReduce: Boolean = false): Double = latencyOf(s, inReduce)

  @stateful def latencyOf(s: Exp[_], inReduce: Boolean): Double = {
    val prevVerbosity = config.verbosity
    config.verbosity = modelVerbosity
    val latency = s match {
      case Exact(_) => 0
      case Final(_) => 0
      case Def(d) if inReduce  => latencyOfNodeInReduce(s, d)
      case Def(d) if !inReduce => latencyOfNode(s, d)
      case _ => 0
    }
    config.verbosity = prevVerbosity
    latency
  }

  @stateful protected def latencyOfNodeInReduce(s: Exp[_], d: Def): Double = d match {
    case FixAdd(_,_)     => model("FixAdd")("b" -> nbits(s))("LatencyInReduce")
    case Mux(_,_,_)      => model("Mux")()("LatencyInReduce")
    case FltAdd(_,_)     => model("FltAdd")()("LatencyInReduce")
    //case RegWrite(_,_,_) => 0
    case _ => latencyOfNode(s,d) //else 0L
  }

  def nbits(e: Exp[_]): Int = e.tp match { case Bits(bits) => bits.length }
  def sign(e: Exp[_]): Boolean = e.tp match { case FixPtType(s,_,_) => s; case _ => true }

  @stateful def requiresRegisters(s: Exp[_], inReduce: Boolean): Boolean = addRetimeRegisters && {
    if (inReduce) requiresRegistersInReduce(s)
    else requiresRegisters(s)
  }

  @stateful protected def requiresRegistersInReduce(s: Exp[_]): Boolean = getDef(s).exists{
    case _:SRAMLoad[_]       => if (spatialConfig.enableSyncMem) model("SRAMLoadSyncMem")()("RequiresInReduce") > 0 else model("SRAMLoad")()("RequiresInReduce") > 0
    case _:BankedSRAMLoad[_] => if (spatialConfig.enableSyncMem) model("ParSRAMLoadSyncMem")()("RequiresInReduce") > 0 else model("ParSRAMLoad")()("RequiresInReduce") > 0
    case FixMul(_,_) => model("FixMul")("b" -> nbits(s))("RequiresInReduce") > 0
    case d => latencyOfNodeInReduce(s,d) > 0
  }

  @stateful protected def requiresRegisters(s: Exp[_]): Boolean = addRetimeRegisters && getDef(s).exists{
    case FieldApply(_,_)    => model("FieldApply")()("RequiresRegs") > 0
    case VectorApply(_,_)   => model("VectorApply")()("RequiresRegs") > 0
    case VectorSlice(_,_,_) => model("VectorSlice")()("RequiresRegs") > 0
    case VectorConcat(_)    => model("VectorConcat")()("RequiresRegs") > 0
    case DataAsBits(_)      => model("DataAsBits")()("RequiresRegs") > 0
    case BitsAsData(_,_)    => model("BitsAsData")()("RequiresRegs") > 0

    case _:VarRegNew[_]   => model("VarRegNew")()("RequiresRegs") > 0
    case _:VarRegRead[_]  => model("VarRegRead")()("RequiresRegs") > 0
    case _:VarRegWrite[_] => model("VarRegWrite")()("RequiresRegs") > 0

    case _:LUTLoad[_] => model("LUTLoad")()("RequiresRegs") > 0

    // Registers
    case _:RegRead[_]  => model("RegRead")()("RequiresRegs") > 0
    case _:RegWrite[_] => model("RegWrite")()("RequiresRegs") > 0
    case _:RegReset[_] => model("RegReset")()("RequiresRegs") > 0

    // Register File
    case _:RegFileStore[_]         => model("RegFileStore")()("RequiresRegs") > 0
    case _:BankedRegFileStore[_]   => model("ParRegFileStore")()("RequiresRegs") > 0
    case _:RegFileShiftIn[_]       => model("RegFileShiftIn")()("RequiresRegs") > 0
    case _:RegFileVectorShiftIn[_] => model("ParRegFileShiftIn")()("RequiresRegs") > 0

    // Streams
    case _:StreamRead[_]        => model("StreamRead")()("RequiresRegs") > 0
    case _:BankedStreamRead[_]  => model("ParStreamRead")()("RequiresRegs") > 0
    case _:StreamWrite[_]       => model("StreamWrite")()("RequiresRegs") > 0
    case _:BankedStreamWrite[_] => model("ParStreamWrite")()("RequiresRegs") > 0
    case _:BufferedOutWrite[_]  => model("BufferedOutWrite")()("RequiresRegs") > 0

    // FIFOs
    case _:FIFOEnq[_]         => model("FIFOEnq")()("RequiresRegs") > 0
    case _:BankedFIFOEnq[_]   => model("ParFIFOEnq")()("RequiresRegs") > 0
    case _:FIFONumel[_]       => model("FIFONumel")()("RequiresRegs") > 0
    case _:FIFOAlmostEmpty[_] => model("FIFOAlmostEmpty")()("RequiresRegs") > 0
    case _:FIFOAlmostFull[_]  => model("FIFOAlmostFull")()("RequiresRegs") > 0
    case _:FIFOEmpty[_]       => model("FIFOEmpty")()("RequiresRegs") > 0
    case _:FIFOFull[_]        => model("FIFOFull")()("RequiresRegs") > 0

    // SRAMs
    // TODO: Should be a function of number of banks?
    case _:SRAMStore[_]       => model("SRAMStore")()("RequiresRegs") > 0
    case _:BankedSRAMStore[_] => model("ParSRAMStore")()("RequiresRegs") > 0

    // LineBuffer
    case _:LineBufferEnq[_]       => model("LineBufferEnq")()("RequiresRegs") > 0
    case _:BankedLineBufferEnq[_] => model("ParLineBufferEnq")()("RequiresRegs") > 0

    // Shift Register
    case DelayLine(size, data) => model("DelayLine")()("RequiresRegs") > 0 // TODO: Should use different model once these are added?

    // DRAM
    case GetDRAMAddress(_) => model("GetDRAMAddress")()("RequiresRegs") > 0

    // Fixed point math
    // TODO: Have to get numbers for non-32 bit multiplies and divides

    // Saturating and/or unbiased math

    // Floating point math
    // TODO: Floating point for things besides single precision
    case FltAbs(_)  => model("FltAbs")()("RequiresRegs") > 0
    case FltNeg(_)  => model("FltNeg")()("RequiresRegs") > 0
    case FltAdd(_,_) if s.tp == FloatType => model("FltAddFloat")()("RequiresRegs") > 0
    case FltSub(_,_) if s.tp == FloatType => model("FltSubFloat")()("RequiresRegs") > 0
    case FltMul(_,_) if s.tp == FloatType => model("FltMulFloat")()("RequiresRegs") > 0
    case FltDiv(_,_) if s.tp == FloatType => model("FltDivFloat")()("RequiresRegs") > 0

    case FltLt(a,_)  if a.tp == FloatType => model("FltLtFloat")()("RequiresRegs") > 0
    case FltLeq(a,_) if a.tp == FloatType => model("FltLeqFloat")()("RequiresRegs") > 0

    case FltNeq(a,_) if a.tp == FloatType => model("FltNeqFloat")()("RequiresRegs") > 0
    case FltEql(a,_) if a.tp == FloatType => model("FltEqlFloat")()("RequiresRegs") > 0

    case FltLog(_) if s.tp == FloatType => model("FltLogFloat")()("RequiresRegs") > 0
    case FltExp(_) if s.tp == FloatType => model("FltExpFloat")()("RequiresRegs") > 0
    case FltSqrt(_) if s.tp == FloatType => model("FltSqrtFloat")()("RequiresRegs") > 0

    case FltConvert(_)  => model("FltConvert")()("RequiresRegs") > 0 // TODO

    case FltPtToFixPt(x) if x.tp == FloatType => model("FltPtToFixPtFloat")()("RequiresRegs") > 0
    case FixPtToFltPt(x) if s.tp == FloatType => model("FixPtToFltPtFloat")()("RequiresRegs") > 0

    case _:Hwblock             => model("Hwblock")()("RequiresRegs") > 0
    case _:ParallelPipe        => model("ParallelPipe")()("RequiresRegs") > 0
    case _:UnitPipe            => model("UnitPipe")()("RequiresRegs") > 0
    case _:OpForeach           => model("OpForeach")()("RequiresRegs") > 0
    case _:OpReduce[_]         => model("OpReduce")()("RequiresRegs") > 0
    case _:OpMemReduce[_,_]    => model("OpMemReduce")()("RequiresRegs") > 0
    case _:UnrolledForeach     => model("UnrolledForeach")()("RequiresRegs") > 0
    case _:UnrolledReduce      => model("UnrolledReduce")()("RequiresRegs") > 0
    case _:Switch[_]           => model("Switch")()("RequiresRegs") > 0
    case _:SwitchCase[_]       => model("SwitchCase")()("RequiresRegs") > 0

      // Host/Debugging/Unsynthesizable nodes
    case _:ExitIf  => model("ExitIf")()("RequiresRegs") > 0
    case _:BreakpointIf  => model("BreakpointIf")()("RequiresRegs") > 0
    case _:PrintIf   => model("PrintIf")()("RequiresRegs") > 0
    case _:PrintlnIf => model("PrintlnIf")()("RequiresRegs") > 0
    case _:AssertIf  => model("AssertIf")()("RequiresRegs") > 0
    case _:ToString[_] => model("ToString")()("RequiresRegs") > 0
    case _:StringConcat => model("StringConcat")()("RequiresRegs") > 0
    case FixRandom(_) => model("FixRandom")()("RequiresRegs") > 0  // TODO: This is synthesizable now?
    case FltRandom(_) => model("FltRandom")()("RequiresRegs") > 0  // TODO: This is synthesizable now?

    // Register File
    case _:RegFileLoad[_]       => model("RegFileLoad")()("RequiresRegs") > 0
    case _:BankedRegFileLoad[_] => model("ParRegFileLoad")()("RequiresRegs") > 0
    // Streams

    // FIFOs
    case _:FIFODeq[_]       => model("FIFODeq")()("RequiresRegs") > 0
    case _:BankedFIFODeq[_] => model("ParFIFODeq")()("RequiresRegs") > 0

    // SRAMs
    // TODO: Should be a function of number of banks?
    case _:SRAMLoad[_]       => if (spatialConfig.enableSyncMem) model("SRAMLoadSyncMem")()("RequiresRegs") > 0 else model("SRAMLoad")()("RequiresRegs") > 0
    case _:BankedSRAMLoad[_] => if (spatialConfig.enableSyncMem) model("ParSRAMLoadSyncMem")()("RequiresRegs") > 0 else model("ParSRAMLoad")()("RequiresRegs") > 0

    // LineBuffer
    case _:LineBufferLoad[_]       => model("LineBufferLoad")()("RequiresRegs") > 0
    case _:BankedLineBufferLoad[_] => model("ParLineBufferLoad")()("RequiresRegs") > 0

    case Not(_)     => model("Not")()("RequiresRegs") > 0
    case And(_,_)   => model("And")()("RequiresRegs") > 0
    case Or(_,_)    => model("Or")()("RequiresRegs") > 0
    case XOr(_,_)   => model("XOr")()("RequiresRegs") > 0
    case XNor(_,_)  => model("XNor")()("RequiresRegs") > 0
    case FixNeg(_)   => model("FixNeg")()("RequiresRegs") > 0
    case FixInv(_)   => model("FixInv")()("RequiresRegs") > 0
    case FixAdd(_,_) => model("FixAdd")("b" -> nbits(s))("RequiresRegs") > 0
    case FixSub(_,_) => model("FixSub")("b" -> nbits(s))("RequiresRegs") > 0
    case FixMul(_,_) => model("FixMul")("b" -> nbits(s))("RequiresRegs") > 0
    case FixDiv(_,_) => model("FixDiv")("b" -> nbits(s))("RequiresRegs") > 0
    case FixMod(_,_) => model("FixMod")("b" -> nbits(s))("RequiresRegs") > 0
    case FixLt(_,_)  => model("FixLt")()("RequiresRegs") > 0
    case FixLeq(_,_) => model("FixLeq")()("RequiresRegs") > 0
    case FixNeq(_,_) => model("FixNeq")()("RequiresRegs") > 0
    case FixEql(_,_) => model("FixEql")()("RequiresRegs") > 0
    case FixAnd(_,_) => model("FixAnd")()("RequiresRegs") > 0
    case FixOr(_,_)  => model("FixOr")()("RequiresRegs") > 0
    case FixXor(_,_) => model("FixXor")()("RequiresRegs") > 0
    case FixLsh(_,_) => model("FixLsh")()("RequiresRegs") > 0
    case FixRsh(_,_) => model("FixRsh")()("RequiresRegs") > 0
    case FixURsh(_,_) => model("FixURsh")()("RequiresRegs") > 0
    case FixAbs(_)    => model("FixAbs")()("RequiresRegs") > 0
    case FixConvert(_) => model("FixConvert")()("RequiresRegs") > 0

    case SatAdd(x,y) => model("SatAdd")()("RequiresRegs") > 0
    case SatSub(x,y) => model("SatSub")()("RequiresRegs") > 0
    case SatMul(x,y) => model("SatMul")()("RequiresRegs") > 0
    case SatDiv(x,y) => model("SatDiv")()("RequiresRegs") > 0
    case UnbMul(x,y) => model("UnbMul")()("RequiresRegs") > 0
    case UnbDiv(x,y) => model("UnbDiv")()("RequiresRegs") > 0
    case UnbSatMul(x,y) => model("UnbSatMul")()("RequiresRegs") > 0
    case UnbSatDiv(x,y) => model("UnbSatDiv")()("RequiresRegs") > 0

    case Mux(_,_,_) => model("Mux")()("RequiresRegs") > 0
    case Min(_,_)   => model("Min")()("RequiresRegs") > 0
    case Max(_,_)   => model("Max")()("RequiresRegs") > 0
    case _ => 
      miss(u"${s} (rule)")
      false
  }

  @stateful protected def latencyOfNode(s: Exp[_], d: Def): Double = d match {
    case d if isAllocation(d) => model("isAllocation")()("LatencyOf")
    case FieldApply(_,_)    => model("FieldApply")()("LatencyOf")
    case VectorApply(_,_)   => model("VectorApply")()("LatencyOf")
    case VectorSlice(_,_,_) => model("VectorSlice")()("LatencyOf")
    case VectorConcat(_)    => model("VectorConcat")()("LatencyOf")
    case DataAsBits(_)      => model("DataAsBits")()("LatencyOf")
    case BitsAsData(_,_)    => model("BitsAsData")()("LatencyOf")

    case _:VarRegNew[_]   => model("VarRegNew")()("LatencyOf")
    case _:VarRegRead[_]  => model("VarRegRead")()("LatencyOf")
    case _:VarRegWrite[_] => model("VarRegWrite")()("LatencyOf")

    case _:LUTLoad[_] => model("LUTLoad")()("LatencyOf")

    // Registers
    case _:RegRead[_]  => model("RegRead")()("LatencyOf")
    case _:RegWrite[_] => model("RegWrite")()("LatencyOf")
    case _:RegReset[_] => model("RegReset")()("LatencyOf")

    // Register File
    case _:RegFileLoad[_]          => model("RegFileLoad")()("LatencyOf")
    case _:BankedRegFileLoad[_]    => model("ParRegFileLoad")()("LatencyOf")
    case _:RegFileStore[_]         => model("RegFileStore")()("LatencyOf")
    case _:BankedRegFileStore[_]   => model("ParRegFileStore")()("LatencyOf")
    case _:RegFileShiftIn[_]       => model("RegFileShiftIn")()("LatencyOf")
    case _:RegFileVectorShiftIn[_] => model("ParRegFileShiftIn")()("LatencyOf")

    // Streams
    case _:StreamRead[_]        => model("StreamRead")()("LatencyOf")
    case _:BankedStreamRead[_]  => model("ParStreamRead")()("LatencyOf")
    case _:StreamWrite[_]       => model("StreamWrite")()("LatencyOf")
    case _:BankedStreamWrite[_] => model("ParStreamWrite")()("LatencyOf")
    case _:BufferedOutWrite[_]  => model("BufferedOutWrite")()("LatencyOf")

    // FIFOs
    case _:FIFOEnq[_]         => model("FIFOEnq")()("LatencyOf")
    case _:BankedFIFOEnq[_]   => model("ParFIFOEnq")()("LatencyOf")
    case _:FIFODeq[_]         => model("FIFODeq")()("LatencyOf")
    case _:BankedFIFODeq[_]   => model("ParFIFODeq")()("LatencyOf")
    case _:FIFONumel[_]       => model("FIFONumel")()("LatencyOf")
    case _:FIFOAlmostEmpty[_] => model("FIFOAlmostEmpty")()("LatencyOf")
    case _:FIFOAlmostFull[_]  => model("FIFOAlmostFull")()("LatencyOf")
    case _:FIFOEmpty[_]       => model("FIFOEmpty")()("LatencyOf")
    case _:FIFOFull[_]        => model("FIFOFull")()("LatencyOf")

    // SRAMs
    // TODO: Should be a function of number of banks?
    case _:SRAMLoad[_]     => if (spatialConfig.enableSyncMem) model("SRAMLoadSyncMem")()("LatencyOf") else model("SRAMLoad")()("LatencyOf")
    case _:BankedSRAMLoad[_]  => if (spatialConfig.enableSyncMem) model("ParSRAMLoadSyncMem")()("LatencyOf") else model("ParSRAMLoad")()("LatencyOf")
    case _:SRAMStore[_]    => model("SRAMStore")()("LatencyOf")
    case _:BankedSRAMStore[_] => model("ParSRAMStore")()("LatencyOf")

    // LineBuffer
    case _:LineBufferEnq[_]        => model("LineBufferEnq")()("LatencyOf")
    case _:BankedLineBufferEnq[_]  => model("ParLineBufferEnq")()("LatencyOf")
    case _:LineBufferLoad[_]       => model("LineBufferLoad")()("LatencyOf")
    case _:BankedLineBufferLoad[_] => model("ParLineBufferLoad")()("LatencyOf")

    // Shift Register
    case DelayLine(size, data) => model("DelayLine")()("LatencyOf") // TODO: Should use different model once these are added?

    // DRAM
    case GetDRAMAddress(_) => model("GetDRAMAddress")()("LatencyOf")

    // Boolean operations
    case Not(_)     => model("Not")()("LatencyOf")
    case And(_,_)   => model("And")()("LatencyOf")
    case Or(_,_)    => model("Or")()("LatencyOf")
    case XOr(_,_)   => model("XOr")()("LatencyOf")
    case XNor(_,_)  => model("XNor")()("LatencyOf")

    // Fixed point math
    // TODO: Have to get numbers for non-32 bit multiplies and divides
    case FixNeg(_)   => model("FixNeg")("b" -> nbits(s))("LatencyOf")
    case FixInv(_)   => model("FixInv")()("LatencyOf")
    case FixAdd(_,_) => model("FixAdd")("b" -> nbits(s))("LatencyOf")
    case FixSub(_,_) => model("FixSub")("b" -> nbits(s))("LatencyOf")
    case FixMul(_,_) => model("FixMul")("b" -> nbits(s))("LatencyOf")  // TODO
    case FixDiv(_,_) => model("FixDiv")("b" -> nbits(s))("LatencyOf") // TODO
    case FixMod(_,_) => model("FixMod")("b" -> nbits(s))("LatencyOf")
    case FixLt(_,_)  => model("FixLt")()("LatencyOf")
    case FixLeq(_,_) => model("FixLeq")()("LatencyOf")
    case FixNeq(_,_) => model("FixNeq")()("LatencyOf")
    case FixEql(_,_) => model("FixEql")()("LatencyOf")
    case FixAnd(_,_) => model("FixAnd")()("LatencyOf")
    case FixOr(_,_)  => model("FixOr")()("LatencyOf")
    case FixXor(_,_) => model("FixXor")()("LatencyOf")
    case FixLsh(_,_) => model("FixLsh")()("LatencyOf") // TODO
    case FixRsh(_,_) => model("FixRsh")()("LatencyOf") // TODO
    case FixURsh(_,_) => model("FixURsh")()("LatencyOf") // TODO
    case FixAbs(_)    => model("FixAbs")()("LatencyOf")

    // Saturating and/or unbiased math
    case SatAdd(x,y) => model("SatAdd")()("LatencyOf")
    case SatSub(x,y) => model("SatSub")()("LatencyOf")
    case SatMul(x,y) => model("SatMul")()("LatencyOf")
    case SatDiv(x,y) => model("SatDiv")()("LatencyOf")
    case UnbMul(x,y) => model("UnbMul")()("LatencyOf")
    case UnbDiv(x,y) => model("UnbDiv")()("LatencyOf")
    case UnbSatMul(x,y) => model("UnbSatMul")()("LatencyOf")
    case UnbSatDiv(x,y) => model("UnbSatDiv")()("LatencyOf")

    // Floating point math
    // TODO: Floating point for things besides single precision
    case FltAbs(_)  => model("FltAbs")()("LatencyOf")
    case FltNeg(_)  => model("FltNeg")()("LatencyOf")
    case FltAdd(_,_) if s.tp == FloatType => model("FltAddFloat")()("LatencyOf")
    case FltSub(_,_) if s.tp == FloatType => model("FltSubFloat")()("LatencyOf")
    case FltMul(_,_) if s.tp == FloatType => model("FltMulFloat")()("LatencyOf")
    case FltDiv(_,_) if s.tp == FloatType => model("FltDivFloat")()("LatencyOf")

    case FltLt(a,_)  if a.tp == FloatType => model("FltLtFloat")()("LatencyOf")
    case FltLeq(a,_) if a.tp == FloatType => model("FltLeqFloat")()("LatencyOf")

    case FltNeq(a,_) if a.tp == FloatType => model("FltNeqFloat")()("LatencyOf")
    case FltEql(a,_) if a.tp == FloatType => model("FltEqlFloat")()("LatencyOf")

    case FltLog(_) if s.tp == FloatType => model("FltLogFloat")()("LatencyOf")
    case FltExp(_) if s.tp == FloatType => model("FltExpFloat")()("LatencyOf")
    case FltSqrt(_) if s.tp == FloatType => model("FltSqrtFloat")()("LatencyOf")

    case Mux(_,_,_)  => model("Mux")()("LatencyOf")
    case Min(_,_)    => model("Min")()("LatencyOf")
    case Max(_,_)    => model("Max")()("LatencyOf")

    case FixConvert(_)  => model("FixConvert")()("LatencyOf")
    case FltConvert(_)  => model("FltConvert")()("LatencyOf") // TODO

    case FltPtToFixPt(x) if x.tp == FloatType => model("FltPtToFixPtFloat")()("LatencyOf")
    case FixPtToFltPt(x) if s.tp == FloatType => model("FixPtToFltPtFloat")()("LatencyOf")

    case _:Hwblock             => model("Hwblock")()("LatencyOf")
    case _:ParallelPipe        => model("ParallelPipe")()("LatencyOf")
    case _:UnitPipe            => model("UnitPipe")()("LatencyOf")
    case _:OpForeach           => model("OpForeach")()("LatencyOf")
    case _:OpReduce[_]         => model("OpReduce")()("LatencyOf")
    case _:OpMemReduce[_,_]    => model("OpMemReduce")()("LatencyOf")
    case _:UnrolledForeach     => model("UnrolledForeach")()("LatencyOf")
    case _:UnrolledReduce      => model("UnrolledReduce")()("LatencyOf")
    case _:Switch[_]           => model("Switch")()("LatencyOf")
    case _:SwitchCase[_]       => model("SwitchCase")()("LatencyOf")

      // Host/Debugging/Unsynthesizable nodes
    case _:ExitIf  => model("ExitIf")()("LatencyOf")
    case _:BreakpointIf  => model("BreakpointIf")()("LatencyOf")
    case _:PrintIf   => model("PrintIf")()("LatencyOf")
    case _:PrintlnIf => model("PrintlnIf")()("LatencyOf")
    case _:AssertIf  => model("AssertIf")()("LatencyOf")
    case _:ToString[_] => model("ToString")()("LatencyOf")
    case _:StringConcat => model("StringConcat")()("LatencyOf")
    case FixRandom(_) => model("FixRandom")()("LatencyOf")  // TODO: This is synthesizable now?
    case FltRandom(_) => model("FltRandom")()("LatencyOf")  // TODO: This is synthesizable now?

    case _:Char2Int => 0
    case _:StateMachine[_] => 0

    case _ =>
      miss(u"${d.getClass} (rule)")
      0
    }

  // For some templates, I have to manually put a delay inside the template to break a critical path that retime would not do on its own.
  //   For example, FIFODeq has a critical path for certain apps on the deq boolean, so we need to stick a register on this input and then treat the output as
  //   having a latency of 2 but needing only 1 injected by transformer
  @stateful def builtInLatencyOfNode(s: Exp[_]): Double = { 
    s match {
      case Def(FieldApply(_,_))    => model("FieldApply")()("BuiltInLatency")
      case Def(VectorApply(_,_))   => model("VectorApply")()("BuiltInLatency")
      case Def(VectorSlice(_,_,_)) => model("VectorSlice")()("BuiltInLatency")
      case Def(VectorConcat(_))    => model("VectorConcat")()("BuiltInLatency")
      case Def(DataAsBits(_))      => model("DataAsBits")()("BuiltInLatency")
      case Def(BitsAsData(_,_))    => model("BitsAsData")()("BuiltInLatency")

      case Def(_:VarRegNew[_])   => model("VarRegNew")()("BuiltInLatency")
      case Def(_:VarRegRead[_])  => model("VarRegRead")()("BuiltInLatency")
      case Def(_:VarRegWrite[_]) => model("VarRegWrite")()("BuiltInLatency")

      case Def(_:LUTLoad[_]) => model("LUTLoad")()("BuiltInLatency")

      // Registers
      case Def(_:RegRead[_])  => model("RegRead")()("BuiltInLatency")
      case Def(_:RegWrite[_]) => model("RegWrite")()("BuiltInLatency")
      case Def(_:RegReset[_]) => model("RegReset")()("BuiltInLatency")

      // Register File
      case Def(_:RegFileLoad[_])          => model("RegFileLoad")()("BuiltInLatency")
      case Def(_:BankedRegFileLoad[_])    => model("ParRegFileLoad")()("BuiltInLatency")
      case Def(_:RegFileStore[_])         => model("RegFileStore")()("BuiltInLatency")
      case Def(_:BankedRegFileStore[_])   => model("ParRegFileStore")()("BuiltInLatency")
      case Def(_:RegFileShiftIn[_])       => model("RegFileShiftIn")()("BuiltInLatency")
      case Def(_:RegFileVectorShiftIn[_]) => model("ParRegFileShiftIn")()("BuiltInLatency")

      // Streams
      case Def(_:StreamRead[_])        => model("StreamRead")()("BuiltInLatency")
      case Def(_:BankedStreamRead[_])  => model("ParStreamRead")()("BuiltInLatency")
      case Def(_:StreamWrite[_])       => model("StreamWrite")()("BuiltInLatency")
      case Def(_:BankedStreamWrite[_]) => model("ParStreamWrite")()("BuiltInLatency")
      case Def(_:BufferedOutWrite[_])  => model("BufferedOutWrite")()("BuiltInLatency")

      // FIFOs
      case Def(_:FIFOEnq[_])         => model("FIFOEnq")()("BuiltInLatency")
      case Def(_:BankedFIFOEnq[_])   => model("ParFIFOEnq")()("BuiltInLatency")
      case Def(_:FIFODeq[_])         => model("FIFODeq")()("BuiltInLatency")
      case Def(_:BankedFIFODeq[_])   => model("ParFIFODeq")()("BuiltInLatency")
      case Def(_:FIFONumel[_])       => model("FIFONumel")()("BuiltInLatency")
      case Def(_:FIFOAlmostEmpty[_]) => model("FIFOAlmostEmpty")()("BuiltInLatency")
      case Def(_:FIFOAlmostFull[_])  => model("FIFOAlmostFull")()("BuiltInLatency")
      case Def(_:FIFOEmpty[_])       => model("FIFOEmpty")()("BuiltInLatency")
      case Def(_:FIFOFull[_])        => model("FIFOFull")()("BuiltInLatency")

      // SRAMs
      // TODO: Should be a function of number of banks?
      case Def(_:SRAMLoad[_])        => if (spatialConfig.enableSyncMem) model("SRAMLoadSyncMem")()("BuiltInLatency") else model("SRAMLoad")()("BuiltInLatency")
      case Def(_:BankedSRAMLoad[_])  => if (spatialConfig.enableSyncMem) model("ParSRAMLoadSyncMem")()("BuiltInLatency") else model("ParSRAMLoad")()("BuiltInLatency")
      case Def(_:SRAMStore[_])       => model("SRAMStore")()("BuiltInLatency")
      case Def(_:BankedSRAMStore[_]) => model("ParSRAMStore")()("BuiltInLatency")

      // LineBuffer
      case Def(_:LineBufferEnq[_])        => model("LineBufferEnq")()("BuiltInLatency")
      case Def(_:BankedLineBufferEnq[_])  => model("ParLineBufferEnq")()("BuiltInLatency")
      case Def(_:LineBufferLoad[_])       => model("LineBufferLoad")()("BuiltInLatency")
      case Def(_:BankedLineBufferLoad[_]) => model("ParLineBufferLoad")()("BuiltInLatency")

      // Shift Register
      case Def(DelayLine(size, data)) => model("DelayLine")()("BuiltInLatency") // TODO: Should use different model once these are added?

      // DRAM
      case Def(GetDRAMAddress(_)) => model("GetDRAMAddress")()("BuiltInLatency")

      // Boolean operations
      case Def(Not(_))     => model("Not")()("BuiltInLatency")
      case Def(And(_,_))   => model("And")()("BuiltInLatency")
      case Def(Or(_,_))    => model("Or")()("BuiltInLatency")
      case Def(XOr(_,_))   => model("XOr")()("BuiltInLatency")
      case Def(XNor(_,_))  => model("XNor")()("BuiltInLatency")

      // Fixed point math
      // TODO: Have to get numbers for non-32 bit multiplies and divides
      case Def(FixNeg(_))   => model("FixNeg")("b" -> nbits(s))("BuiltInLatency")
      case Def(FixInv(_))   => model("FixInv")()("BuiltInLatency")
      case Def(FixAdd(_,_)) => model("FixAdd")("b" -> nbits(s))("BuiltInLatency")
      case Def(FixSub(_,_)) => model("FixSub")("b" -> nbits(s))("BuiltInLatency")
      case Def(FixMul(_,_)) => model("FixMul")("b" -> nbits(s))("BuiltInLatency")  // TODO
      case Def(FixDiv(_,_)) => model("FixDiv")("b" -> nbits(s))("BuiltInLatency") // TODO
      case Def(FixMod(_,_)) => model("FixMod")("b" -> nbits(s))("BuiltInLatency")
      case Def(FixLt(_,_))  => model("FixLt")()("BuiltInLatency")
      case Def(FixLeq(_,_)) => model("FixLeq")()("BuiltInLatency")
      case Def(FixNeq(_,_)) => model("FixNeq")()("BuiltInLatency")
      case Def(FixEql(_,_)) => model("FixEql")()("BuiltInLatency")
      case Def(FixAnd(_,_)) => model("FixAnd")()("BuiltInLatency")
      case Def(FixOr(_,_))  => model("FixOr")()("BuiltInLatency")
      case Def(FixXor(_,_)) => model("FixXor")()("BuiltInLatency")
      case Def(FixLsh(_,_)) => model("FixLsh")()("BuiltInLatency") // TODO
      case Def(FixRsh(_,_)) => model("FixRsh")()("BuiltInLatency") // TODO
      case Def(FixURsh(_,_)) => model("FixURsh")()("BuiltInLatency") // TODO
      case Def(FixAbs(_))    => model("FixAbs")()("BuiltInLatency")

      // Saturating and/or unbiased math
      case Def(SatAdd(x,y)) => model("SatAdd")()("BuiltInLatency")
      case Def(SatSub(x,y)) => model("SatSub")()("BuiltInLatency")
      case Def(SatMul(x,y)) => model("SatMul")()("BuiltInLatency")
      case Def(SatDiv(x,y)) => model("SatDiv")()("BuiltInLatency")
      case Def(UnbMul(x,y)) => model("UnbMul")()("BuiltInLatency")
      case Def(UnbDiv(x,y)) => model("UnbDiv")()("BuiltInLatency")
      case Def(UnbSatMul(x,y)) => model("UnbSatMul")()("BuiltInLatency")
      case Def(UnbSatDiv(x,y)) => model("UnbSatDiv")()("BuiltInLatency")

      // Floating point math
      // TODO: Floating point for things besides single precision
      case Def(FltAbs(_))  => model("FltAbs")()("BuiltInLatency")
      case Def(FltNeg(_))  => model("FltNeg")()("BuiltInLatency")
      case Def(FltAdd(_,_)) if s.tp == FloatType => model("FltAddFloat")()("BuiltInLatency")
      case Def(FltSub(_,_)) if s.tp == FloatType => model("FltSubFloat")()("BuiltInLatency")
      case Def(FltMul(_,_)) if s.tp == FloatType => model("FltMulFloat")()("BuiltInLatency")
      case Def(FltDiv(_,_)) if s.tp == FloatType => model("FltDivFloat")()("BuiltInLatency")

      case Def(FltLt(a,_))  if a.tp == FloatType => model("FltLtFloat")()("BuiltInLatency")
      case Def(FltLeq(a,_)) if a.tp == FloatType => model("FltLeqFloat")()("BuiltInLatency")

      case Def(FltNeq(a,_)) if a.tp == FloatType => model("FltNeqFloat")()("BuiltInLatency")
      case Def(FltEql(a,_)) if a.tp == FloatType => model("FltEqlFloat")()("BuiltInLatency")

      case Def(FltLog(_)) if s.tp == FloatType => model("FltLogFloat")()("BuiltInLatency")
      case Def(FltExp(_)) if s.tp == FloatType => model("FltExpFloat")()("BuiltInLatency")
      case Def(FltSqrt(_)) if s.tp == FloatType => model("FltSqrtFloat")()("BuiltInLatency")

      case Def(Mux(_,_,_))  => model("Mux")()("BuiltInLatency")
      case Def(Min(_,_))    => model("Min")()("BuiltInLatency")
      case Def(Max(_,_))    => model("Max")()("BuiltInLatency")

      case Def(FixConvert(_))  => model("FixConvert")()("BuiltInLatency")
      case Def(FltConvert(_))  => model("FltConvert")()("BuiltInLatency") // TODO

      case Def(FltPtToFixPt(x)) if x.tp == FloatType => model("FltPtToFixPtFloat")()("BuiltInLatency")
      case Def(FixPtToFltPt(x)) if s.tp == FloatType => model("FixPtToFltPtFloat")()("BuiltInLatency")

      case Def(_:Hwblock)             => model("Hwblock")()("BuiltInLatency")
      case Def(_:ParallelPipe)        => model("ParallelPipe")()("BuiltInLatency")
      case Def(_:UnitPipe)            => model("UnitPipe")()("BuiltInLatency")
      case Def(_:OpForeach)           => model("OpForeach")()("BuiltInLatency")
      case Def(_:OpReduce[_])         => model("OpReduce")()("BuiltInLatency")
      case Def(_:OpMemReduce[_,_])    => model("OpMemReduce")()("BuiltInLatency")
      case Def(_:UnrolledForeach)     => model("UnrolledForeach")()("BuiltInLatency")
      case Def(_:UnrolledReduce)      => model("UnrolledReduce")()("BuiltInLatency")
      case Def(_:Switch[_])           => model("Switch")()("BuiltInLatency")
      case Def(_:SwitchCase[_])       => model("SwitchCase")()("BuiltInLatency")

        // Host/Debugging/Unsynthesizable nodes
      case Def(_:ExitIf)  => model("ExitIf")()("BuiltInLatency")
      case Def(_:BreakpointIf)  => model("BreakpointIf")()("BuiltInLatency")
      case Def(_:PrintIf)   => model("PrintIf")()("BuiltInLatency")
      case Def(_:PrintlnIf) => model("PrintlnIf")()("BuiltInLatency")
      case Def(_:AssertIf)  => model("AssertIf")()("BuiltInLatency")
      case Def(_:ToString[_]) => model("ToString")()("BuiltInLatency")
      case Def(_:StringConcat) => model("StringConcat")()("BuiltInLatency")
      case Def(FixRandom(_)) => model("FixRandom")()("BuiltInLatency")  // TODO: This is synthesizable now?
      case Def(FltRandom(_)) => model("FltRandom")()("BuiltInLatency")  // TODO: This is synthesizable now?

      case _ =>
        miss(u"${s} (rule)")
        0
    }
  }

}
