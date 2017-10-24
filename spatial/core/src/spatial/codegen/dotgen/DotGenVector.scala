package spatial.codegen.dotgen

import argon.codegen.dotgen.DotCodegen
import argon.core._
import spatial.aliases._
import spatial.nodes._

trait DotGenVector extends DotCodegen {

  override def attr(n:Exp[_]) = n match {
    case lhs: Sym[_] => lhs match {
      case Def(VectorApply(vector, i)) => super.attr(n).label(src"""apply($i)""")
      case _ => super.attr(n)
    }
    case _ => super.attr(n)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case ListVector(elems)      => 
    case VectorApply(vector, i) =>  if (config.dotDetail > 0) {emitVert(lhs);emitEdge(vector, lhs)}
    case VectorSlice(vector, start, end) => 
    case _ => super.emitNode(lhs, rhs)
  }

}
