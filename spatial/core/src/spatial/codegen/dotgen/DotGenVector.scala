package spatial.codegen.dotgen

import argon.codegen.dotgen.DotCodegen
import spatial.SpatialExp
import argon.Config


trait DotGenVector extends DotCodegen {
  val IR: SpatialExp
  import IR._

  override def attr(n:Exp[_]) = n match {
    case lhs: Sym[_] => lhs match {
      case Def(VectorApply(vector, i)) => super.attr(n).label(src"""$i""")
      case _ => super.attr(n)
    }
    case _ => super.attr(n)
  }


  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case ListVector(elems)      => 
    case VectorApply(vector, i) =>  if (Config.dotDetail > 0) {emitVert(lhs);emitEdge(vector, lhs)}
    case VectorSlice(vector, start, end) => 
    case _ => super.emitNode(lhs, rhs)
  }
}
