package spatial.analysis

import org.virtualized.SourceContext

/**
  * Basic control style annotation checking / fixing.
  * Also includes other high level sanity checks.
  *
  * Current sanity checks:
  *   1. Control nodes are not allowed within reduction functions
  */
trait PipeLevelAnalyzer extends SpatialTraversal {
  import IR._

  override val name = "Pipe Level Analyzer"
  override val recurse = Always


  def annotateControl(pipe: Exp[_], blks: Block[_]*) = {
    val isOuter = hasControlNodes(blks:_*)

    (styleOf.get(pipe), isOuter) match {
      case (None, false)           => styleOf(pipe) = InnerPipe   // No annotations, no inner control nodes
      case (None, true)            => styleOf(pipe) = SeqPipe     // No annotations, has inner control nodes
      case (Some(InnerPipe), true) => styleOf(pipe) = MetaPipe    // Inner pipeline but has inner control nodes
      case _ =>                                                   // Otherwise preserve existing annotation
    }

    levelOf(pipe) = if (isOuter) OuterControl else InnerControl
  }

  def annotateLeafControl(pipe: Exp[_]): Unit = {
    styleOf(pipe) = InnerPipe
    levelOf(pipe) = InnerControl
  }

  override def visit(lhs: Sym[_], rhs: Op[_]) = rhs match {
    case Hwblock(blk,_)   => annotateControl(lhs, blk)
    case UnitPipe(_,blk)  => annotateControl(lhs, blk)

    case e: OpForeach     => annotateControl(lhs, e.func)

    case e: OpReduce[_]   =>
      annotateControl(lhs, e.blocks:_*)
      if (hasControlNodes(e.reduce)) new ControlInReductionError(ctxOrHere(lhs))

    case e: OpMemReduce[_,_] =>
      styleOf.get(lhs) match {
        case None            => styleOf(lhs) = MetaPipe            // MemReduce is MetaPipe by default
        case Some(InnerPipe) => styleOf(lhs) = MetaPipe            // MemReduce is always an outer controller
        case _ =>                                                  // Otherwise preserve existing annotation
      }
      levelOf(lhs) = OuterControl

      if (hasControlNodes(e.reduce)) new ControlInReductionError(ctxOrHere(lhs))

    case StateMachine(_,_,notDone,action,nextState,_) =>
      annotateControl(lhs, action)
      if (hasControlNodes(notDone)) new ControlInNotDoneError(ctxOrHere(lhs))
      if (hasControlNodes(nextState)) new ControlInNextStateError(ctxOrHere(lhs))

    case e: DenseTransfer[_,_] => annotateLeafControl(lhs)
    case e: SparseTransfer[_]  => annotateLeafControl(lhs)
    case _ => super.visit(lhs, rhs)
  }
}
