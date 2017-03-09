package spatial.codegen.dotgen

import argon.codegen.dotgen.DotCodegen
import spatial.api.FIFOExp
import spatial.SpatialConfig
import spatial.SpatialExp

trait DotGenFIFO extends DotCodegen {
  val IR: SpatialExp
  import IR._

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case op@FIFONew(size)   => 
    case FIFOEnq(fifo,v,en) => 
    case FIFODeq(fifo,en,z) => 

    case _ => super.emitNode(lhs, rhs)
  }
}
