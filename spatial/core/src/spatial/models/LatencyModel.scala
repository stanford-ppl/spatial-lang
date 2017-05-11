package spatial.models

import argon.Config
import spatial.SpatialExp

trait LatencyModel {
  val IR: SpatialExp
  import IR._

  var clockRate = 1000.0f        // Frequency in MHz
  var baseCycles = 43000        // Number of cycles required for startup
  var addRetimeRegisters = true // Enable adding registers after specified comb. logic
  var modelVerbosity = 1

  def silence(): Unit = { modelVerbosity = -1 }

  def apply(s: Exp[_], inReduce: Boolean = false): Long = latencyOf(s, inReduce)

  def latencyOf(s: Exp[_], inReduce: Boolean): Long = {
    val prevVerbosity = Config.verbosity
    Config.verbosity = modelVerbosity
    val latency = s match {
      case Exact(_) => 0
      case Final(_) => 0
      case Def(d) if inReduce  => latencyOfNodeInReduce(s, d)
      case Def(d) if !inReduce => latencyOfNode(s, d)
      case _ => 0
    }
    Config.verbosity = prevVerbosity
    latency
  }

  private def latencyOfNodeInReduce(s: Exp[_], d: Def): Long = d match {
    case FltAdd(_,_)     => 1
    case RegWrite(_,_,_) => 0
    case _ => latencyOfNode(s, d)
  }

  def nbits(e: Exp[_]) = e.tp match { case Bits(bits) => bits.length }
  def sign(e: Exp[_]) = e.tp match { case FixPtType(s,_,_) => s; case _ => true }

  def requiresRegisters(s: Exp[_]): Boolean = addRetimeRegisters && getDef(s).exists{
    // Register File
    case _:RegFileLoad[_]    => true
    case _:ParRegFileLoad[_] => true
    // Streams

    // FIFOs
    case _:FIFODeq[_]    => true
    case _:ParFIFODeq[_] => true

    // SRAMs
    // TODO: Should be a function of number of banks?
    case _:SRAMLoad[_]     => true
    case _:ParSRAMLoad[_]  => true

    // LineBuffer
    case _:LineBufferLoad[_]    => true
    case _:ParLineBufferLoad[_] => true

    // Shift Register
    // None

    case Not(_)     => true
    case And(_,_)   => true
    case Or(_,_)    => true
    case XOr(_,_)   => true
    case XNor(_,_)  => true
    case FixNeg(_)   => true
    case FixAdd(_,_) => true
    case FixSub(_,_) => true
    case FixMul(_,_) => true
    case FixDiv(_,_) => true
    case FixMod(_,_) => true
    case FixLt(_,_)  => true
    case FixLeq(_,_) => true
    case FixNeq(_,_) => true
    case FixEql(_,_) => true
    case FixAnd(_,_) => true
    case FixOr(_,_)  => true
    case FixLsh(_,_) => true
    case FixRsh(_,_) => true
    case FixURsh(_,_) => true
    case FixAbs(_)    => true
    case FixConvert(_) => true

    case SatAdd(x,y) => true
    case SatSub(x,y) => true
    case SatMul(x,y) => true
    case SatDiv(x,y) => true
    case UnbMul(x,y) => true
    case UnbDiv(x,y) => true
    case UnbSatMul(x,y) => true
    case UnbSatDiv(x,y) => true

    case Mux(_,_,_) => true
    case Min(_,_)   => true
    case Max(_,_)   => true
    case _ => false
  }

  protected def latencyOfNode(s: Exp[_], d: Def): Long = d match {
    case d if isAllocation(d) => 0
    case FieldApply(_,_)    => 0
    case VectorApply(_,_)   => 0
    case VectorSlice(_,_,_) => 0
    case VectorConcat(_)    => 0
    case DataAsBits(_)      => 0
    case BitsAsData(_,_)    => 0

    // Registers
    case _:RegRead[_]  => 0
    case _:RegWrite[_] => 1

    // Register File
    case _:RegFileLoad[_]       => 1
    case _:ParRegFileLoad[_]    => 1
    case _:RegFileStore[_]      => 1
    case _:ParRegFileStore[_]   => 1
    case _:RegFileShiftIn[_]    => 1
    case _:ParRegFileShiftIn[_] => 1

    // Streams
    case _:StreamRead[_]     => 0
    case _:ParStreamRead[_]  => 0
    case _:StreamWrite[_]    => 0
    case _:ParStreamWrite[_] => 0

    // FIFOs
    case _:FIFOEnq[_]    => 1
    case _:ParFIFOEnq[_] => 1
    case _:FIFODeq[_]    => 1
    case _:ParFIFODeq[_] => 1

    // SRAMs
    // TODO: Should be a function of number of banks?
    case _:SRAMLoad[_]     => 1
    case _:ParSRAMLoad[_]  => 1
    case _:SRAMStore[_]    => 1
    case _:ParSRAMStore[_] => 1

    // LineBuffer
    case _:LineBufferEnq[_]     => 1
    case _:ParLineBufferEnq[_]  => 1
    case _:LineBufferLoad[_]    => 1
    case _:ParLineBufferLoad[_] => 1

    // Shift Register
    case ValueDelay(size, data) => 0 // wrong but it works???
    case _:ShiftRegNew[_] => 0
    case ShiftRegRead(reg@Op(ShiftRegNew(size,_))) => size
    case _:ShiftRegWrite[_] => 0

    // DRAM
    case GetDRAMAddress(_) => 0

    // Boolean operations
    case Not(_)     => 1
    case And(_,_)   => 1
    case Or(_,_)    => 1
    case XOr(_,_)   => 1
    case XNor(_,_)  => 1

    // Fixed point math
    // TODO: Have to get numbers for non-32 bit multiplies and divides
    case FixNeg(_)   => 1
    case FixAdd(_,_) => 1
    case FixSub(_,_) => 1
    case FixMul(_,_) => 1 // TODO
    case FixDiv(_,_) => 1 // TODO
    case FixMod(_,_) => 1
    case FixLt(_,_)  => 1
    case FixLeq(_,_) => 1
    case FixNeq(_,_) => 1
    case FixEql(_,_) => 1
    case FixAnd(_,_) => 1
    case FixOr(_,_)  => 1
    case FixLsh(_,_) => 1 // TODO
    case FixRsh(_,_) => 1 // TODO
    case FixURsh(_,_) => 1 // TODO
    case FixAbs(_)    => 1

    // Saturating and/or unbiased math
    case SatAdd(x,y) => 1
    case SatSub(x,y) => 1
    case SatMul(x,y) => 1
    case SatDiv(x,y) => 1
    case UnbMul(x,y) => 1
    case UnbDiv(x,y) => 1
    case UnbSatMul(x,y) => 1
    case UnbSatDiv(x,y) => 1

    // Floating point math
    // TODO: Floating point for things besides single precision
    case FltNeg(_) =>
      //if (nbits(s) != 32) warn(s"Don't know latency for $d - using default")
      1

    case FltAdd(_,_) =>
      //if (nbits(s) != 32) warn(s"Don't know latency for $d - using default")
      14

    case FltSub(_,_) =>
      //if (nbits(s) != 32) warn(s"Don't know latency for $d - using default")
      14

    case FltMul(_,_) =>
      //if (nbits(s) != 32) warn(s"Don't know latency for $d - using default")
      11

    case FltDiv(_,_) =>
      //if (nbits(s) != 32) warn(s"Don't know latency for $d - using default")
      33

    case FltLt(a,_)  =>
      //if (nbits(a) != 32) warn(s"Don't know latency for $d - using default")
      3

    case FltLeq(a,_) =>
      //if (nbits(a) != 32) warn(s"Don't know latency for $d - using default")
      3

    case FltNeq(a,_) =>
      //if (nbits(a) != 32) warn(s"Don't know latency for $d - using default")
      3

    case FltEql(a,_) =>
      //if (nbits(a) != 32) warn(s"Don't know latency for $d - using default")
      3

    case FltAbs(_) => 1
    case FltLog(_) =>
      //if (nbits(s) != 32) warn(s"Don't know latency for $d - using default")
      35

    case FltExp(_) =>
      //if (nbits(s) != 32) warn(s"Don't know latency for $d - using default")
      27

    case FltSqrt(_) =>
      //if (nbits(s) != 32) warn(s"Don't know latency for $d - using default")
      28

    case Mux(_,_,_) => 1
    case Min(_,_)   => 1
    case Max(_,_)   => 1

    case FixConvert(_) => 1
    case FltConvert(_) => 6 // TODO

    case FltPtToFixPt(x) =>
      //if (nbits(s) != 32 && nbits(x) != 32) warn(s"Don't know latency for $d - using default")
      6

    case FixPtToFltPt(x) =>
      //if (nbits(s) != 32 && nbits(x) != 32) warn(s"Don't know latency for $d - using default")
      6

    // TODO
    /*case BurstStore(mem,stream,ofs,len,par) =>
      val c = contentionOf(s)
      val p = bound(par).get
      val size = bound(len).getOrElse{warn("Cannot resolve bound of offchip store")(mpos(s.pos)); 96.0}

      val baseCycles = size / p.toDouble

      val oFactor = 0.02*c - 0.019
      val smallOverhead = if (c < 8) 0.0 else 0.0175
      val overhead = if (p < 8) 1.0 + smallOverhead*p else oFactor*p + (1 - (8*oFactor)) + smallOverhead*8

      //System.out.println(s"Sizes: $sizes, base cycles: $baseCycles, ofactor: $oFactor, smallOverhead: $smallOverhead, overhead: $overhead")
      Math.ceil(baseCycles*overhead).toLong

    case BurstLoad(mem,stream,ofs,len,par) =>
      val c = contentionOf(s)
      val ts = bound(len).getOrElse{stageWarn("Cannot resolve bound of offchip load")(mpos(s.pos)); 96.0}
      val b = ts  // TODO - max of this and max command size
      val r = 1.0 // TODO - number of commands needed (probably 1)
      val p = bound(par).get
      //System.out.println(s"Tile transfer $s: c = $c, r = $r, b = $b, p = $p")
      memoryModel(c,r.toInt,b.toInt,p.toInt)*/

    case _:Hwblock             => 1
    case _:ParallelPipe        => 1
    case _:UnitPipe            => 0
    case _:OpForeach           => 1
    case _:OpReduce[_]         => 1
    case _:OpMemReduce[_,_]    => 1
    case _:UnrolledForeach     => 1
    case _:UnrolledReduce[_,_] => 1

    // Host/Debugging/Unsynthesizable nodes
    case _:PrintIf   => 0
    case _:PrintlnIf => 0
    case _:AssertIf  => 0
    case _:ToString[_] => 0
    case _:TextConcat => 0
    case FixRandom(_) => 0
    case FltRandom(_) => 0

    case _ =>
      warn(s"Don't know latency of $d - using default of 0")
      0
    }
}
