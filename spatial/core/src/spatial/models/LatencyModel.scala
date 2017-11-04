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

  @stateful def apply(s: Exp[_], inReduce: Boolean = false): Long = latencyOf(s, inReduce)

  @stateful def latencyOf(s: Exp[_], inReduce: Boolean): Long = {
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

  @stateful protected def latencyOfNodeInReduce(s: Exp[_], d: Def): Long = d match {
    case FixAdd(_,_)     => model("FixAdd")()("LatencyInReduce").toLong
    case Mux(_,_,_)      => model("Mux")()("LatencyInReduce").toLong
    case FltAdd(_,_)     => model("FltAdd")()("LatencyInReduce").toLong
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
    case FixAdd(_,_) => model("FixAdd")()("RequiresRegs") > 0
    case FixSub(_,_) => model("FixSub")()("RequiresRegs") > 0
    case FixMul(_,_) => model("FixMul")()("RequiresRegs") > 0
    case FixDiv(_,_) => model("FixDiv")()("RequiresRegs") > 0
    case FixMod(_,_) => model("FixMod")()("RequiresRegs") > 0
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

  @stateful protected def latencyOfNode(s: Exp[_], d: Def): Long = d match {
    case d if isAllocation(d) => model("isAllocation")()("LatencyOf").toLong
    case FieldApply(_,_)    => model("FieldApply")()("LatencyOf").toLong
    case VectorApply(_,_)   => model("VectorApply")()("LatencyOf").toLong
    case VectorSlice(_,_,_) => model("VectorSlice")()("LatencyOf").toLong
    case VectorConcat(_)    => model("VectorConcat")()("LatencyOf").toLong
    case DataAsBits(_)      => model("DataAsBits")()("LatencyOf").toLong
    case BitsAsData(_,_)    => model("BitsAsData")()("LatencyOf").toLong

    case _:VarRegNew[_]   => model("VarRegNew")()("LatencyOf").toLong
    case _:VarRegRead[_]  => model("VarRegRead")()("LatencyOf").toLong
    case _:VarRegWrite[_] => model("VarRegWrite")()("LatencyOf").toLong

    case _:LUTLoad[_] => model("LUTLoad")()("LatencyOf").toLong

    // Registers
    case _:RegRead[_]  => model("RegRead")()("LatencyOf").toLong
    case _:RegWrite[_] => model("RegWrite")()("LatencyOf").toLong
    case _:RegReset[_] => model("RegReset")()("LatencyOf").toLong

    // Register File
    case _:RegFileLoad[_]       => model("RegFileLoad")()("LatencyOf").toLong
    case _:ParRegFileLoad[_]    => model("ParRegFileLoad")()("LatencyOf").toLong
    case _:RegFileStore[_]      => model("RegFileStore")()("LatencyOf").toLong
    case _:ParRegFileStore[_]   => model("ParRegFileStore")()("LatencyOf").toLong
    case _:RegFileShiftIn[_]    => model("RegFileShiftIn")()("LatencyOf").toLong
    case _:RegFileVectorShiftIn[_] => model("ParRegFileShiftIn")()("LatencyOf").toLong

    // Streams
    case _:StreamRead[_]        => model("StreamRead")()("LatencyOf").toLong
    case _:ParStreamRead[_]     => model("ParStreamRead")()("LatencyOf").toLong
    case _:StreamWrite[_]       => model("StreamWrite")()("LatencyOf").toLong
    case _:ParStreamWrite[_]    => model("ParStreamWrite")()("LatencyOf").toLong
    case _:BufferedOutWrite[_]  => model("BufferedOutWrite")()("LatencyOf").toLong

    // FIFOs
    case _:FIFOEnq[_]    => model("FIFOEnq")()("LatencyOf").toLong
    case _:ParFIFOEnq[_] => model("ParFIFOEnq")()("LatencyOf").toLong
    case _:FIFODeq[_]    => model("FIFODeq")()("LatencyOf").toLong
    case _:ParFIFODeq[_] => model("ParFIFODeq")()("LatencyOf").toLong
    case _:FIFONumel[_]  => model("FIFONumel")()("LatencyOf").toLong
    case _:FIFOAlmostEmpty[_] => model("FIFOAlmostEmpty")()("LatencyOf").toLong
    case _:FIFOAlmostFull[_]  => model("FIFOAlmostFull")()("LatencyOf").toLong
    case _:FIFOEmpty[_]       => model("FIFOEmpty")()("LatencyOf").toLong
    case _:FIFOFull[_]        => model("FIFOFull")()("LatencyOf").toLong

    // SRAMs
    // TODO: Should be a function of number of banks?
    case _:SRAMLoad[_]     => if (spatialConfig.enableSyncMem) model("SRAMLoadSyncMem")()("LatencyOf").toLong else model("SRAMLoad")()("LatencyOf").toLong
    case _:ParSRAMLoad[_]  => if (spatialConfig.enableSyncMem) model("ParSRAMLoadSyncMem")()("LatencyOf").toLong else model("ParSRAMLoad")()("LatencyOf").toLong
    case _:SRAMStore[_]    => model("SRAMStore")()("LatencyOf").toLong
    case _:ParSRAMStore[_] => model("ParSRAMStore")()("LatencyOf").toLong

    // LineBuffer
    case _:LineBufferEnq[_]     => model("LineBufferEnq")()("LatencyOf").toLong
    case _:ParLineBufferEnq[_]  => model("ParLineBufferEnq")()("LatencyOf").toLong
    case _:LineBufferLoad[_]    => model("LineBufferLoad")()("LatencyOf").toLong
    case _:ParLineBufferLoad[_] => model("ParLineBufferLoad")()("LatencyOf").toLong

    // Shift Register
    case DelayLine(size, data) => model("DelayLine")()("LatencyOf").toLong // TODO: Should use different model once these are added?

    // DRAM
    case GetDRAMAddress(_) => model("GetDRAMAddress")()("LatencyOf").toLong

    // Boolean operations
    case Not(_)     => model("Not")()("LatencyOf").toLong
    case And(_,_)   => model("And")()("LatencyOf").toLong
    case Or(_,_)    => model("Or")()("LatencyOf").toLong
    case XOr(_,_)   => model("XOr")()("LatencyOf").toLong
    case XNor(_,_)  => model("XNor")()("LatencyOf").toLong

    // Fixed point math
    // TODO: Have to get numbers for non-32 bit multiplies and divides
    case FixNeg(_)   => model("FixNeg")()("LatencyOf").toLong
    case FixInv(_)   => model("FixInv")()("LatencyOf").toLong
    case FixAdd(_,_) => model("FixAdd")()("LatencyOf").toLong
    case FixSub(_,_) => model("FixSub")()("LatencyOf").toLong
    case FixMul(_,_) => model("FixMul")()("LatencyOf").toLong  // TODO
    case FixDiv(_,_) => model("FixDiv")()("LatencyOf").toLong // TODO
    case FixMod(_,_) => model("FixMod")()("LatencyOf").toLong
    case FixLt(_,_)  => model("FixLt")()("LatencyOf").toLong
    case FixLeq(_,_) => model("FixLeq")()("LatencyOf").toLong
    case FixNeq(_,_) => model("FixNeq")()("LatencyOf").toLong
    case FixEql(_,_) => model("FixEql")()("LatencyOf").toLong
    case FixAnd(_,_) => model("FixAnd")()("LatencyOf").toLong
    case FixOr(_,_)  => model("FixOr")()("LatencyOf").toLong
    case FixXor(_,_) => model("FixXor")()("LatencyOf").toLong
    case FixLsh(_,_) => model("FixLsh")()("LatencyOf").toLong // TODO
    case FixRsh(_,_) => model("FixRsh")()("LatencyOf").toLong // TODO
    case FixURsh(_,_) => model("FixURsh")()("LatencyOf").toLong // TODO
    case FixAbs(_)    => model("FixAbs")()("LatencyOf").toLong

    // Saturating and/or unbiased math
    case SatAdd(x,y) => model("SatAdd")()("LatencyOf").toLong
    case SatSub(x,y) => model("SatSub")()("LatencyOf").toLong
    case SatMul(x,y) => model("SatMul")()("LatencyOf").toLong
    case SatDiv(x,y) => model("SatDiv")()("LatencyOf").toLong
    case UnbMul(x,y) => model("UnbMul")()("LatencyOf").toLong
    case UnbDiv(x,y) => model("UnbDiv")()("LatencyOf").toLong
    case UnbSatMul(x,y) => model("UnbSatMul")()("LatencyOf").toLong
    case UnbSatDiv(x,y) => model("UnbSatDiv")()("LatencyOf").toLong

    // Floating point math
    // TODO: Floating point for things besides single precision
    case FltAbs(_)  => model("FltAbs")()("LatencyOf").toLong
    case FltNeg(_)  => model("FltNeg")()("LatencyOf").toLong
    case FltAdd(_,_) if s.tp == FloatType => model("FltAddFloat")()("LatencyOf").toLong
    case FltSub(_,_) if s.tp == FloatType => model("FltSubFloat")()("LatencyOf").toLong
    case FltMul(_,_) if s.tp == FloatType => model("FltMulFloat")()("LatencyOf").toLong
    case FltDiv(_,_) if s.tp == FloatType => model("FltDivFloat")()("LatencyOf").toLong

    case FltLt(a,_)  if a.tp == FloatType => model("FltLtFloat")()("LatencyOf").toLong
    case FltLeq(a,_) if a.tp == FloatType => model("FltLeqFloat")()("LatencyOf").toLong

    case FltNeq(a,_) if a.tp == FloatType => model("FltNeqFloat")()("LatencyOf").toLong
    case FltEql(a,_) if a.tp == FloatType => model("FltEqlFloat")()("LatencyOf").toLong

    case FltLog(_) if s.tp == FloatType => model("FltLogFloat")()("LatencyOf").toLong
    case FltExp(_) if s.tp == FloatType => model("FltExpFloat")()("LatencyOf").toLong
    case FltSqrt(_) if s.tp == FloatType => model("FltSqrtFloat")()("LatencyOf").toLong

    case Mux(_,_,_)  => model("Mux")()("LatencyOf").toLong
    case Min(_,_)    => model("Min")()("LatencyOf").toLong
    case Max(_,_)    => model("Max")()("LatencyOf").toLong

    case FixConvert(_)  => model("FixConvert")()("LatencyOf").toLong
    case FltConvert(_)  => model("FltConvert")()("LatencyOf").toLong // TODO

    case FltPtToFixPt(x) if x.tp == FloatType => model("FltPtToFixPtFloat")()("LatencyOf").toLong
    case FixPtToFltPt(x) if s.tp == FloatType => model("FixPtToFltPtFloat")()("LatencyOf").toLong

    case _:Hwblock             => model("Hwblock")()("LatencyOf").toLong
    case _:ParallelPipe        => model("ParallelPipe")()("LatencyOf").toLong
    case _:UnitPipe            => model("UnitPipe")()("LatencyOf").toLong
    case _:OpForeach           => model("OpForeach")()("LatencyOf").toLong
    case _:OpReduce[_]         => model("OpReduce")()("LatencyOf").toLong
    case _:OpMemReduce[_,_]    => model("OpMemReduce")()("LatencyOf").toLong
    case _:UnrolledForeach     => model("UnrolledForeach")()("LatencyOf").toLong
    case _:UnrolledReduce[_,_] => model("UnrolledReduce")()("LatencyOf").toLong
    case _:Switch[_]           => model("Switch")()("LatencyOf").toLong
    case _:SwitchCase[_]       => model("SwitchCase")()("LatencyOf").toLong

      // Host/Debugging/Unsynthesizable nodes
    case _:ExitIf  => model("ExitIf")()("LatencyOf").toLong
    case _:BreakpointIf  => model("BreakpointIf")()("LatencyOf").toLong
    case _:PrintIf   => model("PrintIf")()("LatencyOf").toLong
    case _:PrintlnIf => model("PrintlnIf")()("LatencyOf").toLong
    case _:AssertIf  => model("AssertIf")()("LatencyOf").toLong
    case _:ToString[_] => model("ToString")()("LatencyOf").toLong
    case _:StringConcat => model("StringConcat")()("LatencyOf").toLong
    case FixRandom(_) => model("FixRandom")()("LatencyOf").toLong  // TODO: This is synthesizable now?
    case FltRandom(_) => model("FltRandom")()("LatencyOf").toLong  // TODO: This is synthesizable now?

    case _ =>
      miss(u"${d.getClass} (rule)")
      0
    }
}
