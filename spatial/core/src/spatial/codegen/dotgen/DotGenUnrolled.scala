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

      case op: BankedSRAMLoad[_]   => emitMemRead(lhs)
      case op: BankedSRAMStore[_]  => emitMemWrite(lhs)
      case op: BankedFIFODeq[_]    => emitMemRead(lhs)
      case op: BankedFIFOEnq[_]    => emitMemWrite(lhs)
      case op: BankedStreamRead[_] => emitMemRead(lhs)
      case op: BankedStreamWrite[_] => emitMemWrite(lhs)

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

      case BankedSRAMLoad(sram, bank, ofs, ens) =>
        emitVert(lhs)
        emitEdge(sram, lhs)
        bank.foreach{ind => ind.foreach{ a => emitEdge(a, sram) }}
        ofs.foreach{o => emitEdge(o, sram) }
        ens.foreach{a => emitEn(a,lhs) }

      case BankedSRAMStore(sram,data,bank,ofs,ens) =>
        data.foreach{ a => emitVert(a); emitEdge(a, sram)}
        ens.foreach{ a => emitVert(a); emitEn(a, sram)}
        bank.foreach{ind => ind.foreach{ a => emitEdge(a, sram) }}
        ofs.foreach{o => emitEdge(o,sram) }


      case BankedFIFODeq(fifo, ens) =>
        emitVert(lhs)
        emitEdge(fifo, lhs)
        ens.foreach{ a => emitEn(a,lhs) }

      case BankedFIFOEnq(fifo, data, ens) => emitMemWrite(lhs)
        data.foreach{a => emitVert(a); emitEdge(a, fifo)}
        ens.foreach{a => emitVert(a); emitEn(a, lhs)}


      case BankedStreamRead(strm, ens) => emitVert(lhs); emitEdge(strm, lhs); ens.foreach{emitEn(_,strm)}
      case BankedStreamWrite(strm, data, ens) => data.foreach{emitEdge(_, strm)}; ens.foreach{emitEn(_,strm)}

      case _ => super.emitNode(lhs, rhs)
    }
  }
}
