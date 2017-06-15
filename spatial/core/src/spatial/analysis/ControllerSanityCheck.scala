package spatial.analysis

import argon.internals._
import argon.core.Config
import spatial.compiler._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._

trait ControllerSanityCheck extends SpatialTraversal {
  override val name = "Control Sanity Check"
  override val recurse = Default

  override def visit(lhs: Sym[_], rhs: Op[_]) = {
    if (isControlNode(lhs)) {
      val blocks = rhs.blocks
      blocks.foreach{block =>
        val primitives = getPrimitiveNodes(block)
        val controllers = getControlNodes(block).filterNot(isPrimitiveControl)

        if (primitives.nonEmpty && controllers.nonEmpty && Config.verbosity <= 2) {
          warn(lhs.ctx, c"[Compiler] The contents of block ${str(lhs)} appear to have been incorrectly CSEd (see log file #${state.paddedPass(state.pass-1)}.")
          dbg(c"${str(lhs)}")
          dbg("  Primitives:")
          primitives.foreach{p => dbg(c"    ${str(p)}") }
          dbg(c"  Controllers:")
          controllers.foreach{p => dbg(c"    ${str(p)}") }
          dbg("\n\n\n")
        }
        else if (primitives.nonEmpty && controllers.nonEmpty && Config.verbosity >= 3) {
          error(lhs.ctx, c"[Compiler] The contents of block ${str(lhs)} appear to have been incorrectly CSEd (see log file #${state.paddedPass(state.pass-1)}.")
          dbg(c"${str(lhs)}")
          dbg("  Primitives:")
          primitives.foreach{p => dbg(c"    ${str(p)}") }
          dbg(c"  Controllers:")
          controllers.foreach{p => dbg(c"    ${str(p)}") }
          dbg("\n\n\n")
        }
      }
    }
    super.visit(lhs, rhs)
  }

}
