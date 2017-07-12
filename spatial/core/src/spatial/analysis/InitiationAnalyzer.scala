package spatial.analysis

import argon.core._
import argon.nodes._
import spatial.aliases._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._

trait InitiationAnalyzer extends ModelingTraversal {
  override val name = "InitiationAnalyzer"
  override val recurse: RecurseOpt = Default

  def initiationInterval(block: Block[_]): Int = {
    val result = exps(block)
    val scope = blockNestedContents(block).flatMap(_.lhs)
      .filterNot(s => isGlobal(s))
      .filter{e => e.tp == UnitType || Bits.unapply(e.tp).isDefined }
      .map(_.asInstanceOf[Exp[_]]).toSet

    val (_, initInterval) = pipeLatencies(result, scope)

    // HACK: Set initiation interval to 1 if it contains a specialized reduction
    // This is a workaround for chisel codegen currently specializing and optimizing certain reduction types
    if (scope.exists(x => reduceType(x).contains(FixPtSum))) {
      1
    }
    else initInterval.toInt
  }

  override protected def visit(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case _ if isInnerControl(lhs) =>
      dbgs(str(lhs))
      val iis = rhs.blocks.map{block => initiationInterval(block) }
      val interval = (1 +: iis).max
      dbgs(s" - Interval: $interval")
      iiOf(lhs) = interval

    case StateMachine(_,_,notDone,action,nextState,_) if isOuterControl(lhs) =>
      dbgs(str(lhs))
      val iiNotDone = initiationInterval(notDone)
      val iiNextState = initiationInterval(nextState)
      val interval = (Seq(1, iiNotDone, iiNextState) ++ childrenOf(lhs).map{child => iiOf(child) }).max
      dbgs(s" - Interval: $interval")
      iiOf(lhs) = interval

    case _ if isOuterControl(lhs) =>
      rhs match { case Hwblock(_,_) => inHwScope = true; case _ => }

      dbgs(str(lhs))
      super.visit(lhs, rhs)
      val interval = (1 +: childrenOf(lhs).map{child => iiOf(child) }).max
      dbgs(s" - Interval: $interval")
      iiOf(lhs) = interval

      rhs match { case Hwblock(_,_) => inHwScope = false; case _ => }

    case _ => super.visit(lhs, rhs)
  }

}