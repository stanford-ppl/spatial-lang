package spatial.models

import argon.core._
import argon.nodes._
import forge._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._

class AlteraAreaModel extends AreaModel[AlteraArea] {

  private def areaOfMemory(nbits: Int, dims: Seq[Int], instance: Memory) = {
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

  private def areaOfSRAM(nbits: Int, dims: Seq[Int], instances: Seq[Memory]) = {
    instances.map{instance => areaOfMemory(nbits, dims, instance) }.fold(NoArea){_+_}
  }

  @stateful override def areaOf(e: Exp[_], d: Def): AlteraArea = d match {
    case FieldApply(_,_)    => NoArea
    case VectorApply(_,_)   => NoArea // Statically known index
    case VectorSlice(_,_,_) => NoArea // Statically known slice
    case VectorConcat(_)    => NoArea
    case DataAsBits(_)      => NoArea
    case BitsAsData(_,_)    => NoArea

    case _:VarRegNew[_]   => NoArea // Not synthesizable
    case _:VarRegRead[_]  => NoArea // Not synthesizable
    case _:VarRegWrite[_] => NoArea // Not synthesizable

    // LUTs
    case LUTNew(dims,elems) => NoArea // TODO


    case _:LUTLoad[_] => NoArea

    // Registers
    case reg:RegNew[_] => duplicatesOf(e).map{
      case BankedMemory(_,depth,isAccum) => AlteraArea(regs = depth * reg.bT.length)
      case _ => NoArea
    }.fold(NoArea){_+_}
    case _:RegRead[_]  => NoArea
    case _:RegWrite[_] => NoArea
    case _:RegReset[_] => NoArea

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
    case SatAdd(x,y) => NoArea // TODO
    case SatSub(x,y) => NoArea // TODO
    case SatMul(x,y) => NoArea // TODO
    case SatDiv(x,y) => NoArea // TODO
    case UnbMul(x,y) => NoArea // TODO
    case UnbDiv(x,y) => NoArea // TODO
    case UnbSatMul(x,y) => NoArea // TODO
    case UnbSatDiv(x,y) => NoArea // TODO

    // Floating point math
    // TODO: Floating point area
    case FltNeg(_) =>
      //if (nbits(s) != 32) warn(s"Don't know latency for $d - using default")
      AlteraArea(lut3 = 1, regs = nbits(e))

    case FltAdd(_,_) =>
      //if (nbits(s) != 32) warn(s"Don't know latency for $d - using default")
      NoArea

    case FltSub(_,_) =>
      //if (nbits(s) != 32) warn(s"Don't know latency for $d - using default")
      NoArea

    case FltMul(_,_) =>
      //if (nbits(s) != 32) warn(s"Don't know latency for $d - using default")
      NoArea

    case FltDiv(_,_) =>
      //if (nbits(s) != 32) warn(s"Don't know latency for $d - using default")
      NoArea

    case FltLt(a,_)  =>
      //if (nbits(a) != 32) warn(s"Don't know latency for $d - using default")
      NoArea

    case FltLeq(a,_) =>
      //if (nbits(a) != 32) warn(s"Don't know latency for $d - using default")
      NoArea

    case FltNeq(a,_) =>
      //if (nbits(a) != 32) warn(s"Don't know latency for $d - using default")
      NoArea

    case FltEql(a,_) =>
      //if (nbits(a) != 32) warn(s"Don't know latency for $d - using default")
      NoArea

    case FltAbs(_) => AlteraArea(lut3 = 1, regs = nbits(e))
    case FltLog(_) =>
      //if (nbits(s) != 32) warn(s"Don't know latency for $d - using default")
      NoArea

    case FltExp(_) =>
      //if (nbits(s) != 32) warn(s"Don't know latency for $d - using default")
      NoArea

    case FltSqrt(_) =>
      //if (nbits(s) != 32) warn(s"Don't know latency for $d - using default")
      NoArea

    case Mux(_,_,_) => AlteraArea(lut3 = nbits(e), regs = nbits(e))
    case Min(_,_)   => AlteraArea(lut3 = nbits(e), regs = nbits(e)) // TODO
    case Max(_,_)   => AlteraArea(lut3 = nbits(e), regs = nbits(e)) // TODO

    case FixConvert(_) => NoArea // TODO
    case FltConvert(_) => NoArea // TODO

    case FltPtToFixPt(x) =>
      //if (nbits(s) != 32 && nbits(x) != 32) warn(s"Don't know latency for $d - using default")
      NoArea // TODO

    case FixPtToFltPt(x) =>
      //if (nbits(s) != 32 && nbits(x) != 32) warn(s"Don't know latency for $d - using default")
      NoArea // TODO

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

    case _:Hwblock             => NoArea
    case _:ParallelPipe        => NoArea
    case _:UnitPipe            => NoArea
    case _:OpForeach           => NoArea
    case _:OpReduce[_]         => NoArea
    case _:OpMemReduce[_,_]    => NoArea
    case _:UnrolledForeach     => NoArea
    case _:UnrolledReduce[_,_] => NoArea
    case _:Switch[_]           => NoArea
    case _:SwitchCase[_]       => NoArea

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
