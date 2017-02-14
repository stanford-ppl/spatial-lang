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

  def annotateControlStyle(pipe: Exp[_], blks: Block[_]*) = (styleOf.get(pipe), hasControlNodes(blks:_*)) match {
    case (None, false)           => styleOf(pipe) = InnerPipe   // No annotations, no inner control nodes
    case (None, true)            => styleOf(pipe) = SeqPipe     // No annotations, has inner control nodes
    case (Some(InnerPipe), true) => styleOf(pipe) = MetaPipe    // Inner pipeline but has inner control nodes
    case _ =>                                                   // Otherwise preserve existing annotation
  }

  def annotateMemReduce(pipe: Exp[_]) = styleOf.get(pipe) match {
    case None            => styleOf(pipe) = MetaPipe            // MemReduce is MetaPipe by default
    case Some(InnerPipe) => styleOf(pipe) = MetaPipe            // MemReduce is always an outer controller
    case _ =>                                                   // Otherwise preserve existing annotation
  }

  override def visit(lhs: Sym[_], rhs: Op[_]) = rhs match {
    case Hwblock(blk)     => annotateControlStyle(lhs, blk)
    case UnitPipe(_,blk)  => annotateControlStyle(lhs, blk)
    case e: OpForeach     => annotateControlStyle(lhs, e.func)
    case e: OpReduce[_]   =>
      annotateControlStyle(lhs, e.blocks:_*)
      if (hasControlNodes(e.reduce)) new ControlInReductionError(ctxOrHere(lhs))

    case e: OpMemReduce[_,_] =>
      annotateMemReduce(lhs)
      if (hasControlNodes(e.reduce)) new ControlInReductionError(ctxOrHere(lhs))

    case e: CoarseBurst[_,_] => styleOf(lhs) = InnerPipe
    case e: BurstLoad[_]  => styleOf(lhs) = InnerPipe
    case e: BurstStore[_] => styleOf(lhs) = InnerPipe
    case e: Scatter[_]    => styleOf(lhs) = InnerPipe
    case e: Gather[_]     => styleOf(lhs) = InnerPipe
    case _ => super.visit(lhs, rhs)
  }
}
