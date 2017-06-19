package spatial.codegen.dotgen

import argon.codegen.dotgen.DotCodegen
import argon.core._
import spatial.aliases._

trait DotGenEmpty extends DotCodegen {

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = {
  }

}
