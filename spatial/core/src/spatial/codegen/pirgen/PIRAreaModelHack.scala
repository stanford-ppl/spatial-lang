package spatial.codegen.pirgen

import scala.collection.mutable

trait PIRAreaModelHack extends PIRTraversal {
  import IR._

  override val name = "PIR Area Model Hack"
  override val recurse = Always

  val mappingIn  = mutable.HashMap[Expr, List[CU]]()
  var totalArea = 0.0

  override protected def visit(lhs: Sym[_], rhs: Op[_]) {
    if (mappingIn.contains(lhs)) {
      mappingIn(lhs).foreach{cu =>
        val stageArea = cu.allStages.map{stage => areaOf(stage, cu) }.sum
        val ccArea = cu.cchains.map(cchainArea).sum

        if (cu.isPMU) {
          val memArea = cu.mems.map(sramArea).sum
          totalArea += (stageArea + ccArea + memArea)
        }
        else if (cu.isPCU) {
          totalArea += (stageArea + ccArea)
        }
      }
    }
  }

  def areaOf(stage: Stage, cu: CU): Double = stage match {
    case MapStage(op,_,_)      => (opArea(op) + regArea) * cu.lanes
    case ReduceStage(op,_,_,_) => (REDUCE_STAGES + 1)*(opArea(op) + regArea)
  }

  def opArea(op: PIROp): Double = (op match {
    //case PIRALUMux  =>
    case PIRBypass  => 0.0
    case PIRFixAdd  => 937.036816
    case PIRFixSub  => 937.036816
    case PIRFixMul  => 3290.742026
    case PIRFixDiv  => 23728.26997
    //case PIRFixMod  =>
    case PIRFixLt   => 528.318005
    case PIRFixLeq  => 528.318005
    //case PIRFixEql  =>
    //case PIRFixNeq  =>
    //case PIRFixMin  =>
    //case PIRFixMax  =>
    //case PIRFixNeg  =>
    //case PIRFltAdd  =>
    //case PIRFltSub  =>
    //case PIRFltMul  =>
    //case PIRFltDiv  =>
    //case PIRFltLt   =>
    //case PIRFltLeq  =>
    //case PIRFltEql  =>
    //case PIRFltNeq  =>
    //case PIRFltExp  =>
    //case PIRFltLog  =>
    //case PIRFltSqrt =>
    case PIRFltAbs  => opArea(PIRBitAnd)
    //case PIRFltMin  =>
    //case PIRFltMax  =>
    case PIRFltNeg  => opArea(PIRBitAnd)
    //case PIRBitAnd  =>
    //case PIRBitOr   =>
    case _ => warn(s"Don't know area of $op"); 0.0
  }) / 1000000.0 // convert to mm^2

  def regArea: Double = opArea(PIRFixAdd) // TODO
  def counterArea: Double = opArea(PIRFixAdd) + regArea // TODO


  def sramArea(sram: CUMemory): Double = sram.banking match {
    case Some(Strided(stride)) => 16 * (0.000007725*(sram.size.toDouble/16) + 0.011)
    case Some(Duplicated)      => 16 * (0.000007725*sram.size + 0.011)
    case Some(NoBanks)         => 0.000007725*sram.size + 0.011
    case _ => 0.000007725*sram.size.toDouble + 0.011
  }

  def cchainArea(cc: CUCChain): Double = cc match {
    case CChainInstance(_,ctrs) => counterArea * ctrs.length
    case CChainCopy(_,inst,_) => cchainArea(inst)
    case UnitCChain(_) => counterArea
  }



}
