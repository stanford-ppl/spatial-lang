package spatial.codegen.dotgen

import argon.codegen.dotgen._
import argon.core._
import spatial.aliases._
import spatial.nodes._
import spatial.utils._

trait DotGenUnrolled extends DotCodegen with DotGenReg {

  def emitValids(valids: Seq[Seq[Bound[Bit]]]) {
    valids.foreach{ v =>
      v.foreach{vv =>
        emitVert(vv)
      }
    }
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = {
    if (config.dotDetail == 0) rhs match {
      case _ if isControlNode(lhs) => rhs match {
        case Hwblock(_,_) => super.emitNode(lhs,rhs)
        case _ =>
          emitSubGraph(lhs, DotAttr().label(quote(lhs)).style(rounded)){
            emitVert(lhs);
            rhs.blocks.foreach(emitBlock)
          }
      }
      case UnrolledForeach(en,cchain,func,iters,valids) =>

      case UnrolledReduce(en,cchain,accum,func,iters,valids) =>

      case ParSRAMLoad(sram, inds, ens) => emitMemRead(lhs)

      case ParSRAMStore(sram,inds,data,ens) => emitMemWrite(lhs)

      case ParFIFODeq(fifo, ens) => emitMemRead(lhs)

      case ParFIFOEnq(fifo, data, ens) => emitMemWrite(lhs)

      case ParStreamRead(strm, ens) => emitMemRead(lhs)

      case ParStreamWrite(strm, data, ens) => emitMemWrite(lhs)

      case _ => super.emitNode(lhs, rhs)
    }    
    else rhs match {
      case UnrolledForeach(en,cchain,func,iters,valids) =>
        emitValids(valids)
        emitSubGraph(lhs, DotAttr().label(quote(lhs)).style(rounded)){ 
          emitVert(lhs);
          iters.flatten.foreach{i => emitVert(i); emitEdge(cchain, i)}
          rhs.blocks.foreach(emitBlock) 
        }

      case UnrolledReduce(en,cchain,accum,func,iters,valids) =>
        emitValids(valids)
        emitSubGraph(lhs, DotAttr().label(quote(lhs)).style(rounded)){ 
          emitVert(lhs);
          iters.flatten.foreach{i => emitVert(i); emitEdge(cchain, i)}
          rhs.blocks.foreach(emitBlock) 
        }

      case ParSRAMLoad(sram, inds, ens) => 
        emitVert(lhs)
        emitEdge(sram, lhs)
        inds.foreach{ ind => ind.foreach{ a => emitEdge(a, sram) }}
        ens.foreach{ a => emitEn(a,lhs) }

      case ParSRAMStore(sram,inds,data,ens) => 
        data.foreach{ a => emitVert(a); emitEdge(a, sram)}
        ens.foreach{ a => emitVert(a); emitEn(a, sram)}
        inds.foreach{ ind => ind.foreach{ a => emitEdge(a, sram) }}


      case ParFIFODeq(fifo, ens) => 
        emitVert(lhs)
        emitEdge(fifo, lhs)
        ens.foreach{ a => emitEn(a,lhs) }

      case ParFIFOEnq(fifo, data, ens) => emitMemWrite(lhs)
        data.foreach{ a => emitVert(a); emitEdge(a, fifo)}
        ens.foreach{ a => emitVert(a); emitEn(a, lhs)}


      case ParStreamRead(strm, ens) => emitVert(lhs); emitEdge(strm, lhs); ens.foreach{emitEn(_,strm)}

      case ParStreamWrite(strm, data, ens) => data.foreach{emitEdge(_, strm)}; ens.foreach{emitEn(_,strm)}

      case _ => super.emitNode(lhs, rhs)
    }
  }
}
