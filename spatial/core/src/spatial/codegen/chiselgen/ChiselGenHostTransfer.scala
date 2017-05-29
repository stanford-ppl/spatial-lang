package spatial.codegen.chiselgen

import argon.codegen.chiselgen.ChiselCodegen
import spatial.lang.HostTransferExp
import spatial.{SpatialConfig, SpatialExp}

trait ChiselGenHostTransfer extends ChiselCodegen  {
  val IR: SpatialExp
  import IR._


  // Does not belong in chisel
  // override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
  //   case _ => super.emitNode(lhs, rhs)
  // }



}
