package spatial.analysis

import argon.core._
import argon.nodes._
import spatial.aliases._
import spatial.metadata._
import spatial.models.LatencyModel
import spatial.nodes._
import spatial.utils._

case class InitiationAnalyzer(var IR: State, latencyModel: LatencyModel) extends ModelingTraversal {
  override val name = "InitiationAnalyzer"
  override val recurse: RecurseOpt = Default

  private def inHw(x: => Unit): Unit = { inHwScope = true; x; inHwScope = false }

  private def visitOuterContrl(lhs: Sym[_], rhs: Op[_]): Unit = {
    dbgs(str(lhs))
    super.visit(lhs, rhs)
    val interval = (1 +: childrenOf(lhs).map{child => iiOf(child) }).max
    dbgs(s" - Interval: $interval")
    iiOf(lhs) = interval
  }

  private def visitInnerControl(lhs: Sym[_], rhs: Op[_]): Unit = {
    dbgs(str(lhs))
    val blks = rhs.blocks.map{block => latencyAndInterval(block) }
    val latency = blks.map(_._1).sum
    val interval = (1L +: blks.map(_._2)).max
    dbgs(s" - Latency:  $latency")
    dbgs(s" - Interval: $interval")
    bodyLatency(lhs) = latency
    iiOf(lhs) = interval.toInt
  }

  private def visitControl(lhs: Sym[_], rhs: Op[_]): Unit = {
    if (isInnerControl(lhs)) visitInnerControl(lhs,rhs) else visitOuterContrl(lhs,rhs)
  }

  override protected def visit(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case _:Hwblock => inHw { visitControl(lhs,rhs) }

    case StateMachine(_,_,notDone,action,nextState,_) if isOuterControl(lhs) =>
      dbgs(str(lhs))
      val iiNotDone = latencyAndInterval(notDone)._2.toInt
      val iiNextState = latencyAndInterval(nextState)._2.toInt
      val interval = (Seq(1, iiNotDone, iiNextState) ++ childrenOf(lhs).map{child => iiOf(child) }).max
      dbgs(s" - Interval: $interval")
      iiOf(lhs) = interval

    case _ if isControlNode(lhs) => visitControl(lhs, rhs)
    case _ => super.visit(lhs, rhs)
  }

}