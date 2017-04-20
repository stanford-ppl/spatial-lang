package spatial.codegen.dotgen

import argon.codegen.dotgen.DotCodegen
import spatial.SpatialExp

trait DotGenHostTransfer extends DotCodegen  {
  val IR: SpatialExp
  import IR._


  // override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
  //   case _ => super.emitNode(lhs, rhs)
  // }



}
