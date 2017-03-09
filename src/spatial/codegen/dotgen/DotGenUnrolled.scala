package spatial.codegen.dotgen

import argon.codegen.dotgen._
import spatial.api.UnrolledExp
import spatial.SpatialConfig
import spatial.SpatialExp


trait DotGenUnrolled extends DotCodegen with DotGenReg {
  val IR: SpatialExp
  import IR._

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case rhs if isControlNode(lhs) =>
      emitSubGraph(lhs, DotAttr().label(quote(lhs)).style(rounded)){ emitVert(lhs); rhs.blocks.foreach(emitBlock) }

    //case UnrolledForeach(en,cchain,func,iters,valids) =>

    //case UnrolledReduce(en,cchain,accum,func,_,iters,valids,rV) =>

    case ParSRAMLoad(sram,inds) => emitMemRead(lhs)

    case ParSRAMStore(sram,inds,data,ens) => emitMemWrite(lhs)

    case ParFIFODeq(fifo, ens, z) => emitMemRead(lhs)

    case ParFIFOEnq(fifo, data, ens) => emitMemWrite(lhs)

    case _ => super.emitNode(lhs, rhs)
  }
}
