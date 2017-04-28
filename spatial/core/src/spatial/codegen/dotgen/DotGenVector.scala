package spatial.codegen.dotgen

import argon.codegen.dotgen.DotCodegen
import spatial.SpatialExp

trait DotGenVector extends DotCodegen {
  val IR: SpatialExp
  import IR._

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case ListVector(elems)      => 
    case VectorApply(vector, i) =>  //if (Config.dotDetail > 0) emitEdge(vector, lhs)
    case VectorSlice(vector, start, end) => 
    case _ => super.emitNode(lhs, rhs)
  }
}
