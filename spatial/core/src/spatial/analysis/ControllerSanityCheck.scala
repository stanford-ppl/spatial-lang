package spatial.analysis

import argon.core._
import spatial.aliases._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._

trait ControllerSanityCheck extends SpatialTraversal {
  override val name = "Control Sanity Check"
  override val recurse = Default

  private def checkIters(lhs: Exp[_], iters: Seq[Exp[_]]) = iters.foreach{i =>
    if (isGlobal(i)) bug(lhs.ctx, c"Iterator $i of loop $lhs is global?")
  }

  override def visit(lhs: Sym[_], rhs: Op[_]) = rhs match {
    case _ if isControlNode(lhs) =>
      rhs match {
        case e: UnrolledForeach     => checkIters(lhs, e.iters.flatten)
        case e: UnrolledReduce[_,_] => checkIters(lhs, e.iters.flatten)
        case _ =>
      }

      // if (rhs match {case e: ParallelPipe => false; case _ => true}) { // Skip Parallels since they likely depend on their children
      //   depsOf(lhs).filter {e => isControlNode(e)}.map{e => 
      //     val lca = leastCommonAncestorWithPaths[Exp[_]](lhs, e, {node => parentOf(node)})._1.get
      //     dbg(c"Checking lca of $lhs ${lhs.ctx} and $e ${e.ctx} = $lca")
      //     if (styleOf(lca) == ForkJoin) {throw new Exception(s"Trying to schedule nodes with dependencies in parallel with each other: ${e} (${e.ctx}) and ${lhs} (${lhs.ctx}).")}
      //   }
      // }

      val blocks = rhs.blocks
      blocks.foreach{block =>
        val primitives = getPrimitiveNodes(block)
        val controllers = getControlNodes(block).filterNot(isPrimitiveControl)

        if (primitives.nonEmpty && controllers.nonEmpty && config.verbosity <= 2) {
          bug(lhs.ctx, c"The contents of block ${str(lhs)} appear to have been incorrectly code motioned (see log file #${state.paddedPass(state.pass-1)}.")
          dbg(c"${str(lhs)}")
          dbg("  Primitives:")
          primitives.foreach{p => dbg(c"    ${str(p)}") }
          dbg(c"  Controllers:")
          controllers.foreach{p => dbg(c"    ${str(p)}") }
          dbg("\n\n\n")
        }
        else if (primitives.nonEmpty && controllers.nonEmpty && config.verbosity >= 3) {
          bug(lhs.ctx, c"The contents of block ${str(lhs)} appear to have been incorrectly code motioned (see log file #${state.paddedPass(state.pass-1)}.")
          dbg(c"${str(lhs)}")
          dbg("  Primitives:")
          primitives.foreach{p => dbg(c"    ${str(p)}") }
          dbg(c"  Controllers:")
          controllers.foreach{p => dbg(c"    ${str(p)}") }
          dbg("\n\n\n")
        }
      }
      super.visit(lhs,rhs)

    case _ => super.visit(lhs, rhs)
  }

}
