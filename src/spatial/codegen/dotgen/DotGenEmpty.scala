package spatial.codegen.dotgen

import argon.codegen.FileDependencies
import argon.codegen.dotgen.DotCodegen
import spatial.api.CounterExp
import spatial.SpatialConfig
import spatial.SpatialExp


trait DotGenEmpty extends DotCodegen {
  val IR: SpatialExp
  import IR._

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = {
  }

}
