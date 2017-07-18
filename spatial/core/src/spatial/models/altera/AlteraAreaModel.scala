package spatial.models
package altera

import argon.core._
import argon.nodes._
import forge._
import spatial.aliases._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._

abstract class AlteraAreaModel extends AreaModel[AlteraArea] {

  private def areaOfMemory(nbits: Int, dims: Seq[Int], instance: Memory): AlteraArea = {
    // Physical depth for given word size for horizontally concatenated RAMs
    val nElements = dims.product
    val wordDepth = if      (nbits == 1)  16384
                    else if (nbits == 2)  8192
                    else if (nbits <= 5)  4096
                    else if (nbits <= 10) 2048
                    else if (nbits <= 20) 1024
                    else                  512

    // Number of horizontally concatenated RAMs required to implement given word
    val portWidth = if (nbits > 40) Math.ceil( nbits / 40.0 ).toInt else 1

    val bufferDepth = instance.depth

    val controlResourcesPerBank = if (bufferDepth == 1) NoArea else {
      AlteraArea(lut3 = bufferDepth*nbits, regs = bufferDepth * nbits)
    }

    // TODO: This seems suspicious - check later
    instance match {
      case DiagonalMemory(strides, banks, depth, isAccum) =>
        val elementsPerBank = Math.ceil(nElements.toDouble/banks)
        val nRAMsPerBank = Math.ceil(elementsPerBank/wordDepth).toInt * portWidth
        val resourcesPerBank = AlteraArea(sram = nRAMsPerBank)

        (resourcesPerBank + controlResourcesPerBank).replicate(banks, inner=false)

      case BankedMemory(banking,depth,isAccum) =>
        val banks = banking.map(_.banks)
        val nBanks = banks.product
        val elementsPerBank = dims.zip(banks).map{case (dim,bank) => Math.ceil(dim.toDouble/bank).toInt }.product
        val nRAMsPerBank = Math.ceil(elementsPerBank/wordDepth).toInt * portWidth
        val resourcesPerBank = AlteraArea(sram = nRAMsPerBank)

        (resourcesPerBank + controlResourcesPerBank).replicate(nBanks, inner=false)
    }
  }

  private def areaOfSRAM(nbits: Int, dims: Seq[Int], instances: Seq[Memory]): AlteraArea = {
    instances.map{instance => areaOfMemory(nbits, dims, instance) }.fold(NoArea){_+_}
  }

  /**
    * Returns the area resources for a delay line with the given width (in bits) and length (in cycles)
    * Models delays as registers for short delays, BRAM for long ones
    **/
  @stateful override def areaOfDelayLine(width: Int, length: Int, par: Int): AlteraArea = {
    //System.out.println(s"Delay line: w = $width x l = $length (${width*length}) ")
    val nregs = width*length
    AlteraArea(regs = nregs*par)
    /*if (nregs < 256) AlteraArea(regs = nregs*par)
    else             areaOfSRAM(width*par, List(length), List(SimpleInstance))*/
  }

  private def areaOfStreamPipe(n: Int) = NoArea // TODO

  private def areaOfMetaPipe(n: Int) = AlteraArea(
    lut4 = (11*n*n + 45*n)/2 + 105,  // 0.5(11n^2 + 45n) + 105
    regs = (n*n + 3*n)/2 + 35        // 0.5(n^2 + 3n) + 35
  )
  private def areaOfSeqPipe(n: Int) = AlteraArea(lut4=7*n+40, regs=2*n+35)

  @stateful private def areaOfControl(ctrl: Exp[_]) = {
    if (isInnerControl(ctrl)) NoArea
    else if (isSeqPipe(ctrl)) areaOfSeqPipe(nStages(ctrl))
    else if (isMetaPipe(ctrl)) areaOfMetaPipe(nStages(ctrl))
    else if (isStreamPipe(ctrl)) areaOfStreamPipe(nStages(ctrl))
    else NoArea
  }


  @stateful override def areaOf(e: Exp[_], d: Def, inHwScope: Boolean, inReduce: Boolean): AlteraArea = d match {
    case FieldApply(_,_)    => NoArea // No cost
    case VectorApply(_,_)   => NoArea // Statically known index
    case VectorSlice(_,_,_) => NoArea // Statically known slice
    case VectorConcat(_)    => NoArea // No cost
    case DataAsBits(_)      => NoArea // No cost
    case BitsAsData(_,_)    => NoArea // No cost

    case _:VarRegNew[_]   => NoArea // Not synthesizable
    case _:VarRegRead[_]  => NoArea // Not synthesizable
    case _:VarRegWrite[_] => NoArea // Not synthesizable

    // LUTs
    case LUTNew(dims,elems) => NoArea // TODO
    case _:LUTLoad[_] => NoArea // TODO

    // Registers
    case reg:RegNew[_] => duplicatesOf(e).map{
      case BankedMemory(_,depth,isAccum) => AlteraArea(regs = depth * reg.bT.length)
      case _ => NoArea
    }.fold(NoArea){_+_}
    case _:RegRead[_]  => NoArea // No cost
    case _:RegWrite[_] => NoArea // No cost
    case _:RegReset[_] => NoArea // No cost

    // Register File
    case rf:RegFileNew[_,_] =>
      val size = dimsOf(e).product
      val regs = duplicatesOf(e).map{
        case BankedMemory(_,depth,isAccum) => AlteraArea(regs = size * rf.bT.length * depth)
        case _ => NoArea
      }.fold(NoArea){_+_}
      // val muxes = ???

      regs


    case _:RegFileLoad[_]       => NoArea
    case _:ParRegFileLoad[_]    => NoArea
    case _:RegFileStore[_]      => NoArea
    case _:ParRegFileStore[_]   => NoArea
    case _:RegFileShiftIn[_]    => NoArea
    case _:ParRegFileShiftIn[_] => NoArea

    // Streams
    case _:StreamInNew[_]       => NoArea // TODO: Model
    case _:StreamOutNew[_]      => NoArea // TODO: Model
    case _:StreamRead[_]        => NoArea
    case _:ParStreamRead[_]     => NoArea
    case _:StreamWrite[_]       => NoArea
    case _:ParStreamWrite[_]    => NoArea
    case _:BufferedOutWrite[_]  => NoArea

    // FIFOs
    case _:FIFONew[_]    => duplicatesOf(e).map{
      case BankedMemory(_,depth,isAccum) => NoArea // TODO: Model
      case _ => NoArea
    }.fold(NoArea){_+_}
    case _:FIFOEnq[_]    => NoArea
    case _:ParFIFOEnq[_] => NoArea
    case _:FIFODeq[_]    => NoArea
    case _:ParFIFODeq[_] => NoArea
    case _:FIFONumel[_]  => NoArea
    case _:FIFOAlmostEmpty[_] => NoArea
    case _:FIFOAlmostFull[_]  => NoArea
    case _:FIFOEmpty[_]       => NoArea
    case _:FIFOFull[_]        => NoArea

    // SRAMs
    case op:SRAMNew[_,_]   => areaOfSRAM(op.bT.length,dimsOf(e),duplicatesOf(e))
    case _:SRAMLoad[_]     => NoArea
    case _:ParSRAMLoad[_]  => NoArea
    case _:SRAMStore[_]    => NoArea
    case _:ParSRAMStore[_] => NoArea

    // LineBuffer
    case _:LineBufferNew[_]     => duplicatesOf(e).map{
      case BankedMemory(_,depth,isAccum) => NoArea  // TODO: Model
      case _ => NoArea
    }.fold(NoArea){_+_}
    case _:LineBufferEnq[_]     => NoArea
    case _:ParLineBufferEnq[_]  => NoArea
    case _:LineBufferLoad[_]    => NoArea
    case _:ParLineBufferLoad[_] => NoArea

    // Shift Register
    case line @ DelayLine(size, data) => AlteraArea(regs = size * line.bT.length)

    // DRAM
    case _:DRAMNew[_,_]    => NoArea
    case GetDRAMAddress(_) => NoArea

    // Boolean operations
    case Not(_)     => AlteraArea(lut3 = 1, regs = 1) // TODO: Verify
    case And(_,_)   => AlteraArea(lut3 = 1, regs = 1)
    case Or(_,_)    => AlteraArea(lut3 = 1, regs = 1)
    case XOr(_,_)   => AlteraArea(lut3 = 1, regs = 1)
    case XNor(_,_)  => AlteraArea(lut3 = 1, regs = 1)

    // Fixed point math
    // TODO: Have to get numbers for non-32 bit multiplies and divides
    case FixNeg(_)   => AlteraArea(lut3 = 1, regs = nbits(e))
    case FixAdd(_,_) => AlteraArea(lut3 = nbits(e), regs = nbits(e))
    case FixSub(_,_) => AlteraArea(lut3 = nbits(e), regs = nbits(e))
    case FixMul(_,_) => AlteraArea(dsps = Math.ceil(nbits(e).toDouble/16.0).toInt)
    case FixDiv(_,_) => NoArea // TODO
    case FixMod(_,_) => NoArea // TODO
    case FixLt(_,_)  => AlteraArea(lut3 = nbits(e), regs = nbits(e)) // TODO
    case FixLeq(_,_) => AlteraArea(lut3 = nbits(e), regs = nbits(e)) // TODO
    case FixNeq(_,_) => AlteraArea(lut3 = nbits(e), regs = nbits(e)) // TODO
    case FixEql(_,_) => AlteraArea(lut3 = nbits(e), regs = nbits(e)) // TODO
    case FixAnd(_,_) => AlteraArea(lut3 = nbits(e), regs = nbits(e)) // TODO
    case FixOr(_,_)  => AlteraArea(lut3 = nbits(e), regs = nbits(e)) // TODO
    case FixXor(_,_) => AlteraArea(lut3 = nbits(e), regs = nbits(e)) // TODO
    case FixLsh(_,_) => NoArea // TODO
    case FixRsh(_,_) => NoArea // TODO
    case FixURsh(_,_) => NoArea // TODO
    case FixAbs(_)    => NoArea // TODO

    // Saturating and/or unbiased math
    case SatAdd(_,_) => NoArea // TODO
    case SatSub(_,_) => NoArea // TODO
    case SatMul(_,_) => NoArea // TODO
    case SatDiv(_,_) => NoArea // TODO
    case UnbMul(_,_) => NoArea // TODO
    case UnbDiv(_,_) => NoArea // TODO
    case UnbSatMul(_,_) => NoArea // TODO
    case UnbSatDiv(_,_) => NoArea // TODO

    // Floating point math
    // TODO: Floating point area
    case FltNeg(_) => AlteraArea(lut3 = 1, regs = nbits(e))
    case FltAbs(_) => AlteraArea(lut3 = 1, regs = nbits(e))

    case FltAdd(_,_) => NoArea
    case FltSub(_,_) => NoArea
    case FltMul(_,_) => NoArea
    case FltDiv(_,_) => NoArea

    case FltLt(a,_)  => NoArea
    case FltLeq(a,_) => NoArea
    case FltNeq(a,_) => NoArea
    case FltEql(a,_) => NoArea

    case FltLog(_) => NoArea
    case FltExp(_) => NoArea
    case FltSqrt(_) => NoArea

    case Mux(_,_,_) => AlteraArea(lut3 = nbits(e), regs = nbits(e))
    case Min(_,_)   => AlteraArea(lut3 = nbits(e), regs = nbits(e)) // TODO
    case Max(_,_)   => AlteraArea(lut3 = nbits(e), regs = nbits(e)) // TODO

    case FixConvert(_) => NoArea
    case FltConvert(_) => NoArea // TODO

    case FltPtToFixPt(x) => NoArea // TODO
    case FixPtToFltPt(x) => NoArea // TODO

    case _:ParallelPipe => AlteraArea(lut4=9*nStages(e)/2, regs = nStages(e) + 3)

    case _:Hwblock  => areaOfControl(e)
    case _:UnitPipe => areaOfControl(e)

    case _:OpForeach           => areaOfControl(e)
    case _:OpReduce[_]         => areaOfControl(e)
    case _:OpMemReduce[_,_]    => areaOfControl(e)
    case _:UnrolledForeach     => areaOfControl(e)
    case _:UnrolledReduce[_,_] => areaOfControl(e)

    case _:Switch[_] => e.tp match {
      case Bits(bt) => NoArea // TODO
      case _        => NoArea
    }
    case _:SwitchCase[_] => NoArea

    // Host/Debugging/Unsynthesizable nodes
    case _:PrintIf   => NoArea
    case _:PrintlnIf => NoArea
    case _:AssertIf  => NoArea
    case _:ToString[_] => NoArea
    case _:StringConcat => NoArea
    case FixRandom(_) => NoArea  // TODO: This is synthesizable now?
    case FltRandom(_) => NoArea  // TODO: This is synthesizable now?

    case _ =>
      warn(s"Don't know area of $d - using default of 0")
      NoArea
  }
}
