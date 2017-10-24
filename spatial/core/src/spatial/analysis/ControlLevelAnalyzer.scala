package spatial.analysis

import argon.core._
import spatial.aliases._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._
import spatial.SpatialConfig

/**
  * Basic control style annotation checking / fixing.
  * Also includes other high level sanity checks.
  *
  * Current sanity checks:
  *   1. Control nodes are not allowed within reduction functions
  *   2. Control nodes are not allowed within state machine termination or state transition functions
  */
trait ControlLevelAnalyzer extends SpatialTraversal {
  override val name = "Control Level Analyzer"
  override val recurse = Default

  def annotateControl(pipe: Exp[_], isOuter: Boolean) = {
    (styleOf.get(pipe), isOuter) match {
      case (None, false)           => styleOf(pipe) = InnerPipe   // No annotations, no inner control nodes
      case (None, true)            => styleOf(pipe) = MetaPipe    // No annotations, has inner control nodes
      case (Some(InnerPipe), true) => styleOf(pipe) = MetaPipe    // Inner pipeline but has inner control nodes
      case _ =>                                                   // Otherwise preserve existing annotation
    }

    levelOf(pipe) = if (isOuter) OuterControl else InnerControl
  }

  // Unit pipes are always sequential controllers (since they don't loop)
  def annotateUnit(pipe: Exp[_], isOuter: Boolean) = {
    styleOf.get(pipe) match {
      case Some(StreamPipe) => styleOf(pipe) = StreamPipe
      case _                => styleOf(pipe) = SeqPipe
    }
    levelOf(pipe) = if (isOuter) OuterControl else InnerControl
  }

  def annotateLeafControl(pipe: Exp[_]): Unit = {
    styleOf(pipe) = InnerPipe
    levelOf(pipe) = InnerControl
  }

  def markControlNodes(lhs: Sym[_], rhs: Def): Boolean = {
    // Recursively check scopes to see if there are any control nodes, starting at a Hwblock
    val containsControl = rhs.blocks.map{blk =>
      tab += 1
      val mapping = blk -> traverseStmsInBlock(blk, {stms =>
        stms.map{stm => markControlNodes(stm.lhs.head,stm.rhs) }.fold(false)(_||_)
      })
      tab -= 1
      mapping
    }.toMap

    val isOuter = containsControl.values.fold(false)(_||_)

    dbgs(c"$lhs = $rhs [$isOuter]")

    rhs match {
      case pipe:Hwblock   =>
        annotateUnit(lhs, isOuter)
        if (pipe.isForever) styleOf(lhs) = StreamPipe

      case _:UnitPipe  => annotateUnit(lhs, isOuter)
      case _:OpForeach => annotateControl(lhs, isOuter)
      case op:OpReduce[_] =>
        annotateControl(lhs, isOuter)
        if (containsControl(op.reduce)) new spatial.ControlInReductionError(lhs.ctx)
      case op:OpMemReduce[_,_] =>
        annotateControl(lhs, true)
        if (containsControl(op.reduce)) new spatial.ControlInReductionError(lhs.ctx)
      case op:StateMachine[_] =>
        annotateControl(lhs, isOuter)
        if (hasControlNodes(op.notDone))   new spatial.ControlInNotDoneError(lhs.ctx)
        if (hasControlNodes(op.nextState)) new spatial.ControlInNextStateError(lhs.ctx)

      case _: DenseTransfer[_,_] => annotateLeafControl(lhs)
      case _: SparseTransfer[_]  => annotateLeafControl(lhs)
      case _: SparseTransferMem[_,_,_] => annotateLeafControl(lhs)

      case _ =>
    }

    if (spatialConfig.enablePIR) {
      // Consider Switches/if-then-else to be outer controllers in PIR
      (isControlNode(lhs) && !isSwitchCase(lhs)) || isOuter || isSwitch(lhs) || isIfThenElse(lhs)
    }
    else {
      (isControlNode(lhs) && !isSwitch(lhs) && !isSwitchCase(lhs)) || isOuter
    }
  }

  override def visit(lhs: Sym[_], rhs: Op[_]) = rhs match {
    case Hwblock(blk,_) => markControlNodes(lhs, rhs)
    case _ => super.visit(lhs, rhs)
  }
}
