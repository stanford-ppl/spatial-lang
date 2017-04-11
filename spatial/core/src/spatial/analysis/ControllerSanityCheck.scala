package spatial.analysis

import argon.{State,Config}

trait ControllerSanityCheck extends SpatialTraversal {
  import IR._

  override val name = "Control Sanity Check"
  override val recurse = Default
  override def shouldRun = Config.verbosity >= 2

  override def visit(lhs: Sym[_], rhs: Op[_]) = {
    if (isControlNode(lhs)) {
      val blocks = rhs.blocks
      blocks.foreach{block =>
        val primitives = getPrimitiveNodes(block)
        val controllers = getControlNodes(block)

        if (primitives.nonEmpty && controllers.nonEmpty) {
          warn(lhs.ctx, c"[Compiler] The contents of block ${str(lhs)} appear to have been incorrectly CSEd (see log file #${State.paddedPass}.")
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
