package spatial.codegen.dotgen

import argon.core._
import argon.codegen.dotgen.DotCodegen
import spatial.nodes._

trait DotGenBits extends DotCodegen {

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case BitsAsData(_,_) =>
    case DataAsBits(_) =>
    case _ => super.emitNode(lhs, rhs)
  }
}
