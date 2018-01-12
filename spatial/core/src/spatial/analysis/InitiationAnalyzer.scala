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
    val interval = (1.0 +: childrenOf(lhs).map{child => iiOf(child) }).max
    dbgs(s" - Interval: $interval")
    iiOf(lhs) = userIIOf(lhs).getOrElse(interval)
  }

  private def visitInnerControl(lhs: Sym[_], rhs: Op[_]): Unit = {
    dbgs(str(lhs))
    val blks = rhs.blocks.map{block => latencyAndInterval(block) }
    val latency = blks.map(_._1).sum
    val interval = (1.0 +: blks.map(_._2)).max
    dbgs(s" - Latency:  $latency")
    dbgs(s" - Interval: $interval")
    bodyLatency(lhs) = latency
    iiOf(lhs) = userIIOf(lhs).getOrElse(interval)
  }

  private def visitControl(lhs: Sym[_], rhs: Op[_]): Unit = {
    if (isInnerControl(lhs)) visitInnerControl(lhs,rhs) else visitOuterControl(lhs,rhs)
  }

  override protected def visit(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case _:Hwblock => inHw { visitControl(lhs,rhs) }

    // TODO: Still need to verify that this rule is generally correct
    case StateMachine(_,_,notDone,action,nextState,_) if isInnerControl(lhs) =>
      dbgs(str(lhs))
      rhs.blocks.foreach{blk => visitBlock(blk) }
      val (latNotDone, iiNotDone) = latencyAndInterval(notDone)
      val (latNextState, iiNextState) = latencyAndInterval(nextState)
      val (actionLats, actionCycles) = latenciesAndCycles(action)

      val actionWritten = blockNestedContents(action).flatMap(_.lhs).flatMap{case LocalWriter(writers) => writers.map(_.mem); case _ => Nil }.toSet
      val nextStateRead = blockNestedContents(nextState).flatMap(_.lhs).flatMap{case LocalReader(readers) => readers.map(_.mem); case _ => Nil }.toSet
      val dependencies  = nextStateRead intersect actionWritten

      val writeLatency = if (!spatialConfig.enableRetiming || latencyModel.requiresRegisters(nextState.result, inReduce = true)) 1.0 else 0.0

      val actionLatency = latNextState + writeLatency + (1.0 +: actionLats.values.toSeq).max

      dbgs("Written memories: " + actionWritten.mkString(", "))
      dbgs("Read memories: " + nextStateRead.mkString(", "))
      dbgs("Intersection: " + dependencies.mkString(", "))

      val latency = latNotDone + actionLatency  // Accounts for hidden register write to state register

      val rawInterval = (Seq(latNextState+1, iiNotDone, iiNextState) ++ actionCycles.map(_.length)).max

      val interval = if (dependencies.nonEmpty) Math.max(rawInterval, actionLatency) else rawInterval

      dbgs(s" - Latency:  $latency")
      dbgs(s" - Interval: $interval")
      iiOf(lhs) = userIIOf(lhs).getOrElse(interval)
      bodyLatency(lhs) = latency

    case StateMachine(_,_,notDone,action,nextState,_) if isOuterControl(lhs) =>
      dbgs(str(lhs))
      rhs.blocks.foreach{blk => visitBlock(blk) }
      val (latNotDone, iiNotDone) = latencyAndInterval(notDone)
      val (latNextState, iiNextState) = latencyAndInterval(nextState)
      val interval = (Seq(1.0, iiNotDone, iiNextState) ++ childrenOf(lhs).map{child => iiOf(child) }).max
      dbgs(s" - Latency: $latNotDone, $latNextState")
      dbgs(s" - Interval: $interval")
      iiOf(lhs) = userIIOf(lhs).getOrElse(interval)
      bodyLatency(lhs) = Seq(latNotDone, latNextState)

    case _ if isControlNode(lhs) => visitControl(lhs, rhs)
    case _ => super.visit(lhs, rhs)
  }

}