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
    case FixMul(_,_) => model("FixMul")()("RequiresInReduce") > 0
    case d => latencyOfNodeInReduce(s,d) > 0
  }

  @stateful protected def requiresRegisters(s: Exp[_]): Boolean = addRetimeRegisters && getDef(s).exists{
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
    case _ => false
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
}
