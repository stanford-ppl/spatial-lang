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

  private def visitOuterControl(lhs: Sym[_], rhs: Op[_]): Unit = {
    dbgs(str(lhs))
    rhs.blocks.foreach{blk => visitBlock(blk) }
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
    if (isInnerControl(lhs)) visitInnerControl(lhs,rhs) else visitOuterControl(lhs,rhs)
  }

  override protected def visit(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case _:Hwblock => inHw { visitControl(lhs,rhs) }

    case StateMachine(_,_,notDone,action,nextState,_) if isOuterControl(lhs) =>
      dbgs(str(lhs))
      rhs.blocks.foreach{blk => visitBlock(blk) }
      val (latNotDone, iiNotDone) = latencyAndInterval(notDone)
      val (latNextState, iiNextState) = latencyAndInterval(nextState)
      val interval = (Seq(1, iiNotDone.toInt, iiNextState.toInt) ++ childrenOf(lhs).map{child => iiOf(child) }).max
      dbgs(s" - Interval: $interval")
      iiOf(lhs) = interval
      bodyLatency(lhs) = Seq(latNotDone, latNextState)

    case _ if isControlNode(lhs) => visitControl(lhs, rhs)
    case _ => super.visit(lhs, rhs)
  }

}