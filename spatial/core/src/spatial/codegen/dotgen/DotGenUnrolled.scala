package spatial.codegen.dotgen

import argon.codegen.dotgen._
import argon.Config
import spatial.{SpatialConfig, SpatialExp}

trait DotGenUnrolled extends DotCodegen with DotGenReg {
  val IR: SpatialExp
  import IR._

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case _ if isControlNode(lhs) =>
      rhs match { 
        case Hwblock(_,_) => super.emitNode(lhs,rhs)
        case _ =>
          emitSubGraph(lhs, DotAttr().label(quote(lhs)).style(rounded)){ 
            if (Config.dotDetail == 0) emitVert(lhs);
            rhs.blocks.foreach(emitBlock) 
          }
      }

    //case UnrolledForeach(en,cchain,func,iters,valids) =>

    //case UnrolledReduce(en,cchain,accum,func,_,iters,valids,rV) =>

    case ParSRAMLoad(sram, inds, ens) => emitMemRead(lhs)

    case ParSRAMStore(sram,inds,data,ens) => emitMemWrite(lhs)

    case ParFIFODeq(fifo, ens) => emitMemRead(lhs)

    case ParFIFOEnq(fifo, data, ens) => emitMemWrite(lhs)

    case ParStreamRead(strm, ens) => emitMemRead(lhs)

    case ParStreamWrite(strm, data, ens) => emitMemWrite(lhs)

    case _ => super.emitNode(lhs, rhs)
  }
}
