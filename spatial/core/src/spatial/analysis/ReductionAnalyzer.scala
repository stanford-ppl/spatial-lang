package spatial.analysis

import argon.core._
import argon.nodes._
import spatial.aliases._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._

trait ReductionAnalyzer extends SpatialTraversal {
  override val name = "Reduction Analyzer"
  override val recurse = Always

  private def identifyReduceFunc(rFunc: Block[_], a: Exp[_], b: Exp[_]) = rFunc.result match {
    case Def(FixAdd(`a`,`b`)) => FixPtSum
    case Def(FltAdd(`a`,`b`)) => FltPtSum
    case Def(Min(`a`,`b`)) if isFixPtType(a.tp) => FixPtMin
    case Def(Max(`a`,`b`)) if isFixPtType(a.tp) => FixPtMax
    case _ =>
      warn(u"$rFunc on $a and $b does not match any reduce type!")
      OtherReduction
  }

  override protected def visit(lhs: Sym[_], rhs: Op[_]) = rhs match {
    case e: OpReduce[_] =>
      val redType = identifyReduceFunc(e.reduce, e.rV._1, e.rV._2)
      getStages(e.reduce).foreach{sym => reduceType(sym) = redType }
      getStages(e.load).foreach{sym => reduceType(sym) = redType }
      getStages(e.store).foreach{sym => reduceType(sym) = redType }
      reduceType(e.accum) = redType
      reduceType(lhs) = redType

      dbg(c"pipefold acc ${e.accum} to $redType in $lhs")

    case e: OpMemReduce[_,_] =>
      val redType = identifyReduceFunc(e.reduce, e.rV._1, e.rV._2)
      getStages(e.reduce).foreach{sym => reduceType(sym) = redType }
      getStages(e.loadAcc).foreach{sym => reduceType(sym) = redType }
      getStages(e.loadRes).foreach{sym => reduceType(sym) = redType }
      getStages(e.storeAcc).foreach{sym => reduceType(sym) = redType }
      reduceType(e.accum) = redType
      reduceType(lhs) = redType

      dbg(c"accumfold acc ${e.accum} to $redType in $lhs")

    case _ => super.visit(lhs, rhs)
  }
}
