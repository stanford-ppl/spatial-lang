package spatial.codegen.dotgen

import argon.codegen.dotgen.DotCodegen
import spatial.api.VectorExp
import spatial.SpatialConfig

trait DotGenVector extends DotCodegen {
  val IR: VectorExp
  import IR._

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case ListVector(elems)      => 
    case VectorApply(vector, i) => 
    case VectorSlice(vector, start, end) => 
    case _ => super.emitNode(lhs, rhs)
  }
}
