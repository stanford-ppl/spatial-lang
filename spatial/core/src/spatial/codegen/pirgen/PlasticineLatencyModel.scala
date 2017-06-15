package spatial.codegen.pirgen

import argon.internals._
import argon.nodes._
import spatial.compiler._
import spatial.nodes._
import spatial.utils._
import spatial.models.LatencyModel

trait PlasticineLatencyModel extends LatencyModel {

  override protected def latencyOfNode(s: Exp[_], d: Def): Long = d match {
    case d if isAllocation(d) => 0
    case FieldApply(_,_)    => 0
    case VectorApply(_,_)   => 0
    case VectorSlice(_,_,_) => 0
    case VectorConcat(_)    => 0
    case DataAsBits(_)      => 0
    case BitsAsData(_,_)    => 0

    // Registers
    case _:RegRead[_]  => 0
    case _:RegWrite[_] => 0

    // Register File
    case _:RegFileLoad[_]       => 0
    case _:ParRegFileLoad[_]    => 0
    case _:RegFileStore[_]      => 0
    case _:ParRegFileStore[_]   => 0
    case _:RegFileShiftIn[_]    => 0
    case _:ParRegFileShiftIn[_] => 0

    // Streams
    case _:StreamRead[_]     => 0
    case _:ParStreamRead[_]  => 0
    case _:StreamWrite[_]    => 0
    case _:ParStreamWrite[_] => 0

    // FIFOs
    case _:FIFOEnq[_]    => 0
    case _:ParFIFOEnq[_] => 0
    case _:FIFODeq[_]    => 0
    case _:ParFIFODeq[_] => 0

    // SRAMs
    // TODO: Should be a function of number of banks?
    case _:SRAMLoad[_]     => 0
    case _:ParSRAMLoad[_]  => 0
    case _:SRAMStore[_]    => 0
    case _:ParSRAMStore[_] => 0

    // LineBuffer
    case _:LineBufferEnq[_]     => 0
    case _:ParLineBufferEnq[_]  => 0
    case _:LineBufferLoad[_]    => 0
    case _:ParLineBufferLoad[_] => 0

    // Shift Register
    case DelayLine(size, data) => 0 // wrong but it works???

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
      1

    case FltSub(_,_) =>
      //if (nbits(s) != 32) warn(s"Don't know latency for $d - using default")
      1

    case FltMul(_,_) =>
      //if (nbits(s) != 32) warn(s"Don't know latency for $d - using default")
      1

    case FltDiv(_,_) =>
      //if (nbits(s) != 32) warn(s"Don't know latency for $d - using default")
      1

    case FltLt(a,_)  =>
      //if (nbits(a) != 32) warn(s"Don't know latency for $d - using default")
      1

    case FltLeq(a,_) =>
      //if (nbits(a) != 32) warn(s"Don't know latency for $d - using default")
      1

    case FltNeq(a,_) =>
      //if (nbits(a) != 32) warn(s"Don't know latency for $d - using default")
      1

    case FltEql(a,_) =>
      //if (nbits(a) != 32) warn(s"Don't know latency for $d - using default")
      1

    case FltAbs(_) => 1
    case FltLog(_) =>
      //if (nbits(s) != 32) warn(s"Don't know latency for $d - using default")
      1

    case FltExp(_) =>
      //if (nbits(s) != 32) warn(s"Don't know latency for $d - using default")
      1

    case FltSqrt(_) =>
      //if (nbits(s) != 32) warn(s"Don't know latency for $d - using default")
      1

    case Mux(_,_,_) => 1
    case Min(_,_)   => 1
    case Max(_,_)   => 1

    case FixConvert(_) => 1
    case FltConvert(_) => 1 // TODO

    case FltPtToFixPt(x) =>
      //if (nbits(s) != 32 && nbits(x) != 32) warn(s"Don't know latency for $d - using default")
      1

    case FixPtToFltPt(x) =>
      //if (nbits(s) != 32 && nbits(x) != 32) warn(s"Don't know latency for $d - using default")
      1

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
    case _:StringConcat => 0
    case FixRandom(_) => 0
    case FltRandom(_) => 0

    case _ =>
      warn(s"Don't know latency of $d - using default of 0")
      0
  }
}
