package spatial.codegen.dotgen

import argon.codegen.dotgen.DotCodegen
import spatial.SpatialExp

trait DotGenEmpty extends DotCodegen {
  val IR: SpatialExp
  import IR._

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = {
  }

}
