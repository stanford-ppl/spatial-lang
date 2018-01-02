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
    case FixAdd(_,_)     => model("FixAdd")("b" -> nbits(s))("LatencyInReduce").toDouble
    case Mux(_,_,_)      => model("Mux")()("LatencyInReduce").toDouble
    case FltAdd(_,_)     => model("FltAdd")()("LatencyInReduce").toDouble
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
    case _:SRAMLoad[_]     => if (spatialConfig.enableSyncMem) model("SRAMLoadSyncMem")()("RequiresInReduce") > 0 else model("SRAMLoad")()("RequiresInReduce") > 0
    case _:ParSRAMLoad[_]  => if (spatialConfig.enableSyncMem) model("ParSRAMLoadSyncMem")()("RequiresInReduce") > 0 else model("ParSRAMLoad")()("RequiresInReduce") > 0
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
    case _:RegFileStore[_]      => model("RegFileStore")()("RequiresRegs") > 0
    case _:ParRegFileStore[_]   => model("ParRegFileStore")()("RequiresRegs") > 0
    case _:RegFileShiftIn[_]    => model("RegFileShiftIn")()("RequiresRegs") > 0
    case _:ParRegFileShiftIn[_] => model("ParRegFileShiftIn")()("RequiresRegs") > 0

    // Streams
    case _:StreamRead[_]        => model("StreamRead")()("RequiresRegs") > 0
    case _:ParStreamRead[_]     => model("ParStreamRead")()("RequiresRegs") > 0
    case _:StreamWrite[_]       => model("StreamWrite")()("RequiresRegs") > 0
    case _:ParStreamWrite[_]    => model("ParStreamWrite")()("RequiresRegs") > 0
    case _:BufferedOutWrite[_]  => model("BufferedOutWrite")()("RequiresRegs") > 0

    // FIFOs
    case _:FIFOEnq[_]    => model("FIFOEnq")()("RequiresRegs") > 0
    case _:ParFIFOEnq[_] => model("ParFIFOEnq")()("RequiresRegs") > 0
    case _:FIFONumel[_]  => model("FIFONumel")()("RequiresRegs") > 0
    case _:FIFOAlmostEmpty[_] => model("FIFOAlmostEmpty")()("RequiresRegs") > 0
    case _:FIFOAlmostFull[_]  => model("FIFOAlmostFull")()("RequiresRegs") > 0
    case _:FIFOEmpty[_]       => model("FIFOEmpty")()("RequiresRegs") > 0
    case _:FIFOFull[_]        => model("FIFOFull")()("RequiresRegs") > 0

    // SRAMs
    // TODO: Should be a function of number of banks?
    case _:SRAMStore[_]    => model("SRAMStore")()("RequiresRegs") > 0
    case _:ParSRAMStore[_] => model("ParSRAMStore")()("RequiresRegs") > 0

    // LineBuffer
    case _:LineBufferEnq[_]     => model("LineBufferEnq")()("RequiresRegs") > 0
    case _:ParLineBufferEnq[_]  => model("ParLineBufferEnq")()("RequiresRegs") > 0

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
    case _:UnrolledReduce[_,_] => model("UnrolledReduce")()("RequiresRegs") > 0
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
    case _:RegFileLoad[_]    => model("RegFileLoad")()("RequiresRegs") > 0
    case _:ParRegFileLoad[_] => model("ParRegFileLoad")()("RequiresRegs") > 0
    // Streams

    // FIFOs
    case _:FIFODeq[_]    => model("FIFODeq")()("RequiresRegs") > 0
    case _:ParFIFODeq[_] => model("ParFIFODeq")()("RequiresRegs") > 0

    // SRAMs
    // TODO: Should be a function of number of banks?
    case _:SRAMLoad[_]     => if (spatialConfig.enableSyncMem) model("SRAMLoadSyncMem")()("RequiresRegs") > 0 else model("SRAMLoad")()("RequiresRegs") > 0
    case _:ParSRAMLoad[_]  => if (spatialConfig.enableSyncMem) model("ParSRAMLoadSyncMem")()("RequiresRegs") > 0 else model("ParSRAMLoad")()("RequiresRegs") > 0

    // LineBuffer
    case _:LineBufferLoad[_]    => model("LineBufferLoad")()("RequiresRegs") > 0
    case _:ParLineBufferLoad[_] => model("ParLineBufferLoad")()("RequiresRegs") > 0

    // Shift Register
    // None

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
    case d if isAllocation(d) => model("isAllocation")()("LatencyOf").toDouble
    case FieldApply(_,_)    => model("FieldApply")()("LatencyOf").toDouble
    case VectorApply(_,_)   => model("VectorApply")()("LatencyOf").toDouble
    case VectorSlice(_,_,_) => model("VectorSlice")()("LatencyOf").toDouble
    case VectorConcat(_)    => model("VectorConcat")()("LatencyOf").toDouble
    case DataAsBits(_)      => model("DataAsBits")()("LatencyOf").toDouble
    case BitsAsData(_,_)    => model("BitsAsData")()("LatencyOf").toDouble

    case _:VarRegNew[_]   => model("VarRegNew")()("LatencyOf").toDouble
    case _:VarRegRead[_]  => model("VarRegRead")()("LatencyOf").toDouble
    case _:VarRegWrite[_] => model("VarRegWrite")()("LatencyOf").toDouble

    case _:LUTLoad[_] => model("LUTLoad")()("LatencyOf").toDouble

    // Registers
    case _:RegRead[_]  => model("RegRead")()("LatencyOf").toDouble
    case _:RegWrite[_] => model("RegWrite")()("LatencyOf").toDouble
    case _:RegReset[_] => model("RegReset")()("LatencyOf").toDouble

    // Register File
    case _:RegFileLoad[_]       => model("RegFileLoad")()("LatencyOf").toDouble
    case _:ParRegFileLoad[_]    => model("ParRegFileLoad")()("LatencyOf").toDouble
    case _:RegFileStore[_]      => model("RegFileStore")()("LatencyOf").toDouble
    case _:ParRegFileStore[_]   => model("ParRegFileStore")()("LatencyOf").toDouble
    case _:RegFileShiftIn[_]    => model("RegFileShiftIn")()("LatencyOf").toDouble
    case _:ParRegFileShiftIn[_] => model("ParRegFileShiftIn")()("LatencyOf").toDouble

    // Streams
    case _:StreamRead[_]        => model("StreamRead")()("LatencyOf").toDouble
    case _:ParStreamRead[_]     => model("ParStreamRead")()("LatencyOf").toDouble
    case _:StreamWrite[_]       => model("StreamWrite")()("LatencyOf").toDouble
    case _:ParStreamWrite[_]    => model("ParStreamWrite")()("LatencyOf").toDouble
    case _:BufferedOutWrite[_]  => model("BufferedOutWrite")()("LatencyOf").toDouble

    // FIFOs
    case _:FIFOEnq[_]    => model("FIFOEnq")()("LatencyOf").toDouble
    case _:ParFIFOEnq[_] => model("ParFIFOEnq")()("LatencyOf").toDouble
    case _:FIFODeq[_]    => model("FIFODeq")()("LatencyOf").toDouble
    case _:ParFIFODeq[_] => model("ParFIFODeq")()("LatencyOf").toDouble
    case _:FIFONumel[_]  => model("FIFONumel")()("LatencyOf").toDouble
    case _:FIFOAlmostEmpty[_] => model("FIFOAlmostEmpty")()("LatencyOf").toDouble
    case _:FIFOAlmostFull[_]  => model("FIFOAlmostFull")()("LatencyOf").toDouble
    case _:FIFOEmpty[_]       => model("FIFOEmpty")()("LatencyOf").toDouble
    case _:FIFOFull[_]        => model("FIFOFull")()("LatencyOf").toDouble

    // SRAMs
    // TODO: Should be a function of number of banks?
    case _:SRAMLoad[_]     => if (spatialConfig.enableSyncMem) model("SRAMLoadSyncMem")()("LatencyOf").toDouble else model("SRAMLoad")()("LatencyOf").toDouble
    case _:ParSRAMLoad[_]  => if (spatialConfig.enableSyncMem) model("ParSRAMLoadSyncMem")()("LatencyOf").toDouble else model("ParSRAMLoad")()("LatencyOf").toDouble
    case _:SRAMStore[_]    => model("SRAMStore")()("LatencyOf").toDouble
    case _:ParSRAMStore[_] => model("ParSRAMStore")()("LatencyOf").toDouble

    // LineBuffer
    case _:LineBufferEnq[_]     => model("LineBufferEnq")()("LatencyOf").toDouble
    case _:ParLineBufferEnq[_]  => model("ParLineBufferEnq")()("LatencyOf").toDouble
    case _:LineBufferLoad[_]    => model("LineBufferLoad")()("LatencyOf").toDouble
    case _:ParLineBufferLoad[_] => model("ParLineBufferLoad")()("LatencyOf").toDouble

    // Shift Register
    case DelayLine(size, data) => model("DelayLine")()("LatencyOf").toDouble // TODO: Should use different model once these are added?

    // DRAM
    case GetDRAMAddress(_) => model("GetDRAMAddress")()("LatencyOf").toDouble

    // Boolean operations
    case Not(_)     => model("Not")()("LatencyOf").toDouble
    case And(_,_)   => model("And")()("LatencyOf").toDouble
    case Or(_,_)    => model("Or")()("LatencyOf").toDouble
    case XOr(_,_)   => model("XOr")()("LatencyOf").toDouble
    case XNor(_,_)  => model("XNor")()("LatencyOf").toDouble

    // Fixed point math
    // TODO: Have to get numbers for non-32 bit multiplies and divides
    case FixNeg(_)   => model("FixNeg")("b" -> nbits(s))("LatencyOf").toDouble
    case FixInv(_)   => model("FixInv")()("LatencyOf").toDouble
    case FixAdd(_,_) => model("FixAdd")("b" -> nbits(s))("LatencyOf").toDouble
    case FixSub(_,_) => model("FixSub")("b" -> nbits(s))("LatencyOf").toDouble
    case FixMul(_,_) => model("FixMul")("b" -> nbits(s))("LatencyOf").toDouble  // TODO
    case FixDiv(_,_) => model("FixDiv")("b" -> nbits(s))("LatencyOf").toDouble // TODO
    case FixMod(_,_) => model("FixMod")("b" -> nbits(s))("LatencyOf").toDouble
    case FixLt(_,_)  => model("FixLt")()("LatencyOf").toDouble
    case FixLeq(_,_) => model("FixLeq")()("LatencyOf").toDouble
    case FixNeq(_,_) => model("FixNeq")()("LatencyOf").toDouble
    case FixEql(_,_) => model("FixEql")()("LatencyOf").toDouble
    case FixAnd(_,_) => model("FixAnd")()("LatencyOf").toDouble
    case FixOr(_,_)  => model("FixOr")()("LatencyOf").toDouble
    case FixXor(_,_) => model("FixXor")()("LatencyOf").toDouble
    case FixLsh(_,_) => model("FixLsh")()("LatencyOf").toDouble // TODO
    case FixRsh(_,_) => model("FixRsh")()("LatencyOf").toDouble // TODO
    case FixURsh(_,_) => model("FixURsh")()("LatencyOf").toDouble // TODO
    case FixAbs(_)    => model("FixAbs")()("LatencyOf").toDouble

    // Saturating and/or unbiased math
    case SatAdd(x,y) => model("SatAdd")()("LatencyOf").toDouble
    case SatSub(x,y) => model("SatSub")()("LatencyOf").toDouble
    case SatMul(x,y) => model("SatMul")()("LatencyOf").toDouble
    case SatDiv(x,y) => model("SatDiv")()("LatencyOf").toDouble
    case UnbMul(x,y) => model("UnbMul")()("LatencyOf").toDouble
    case UnbDiv(x,y) => model("UnbDiv")()("LatencyOf").toDouble
    case UnbSatMul(x,y) => model("UnbSatMul")()("LatencyOf").toDouble
    case UnbSatDiv(x,y) => model("UnbSatDiv")()("LatencyOf").toDouble

    // Floating point math
    // TODO: Floating point for things besides single precision
    case FltAbs(_)  => model("FltAbs")()("LatencyOf").toDouble
    case FltNeg(_)  => model("FltNeg")()("LatencyOf").toDouble
    case FltAdd(_,_) if s.tp == FloatType => model("FltAddFloat")()("LatencyOf").toDouble
    case FltSub(_,_) if s.tp == FloatType => model("FltSubFloat")()("LatencyOf").toDouble
    case FltMul(_,_) if s.tp == FloatType => model("FltMulFloat")()("LatencyOf").toDouble
    case FltDiv(_,_) if s.tp == FloatType => model("FltDivFloat")()("LatencyOf").toDouble

    case FltLt(a,_)  if a.tp == FloatType => model("FltLtFloat")()("LatencyOf").toDouble
    case FltLeq(a,_) if a.tp == FloatType => model("FltLeqFloat")()("LatencyOf").toDouble

    case FltNeq(a,_) if a.tp == FloatType => model("FltNeqFloat")()("LatencyOf").toDouble
    case FltEql(a,_) if a.tp == FloatType => model("FltEqlFloat")()("LatencyOf").toDouble

    case FltLog(_) if s.tp == FloatType => model("FltLogFloat")()("LatencyOf").toDouble
    case FltExp(_) if s.tp == FloatType => model("FltExpFloat")()("LatencyOf").toDouble
    case FltSqrt(_) if s.tp == FloatType => model("FltSqrtFloat")()("LatencyOf").toDouble

    case Mux(_,_,_)  => model("Mux")()("LatencyOf").toDouble
    case Min(_,_)    => model("Min")()("LatencyOf").toDouble
    case Max(_,_)    => model("Max")()("LatencyOf").toDouble

    case FixConvert(_)  => model("FixConvert")()("LatencyOf").toDouble
    case FltConvert(_)  => model("FltConvert")()("LatencyOf").toDouble // TODO

    case FltPtToFixPt(x) if x.tp == FloatType => model("FltPtToFixPtFloat")()("LatencyOf").toDouble
    case FixPtToFltPt(x) if s.tp == FloatType => model("FixPtToFltPtFloat")()("LatencyOf").toDouble

    case _:Hwblock             => model("Hwblock")()("LatencyOf").toDouble
    case _:ParallelPipe        => model("ParallelPipe")()("LatencyOf").toDouble
    case _:UnitPipe            => model("UnitPipe")()("LatencyOf").toDouble
    case _:OpForeach           => model("OpForeach")()("LatencyOf").toDouble
    case _:OpReduce[_]         => model("OpReduce")()("LatencyOf").toDouble
    case _:OpMemReduce[_,_]    => model("OpMemReduce")()("LatencyOf").toDouble
    case _:UnrolledForeach     => model("UnrolledForeach")()("LatencyOf").toDouble
    case _:UnrolledReduce[_,_] => model("UnrolledReduce")()("LatencyOf").toDouble
    case _:Switch[_]           => model("Switch")()("LatencyOf").toDouble
    case _:SwitchCase[_]       => model("SwitchCase")()("LatencyOf").toDouble

      // Host/Debugging/Unsynthesizable nodes
    case _:ExitIf  => model("ExitIf")()("LatencyOf").toDouble
    case _:BreakpointIf  => model("BreakpointIf")()("LatencyOf").toDouble
    case _:PrintIf   => model("PrintIf")()("LatencyOf").toDouble
    case _:PrintlnIf => model("PrintlnIf")()("LatencyOf").toDouble
    case _:AssertIf  => model("AssertIf")()("LatencyOf").toDouble
    case _:ToString[_] => model("ToString")()("LatencyOf").toDouble
    case _:StringConcat => model("StringConcat")()("LatencyOf").toDouble
    case FixRandom(_) => model("FixRandom")()("LatencyOf").toDouble  // TODO: This is synthesizable now?
    case FltRandom(_) => model("FltRandom")()("LatencyOf").toDouble  // TODO: This is synthesizable now?

    case _ =>
      miss(u"${d.getClass} (rule)")
      0
    }

  // For some templates, I have to manually put a delay inside the template to break a critical path that retime would not do on its own.
  //   For example, FIFODeq has a critical path for certain apps on the deq boolean, so we need to stick a register on this input and then treat the output as
  //   having a latency of 2 but needing only 1 injected by transformer
  @stateful def builtInLatencyOfNode(s: Exp[_]): Double = { 
    s match {
      case Def(FieldApply(_,_))    => model("FieldApply")()("BuiltInLatency").toDouble
      case Def(VectorApply(_,_))   => model("VectorApply")()("BuiltInLatency").toDouble
      case Def(VectorSlice(_,_,_)) => model("VectorSlice")()("BuiltInLatency").toDouble
      case Def(VectorConcat(_))    => model("VectorConcat")()("BuiltInLatency").toDouble
      case Def(DataAsBits(_))      => model("DataAsBits")()("BuiltInLatency").toDouble
      case Def(BitsAsData(_,_))    => model("BitsAsData")()("BuiltInLatency").toDouble

      case Def(_:VarRegNew[_])   => model("VarRegNew")()("BuiltInLatency").toDouble
      case Def(_:VarRegRead[_])  => model("VarRegRead")()("BuiltInLatency").toDouble
      case Def(_:VarRegWrite[_]) => model("VarRegWrite")()("BuiltInLatency").toDouble

      case Def(_:LUTLoad[_]) => model("LUTLoad")()("BuiltInLatency").toDouble

      // Registers
      case Def(_:RegRead[_])  => model("RegRead")()("BuiltInLatency").toDouble
      case Def(_:RegWrite[_]) => model("RegWrite")()("BuiltInLatency").toDouble
      case Def(_:RegReset[_]) => model("RegReset")()("BuiltInLatency").toDouble

      // Register File
      case Def(_:RegFileLoad[_])       => model("RegFileLoad")()("BuiltInLatency").toDouble
      case Def(_:ParRegFileLoad[_])    => model("ParRegFileLoad")()("BuiltInLatency").toDouble
      case Def(_:RegFileStore[_])      => model("RegFileStore")()("BuiltInLatency").toDouble
      case Def(_:ParRegFileStore[_])   => model("ParRegFileStore")()("BuiltInLatency").toDouble
      case Def(_:RegFileShiftIn[_])    => model("RegFileShiftIn")()("BuiltInLatency").toDouble
      case Def(_:ParRegFileShiftIn[_]) => model("ParRegFileShiftIn")()("BuiltInLatency").toDouble

      // Streams
      case Def(_:StreamRead[_])        => model("StreamRead")()("BuiltInLatency").toDouble
      case Def(_:ParStreamRead[_])     => model("ParStreamRead")()("BuiltInLatency").toDouble
      case Def(_:StreamWrite[_])       => model("StreamWrite")()("BuiltInLatency").toDouble
      case Def(_:ParStreamWrite[_])    => model("ParStreamWrite")()("BuiltInLatency").toDouble
      case Def(_:BufferedOutWrite[_])  => model("BufferedOutWrite")()("BuiltInLatency").toDouble

      // FIFOs
      case Def(_:FIFOEnq[_])    => model("FIFOEnq")()("BuiltInLatency").toDouble
      case Def(_:ParFIFOEnq[_]) => model("ParFIFOEnq")()("BuiltInLatency").toDouble
      case Def(_:FIFODeq[_])    => model("FIFODeq")()("BuiltInLatency").toDouble
      case Def(_:ParFIFODeq[_]) => model("ParFIFODeq")()("BuiltInLatency").toDouble
      case Def(_:FIFONumel[_])  => model("FIFONumel")()("BuiltInLatency").toDouble
      case Def(_:FIFOAlmostEmpty[_]) => model("FIFOAlmostEmpty")()("BuiltInLatency").toDouble
      case Def(_:FIFOAlmostFull[_])  => model("FIFOAlmostFull")()("BuiltInLatency").toDouble
      case Def(_:FIFOEmpty[_])       => model("FIFOEmpty")()("BuiltInLatency").toDouble
      case Def(_:FIFOFull[_])        => model("FIFOFull")()("BuiltInLatency").toDouble

      // SRAMs
      // TODO: Should be a function of number of banks?
      case Def(_:SRAMLoad[_])     => if (spatialConfig.enableSyncMem) model("SRAMLoadSyncMem")()("BuiltInLatency").toDouble else model("SRAMLoad")()("BuiltInLatency").toDouble
      case Def(_:ParSRAMLoad[_])  => if (spatialConfig.enableSyncMem) model("ParSRAMLoadSyncMem")()("BuiltInLatency").toDouble else model("ParSRAMLoad")()("BuiltInLatency").toDouble
      case Def(_:SRAMStore[_])    => model("SRAMStore")()("BuiltInLatency").toDouble
      case Def(_:ParSRAMStore[_]) => model("ParSRAMStore")()("BuiltInLatency").toDouble

      // LineBuffer
      case Def(_:LineBufferEnq[_])     => model("LineBufferEnq")()("BuiltInLatency").toDouble
      case Def(_:ParLineBufferEnq[_])  => model("ParLineBufferEnq")()("BuiltInLatency").toDouble
      case Def(_:LineBufferLoad[_])    => model("LineBufferLoad")()("BuiltInLatency").toDouble
      case Def(_:ParLineBufferLoad[_]) => model("ParLineBufferLoad")()("BuiltInLatency").toDouble

      // Shift Register
      case Def(DelayLine(size, data)) => model("DelayLine")()("BuiltInLatency").toDouble // TODO: Should use different model once these are added?

      // DRAM
      case Def(GetDRAMAddress(_)) => model("GetDRAMAddress")()("BuiltInLatency").toDouble

      // Boolean operations
      case Def(Not(_))     => model("Not")()("BuiltInLatency").toDouble
      case Def(And(_,_))   => model("And")()("BuiltInLatency").toDouble
      case Def(Or(_,_))    => model("Or")()("BuiltInLatency").toDouble
      case Def(XOr(_,_))   => model("XOr")()("BuiltInLatency").toDouble
      case Def(XNor(_,_))  => model("XNor")()("BuiltInLatency").toDouble

      // Fixed point math
      // TODO: Have to get numbers for non-32 bit multiplies and divides
      case Def(FixNeg(_))   => model("FixNeg")("b" -> nbits(s))("BuiltInLatency").toDouble
      case Def(FixInv(_))   => model("FixInv")()("BuiltInLatency").toDouble
      case Def(FixAdd(_,_)) => model("FixAdd")("b" -> nbits(s))("BuiltInLatency").toDouble
      case Def(FixSub(_,_)) => model("FixSub")("b" -> nbits(s))("BuiltInLatency").toDouble
      case Def(FixMul(_,_)) => model("FixMul")("b" -> nbits(s))("BuiltInLatency").toDouble  // TODO
      case Def(FixDiv(_,_)) => model("FixDiv")("b" -> nbits(s))("BuiltInLatency").toDouble // TODO
      case Def(FixMod(_,_)) => model("FixMod")("b" -> nbits(s))("BuiltInLatency").toDouble
      case Def(FixLt(_,_))  => model("FixLt")()("BuiltInLatency").toDouble
      case Def(FixLeq(_,_)) => model("FixLeq")()("BuiltInLatency").toDouble
      case Def(FixNeq(_,_)) => model("FixNeq")()("BuiltInLatency").toDouble
      case Def(FixEql(_,_)) => model("FixEql")()("BuiltInLatency").toDouble
      case Def(FixAnd(_,_)) => model("FixAnd")()("BuiltInLatency").toDouble
      case Def(FixOr(_,_))  => model("FixOr")()("BuiltInLatency").toDouble
      case Def(FixXor(_,_)) => model("FixXor")()("BuiltInLatency").toDouble
      case Def(FixLsh(_,_)) => model("FixLsh")()("BuiltInLatency").toDouble // TODO
      case Def(FixRsh(_,_)) => model("FixRsh")()("BuiltInLatency").toDouble // TODO
      case Def(FixURsh(_,_)) => model("FixURsh")()("BuiltInLatency").toDouble // TODO
      case Def(FixAbs(_))    => model("FixAbs")()("BuiltInLatency").toDouble

      // Saturating and/or unbiased math
      case Def(SatAdd(x,y)) => model("SatAdd")()("BuiltInLatency").toDouble
      case Def(SatSub(x,y)) => model("SatSub")()("BuiltInLatency").toDouble
      case Def(SatMul(x,y)) => model("SatMul")()("BuiltInLatency").toDouble
      case Def(SatDiv(x,y)) => model("SatDiv")()("BuiltInLatency").toDouble
      case Def(UnbMul(x,y)) => model("UnbMul")()("BuiltInLatency").toDouble
      case Def(UnbDiv(x,y)) => model("UnbDiv")()("BuiltInLatency").toDouble
      case Def(UnbSatMul(x,y)) => model("UnbSatMul")()("BuiltInLatency").toDouble
      case Def(UnbSatDiv(x,y)) => model("UnbSatDiv")()("BuiltInLatency").toDouble

      // Floating point math
      // TODO: Floating point for things besides single precision
      case Def(FltAbs(_))  => model("FltAbs")()("BuiltInLatency").toDouble
      case Def(FltNeg(_))  => model("FltNeg")()("BuiltInLatency").toDouble
      case Def(FltAdd(_,_)) if s.tp == FloatType => model("FltAddFloat")()("BuiltInLatency").toDouble
      case Def(FltSub(_,_)) if s.tp == FloatType => model("FltSubFloat")()("BuiltInLatency").toDouble
      case Def(FltMul(_,_)) if s.tp == FloatType => model("FltMulFloat")()("BuiltInLatency").toDouble
      case Def(FltDiv(_,_)) if s.tp == FloatType => model("FltDivFloat")()("BuiltInLatency").toDouble

      case Def(FltLt(a,_))  if a.tp == FloatType => model("FltLtFloat")()("BuiltInLatency").toDouble
      case Def(FltLeq(a,_)) if a.tp == FloatType => model("FltLeqFloat")()("BuiltInLatency").toDouble

      case Def(FltNeq(a,_)) if a.tp == FloatType => model("FltNeqFloat")()("BuiltInLatency").toDouble
      case Def(FltEql(a,_)) if a.tp == FloatType => model("FltEqlFloat")()("BuiltInLatency").toDouble

      case Def(FltLog(_)) if s.tp == FloatType => model("FltLogFloat")()("BuiltInLatency").toDouble
      case Def(FltExp(_)) if s.tp == FloatType => model("FltExpFloat")()("BuiltInLatency").toDouble
      case Def(FltSqrt(_)) if s.tp == FloatType => model("FltSqrtFloat")()("BuiltInLatency").toDouble

      case Def(Mux(_,_,_))  => model("Mux")()("BuiltInLatency").toDouble
      case Def(Min(_,_))    => model("Min")()("BuiltInLatency").toDouble
      case Def(Max(_,_))    => model("Max")()("BuiltInLatency").toDouble

      case Def(FixConvert(_))  => model("FixConvert")()("BuiltInLatency").toDouble
      case Def(FltConvert(_))  => model("FltConvert")()("BuiltInLatency").toDouble // TODO

      case Def(FltPtToFixPt(x)) if x.tp == FloatType => model("FltPtToFixPtFloat")()("BuiltInLatency").toDouble
      case Def(FixPtToFltPt(x)) if s.tp == FloatType => model("FixPtToFltPtFloat")()("BuiltInLatency").toDouble

      case Def(_:Hwblock)             => model("Hwblock")()("BuiltInLatency").toDouble
      case Def(_:ParallelPipe)        => model("ParallelPipe")()("BuiltInLatency").toDouble
      case Def(_:UnitPipe)            => model("UnitPipe")()("BuiltInLatency").toDouble
      case Def(_:OpForeach)           => model("OpForeach")()("BuiltInLatency").toDouble
      case Def(_:OpReduce[_])         => model("OpReduce")()("BuiltInLatency").toDouble
      case Def(_:OpMemReduce[_,_])    => model("OpMemReduce")()("BuiltInLatency").toDouble
      case Def(_:UnrolledForeach)     => model("UnrolledForeach")()("BuiltInLatency").toDouble
      case Def(_:UnrolledReduce[_,_]) => model("UnrolledReduce")()("BuiltInLatency").toDouble
      case Def(_:Switch[_])           => model("Switch")()("BuiltInLatency").toDouble
      case Def(_:SwitchCase[_])       => model("SwitchCase")()("BuiltInLatency").toDouble

        // Host/Debugging/Unsynthesizable nodes
      case Def(_:ExitIf)  => model("ExitIf")()("BuiltInLatency").toDouble
      case Def(_:BreakpointIf)  => model("BreakpointIf")()("BuiltInLatency").toDouble
      case Def(_:PrintIf)   => model("PrintIf")()("BuiltInLatency").toDouble
      case Def(_:PrintlnIf) => model("PrintlnIf")()("BuiltInLatency").toDouble
      case Def(_:AssertIf)  => model("AssertIf")()("BuiltInLatency").toDouble
      case Def(_:ToString[_]) => model("ToString")()("BuiltInLatency").toDouble
      case Def(_:StringConcat) => model("StringConcat")()("BuiltInLatency").toDouble
      case Def(FixRandom(_)) => model("FixRandom")()("BuiltInLatency").toDouble  // TODO: This is synthesizable now?
      case Def(FltRandom(_)) => model("FltRandom")()("BuiltInLatency").toDouble  // TODO: This is synthesizable now?

      case _ =>
        miss(u"${s} (rule)")
        0
    }
  }

}
