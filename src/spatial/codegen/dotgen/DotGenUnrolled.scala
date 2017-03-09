package spatial.codegen.dotgen

import argon.codegen.dotgen.DotCodegen
import spatial.api.UnrolledExp
import spatial.SpatialConfig
import spatial.SpatialExp


trait DotGenUnrolled extends DotCodegen with DotGenController {
  val IR: SpatialExp
  import IR._

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case UnrolledForeach(en,cchain,func,iters,valids) =>

    case UnrolledReduce(en,cchain,accum,func,_,iters,valids,rV) =>

    case ParSRAMLoad(sram,inds) =>

    case ParSRAMStore(sram,inds,data,ens) =>

    case ParFIFODeq(fifo, ens, z) =>

    case ParFIFOEnq(fifo, data, ens) =>

    case _ => super.emitNode(lhs, rhs)
  }
}
