package spatial.codegen.pirgen

import argon.core._
import spatial.metadata._

import scala.collection.mutable

class PIRAreaModelHack(implicit val codegen:PIRCodegen) extends PIRTraversal {
  override val name = "PIR Area Model Hack"
  override val recurse = Always
  var IR = codegen.IR

  var totalArea = 0.0
  var totalMemArea = 0.0
  var largestMem = 0.0
  var nPMUs = 0

  override protected def postprocess[A:Type](b: Block[A]) = {
    report("Estimated ASIC area: " + totalArea)
    report("Estimated ASIC mem area: " + totalMemArea)
    report(s"PMUs: $nPMUs x $largestMem")
    super.postprocess(b)
  }

  override protected def visit(lhs: Sym[_], rhs: Op[_]) {
    mappingOf.get(lhs).foreach { _.foreach {
      case cu:CU =>
        dbgs(c"CU:  $cu (lanes = ${cu.lanes}")
        val stageArea = cu.allStages.map{stage => areaOf(stage, cu) }.sum
        val ccArea = cu.cchains.map(cchainArea).sum

        if (cu.isPMU) {
          nPMUs += 1
          val memArea = cu.mems.map(sramArea).sum
          totalArea += (stageArea + ccArea + memArea)
          totalMemArea += memArea
        }
        else if (cu.isPCU) {
          totalArea += (stageArea + ccArea)
        }
      case _ =>
    } }
  }

  def areaOf(stage: Stage, cu: CU): Double = stage match {
    case MapStage(op,_,_)      => (opArea(op) + regArea()) * cu.lanes
    case ReduceStage(op,_,_,_,_) => (getReduceCost(cu) + 1)*(opArea(op) + regArea())
  }

  def opArea(op: PIROp, verbose: Boolean = true): Double = {
    val area = (op match {
      case PIRALUMux  => 56.448002
      case PIRBypass  => 0.0
      case PIRFixAdd  => 937.036816
      case PIRFixSub  => 937.036816
      case PIRFixMul  => 3290.742026
      case PIRFixDiv  => 2629.947656
      case PIRFixMod  => 2899.134076
      case PIRFixLt   => 89.964002
      case PIRFixLeq  => 89.964002
      case PIRFixEql  => 68.796
      case PIRFixNeq  => 68.4432
      case PIRFixMin  => 139.532402
      case PIRFixMax  => 139.532402
      case PIRFixNeg  => 16.934401
      case PIRFltAdd  => 3810.57
      case PIRFltSub  => 3810.57
      case PIRFltMul  => 3971.568
      case PIRFltDiv  => 6402.371313
      case PIRFltLt   => 535.0101789
      case PIRFltLeq  => 535.0101789
      case PIRFltEql  => 535.0101789
      case PIRFltNeq  => 535.0101789
      case PIRFltExp  => 50000 //10*opArea(PIRFltMul) + 10*opArea(PIRFltAdd)
      case PIRFltLog  => 50000 //10*opArea(PIRFltMul) + 10*opArea(PIRFltAdd)
      case PIRFltSqrt => 50000 //10*opArea(PIRFltMul) + 10*opArea(PIRFltAdd)
      case PIRFltAbs  => opArea(PIRBitAnd)
      case PIRFltMin  => 535.0101789
      case PIRFltMax  => 535.0101789
      case PIRFltNeg  => opArea(PIRFixNeg)/32
      case PIRBitAnd  => 33.868801
      case PIRBitOr   => 33.868801
      case _ => warn(s"Don't know area of $op"); 0.0
    }) / 1000000.0
    if (verbose) dbgs(s"  $op: $area")
    area
  } // convert to mm^2

  def regArea(verbose: Boolean = true): Double = {
    val area = opArea(PIRFixAdd, false)
    if (verbose) dbgs(s"  reg: $area")
    area
  }
  def counterArea: Double = {
    val area = opArea(PIRFixAdd, false) + regArea(false)
    dbgs(c"  cchain: $area")
    area
  } // TODO


  def sramArea(sram: CUMemory): Double = {
    def cacti(bytes: Double): Double = {
      if (bytes < 1000)         { 0.00001117*bytes + 0.00111 }
      else if (bytes < 10000)   { 0.000007725*bytes + 0.011 }
      else if (bytes < 1000000) { 0.000007064*bytes + 0.133 }
      else { warn(s"Don't know how to size sram of size $bytes bytes"); 0.0 }
    }
    var buffers = 1.0
    try {
      buffers = duplicatesOf(sram.mem).head.depth.toDouble
    } catch { case _:Throwable => }

    val area = buffers * (if (sram.size > 8) {
      sram.banking match {
        case Some(Strided(stride, banks)) => 16 * cacti(4*sram.size.toDouble/16)
        case Some(Duplicated)      => 16 * cacti(4*sram.size.toDouble)
        case Some(NoBanks)         => cacti(4*sram.size.toDouble)
        case _                     => cacti(4*sram.size.toDouble)
      }
    }
    else {
      regArea(false) * sram.size
    })
    if (area > largestMem) largestMem = area

    dbgs(s"  sram (size: ${sram.size}, banking: ${sram.banking}, buffers: $buffers): $area")
    area
  }

  def cchainArea(cc: CUCChain): Double = cc match {
    case CChainInstance(_,ctrs) => counterArea * ctrs.length
    case CChainCopy(_,inst,_) => cchainArea(inst)
    case UnitCChain(_) => counterArea
  }



}
