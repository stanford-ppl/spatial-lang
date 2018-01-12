package spatial.codegen.pirgen

import argon.core._
import argon.nodes._
import forge._
import spatial.aliases._
import spatial.nodes._
import spatial.utils._
import spatial.models.LatencyModel

trait PlasticineLatencyModel extends LatencyModel {
  val FILE_NAME = "HackyLatency.csv"

  @stateful override protected def latencyOfNode(s: Exp[_], d: Def): Double = d match {
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
    case FixNeg(_)   => 1
    case FixAdd(_,_) => 1
    case FixSub(_,_) => 1
    case FixMul(_,_) => 1
    case FixDiv(_,_) => 1
    case FixMod(_,_) => 1
    case FixLt(_,_)  => 1
    case FixLeq(_,_) => 1
    case FixNeq(_,_) => 1
    case FixEql(_,_) => 1
    case FixAnd(_,_) => 1
    case FixOr(_,_)  => 1
    case FixLsh(_,_) => 1
    case FixRsh(_,_) => 1
    case FixURsh(_,_) => 1
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
    // TODO: Floating point for things besides single precision?
    case FltNeg(_)   => 1
    case FltAdd(_,_) => 1
    case FltSub(_,_) => 1
    case FltMul(_,_) => 1
    case FltDiv(_,_) => 1

    case FltLt(a,_)  => 1
    case FltLeq(a,_) => 1
    case FltNeq(a,_) => 1
    case FltEql(a,_) => 1

    case FltAbs(_) => 1
    case FltLog(_) => 1
    case FltExp(_) => 1

    case FltSqrt(_) => 1

    case Mux(_,_,_) => 1
    case Min(_,_)   => 1
    case Max(_,_)   => 1
    case FixConvert(_) => 1
    case FltConvert(_) => 1
    case FltPtToFixPt(x) => 1
    case FixPtToFltPt(x) => 1

    case _:Hwblock             => 1
    case _:UnitPipe            => 0
    case _:OpForeach           => 1
    case _:OpReduce[_]         => 1
    case _:OpMemReduce[_,_]    => 1
    case _:UnrolledForeach     => 1
    case _:UnrolledReduce[_,_] => 1

      // Host/Debugging/Unsynthesizable nodes
    case _: ExitIf  => 0      
    case _: BreakpointIf  => 0      
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
