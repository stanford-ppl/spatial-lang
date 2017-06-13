package spatial.codegen.dotgen

import argon.codegen.dotgen.DotCodegen
import spatial.compiler._

trait DotGenEmpty extends DotCodegen {

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = {
  }

}
