package spatial.codegen.dotgen

import argon.codegen.dotgen.DotCodegen
import spatial.api.HostTransferExp
import spatial.SpatialConfig

trait DotGenHostTransfer extends DotCodegen  {
  val IR: HostTransferExp
  import IR._


  // override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
  //   case _ => super.emitNode(lhs, rhs)
  // }



}
