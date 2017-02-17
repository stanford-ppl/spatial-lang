package spatial.codegen.pirgen

import argon.codegen.pirgen.PIRCodegen
import spatial.api.UnrolledExp
import spatial.SpatialConfig
import spatial.SpatialExp


trait PIRGenUnrolled extends PIRCodegen {
  val IR: SpatialExp with PIRCommonExp
  import IR._

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = {
    rhs match {
      case UnrolledForeach(en,cchain,func,iters,valids) =>
        emitBlock(func)

      case UnrolledReduce(en,cchain,accum,func,_,iters,valids,rV) =>
        emitBlock(func)

      //case ParSRAMLoad(sram,inds) =>
      //case ParSRAMStore(sram,inds,data,ens) =>
      //case ParFIFODeq(fifo, ens, z) =>
      //case ParFIFOEnq(fifo, data, ens) =>

      case _ => super.emitNode(lhs, rhs)
    }
  }
}
