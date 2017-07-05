package spatial.codegen.dotgen

import argon.codegen.dotgen.DotCodegen
import argon.core._
import spatial.aliases._
import spatial.nodes._

trait DotGenHostTransfer extends DotCodegen  {

  // override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
  //   case _ => super.emitNode(lhs, rhs)
  // }

}
