package spatial.codegen.dotgen

import argon.codegen.dotgen.DotCodegen
import argon.core._
import spatial.aliases._
import spatial.nodes._
import spatial.utils._

trait DotGenSRAM extends DotCodegen with DotGenReg {

  override def attr(n:Exp[_]) = n match {
    case n if isSRAM(n) => super.attr(n).shape(box).style(filled).color(cyan)
    case n => super.attr(n)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case SRAMNew(_) => emitVert(lhs)

    case BankedSRAMLoad(sram, bank, ofs, ens) =>
      if (config.dotDetail == 0) emitMemRead(lhs) else {
        emitVert(lhs)
        emitEdge(sram, lhs)
        bank.foreach{ind => ind.foreach{ a => emitEdge(a, sram) }}
        ofs.foreach{o => emitEdge(o, sram) }
        ens.foreach{a => emitEn(a,lhs) }
      }

    case BankedSRAMStore(sram,data,bank,ofs,ens) =>
      if (config.dotDetail == 0) emitMemWrite(lhs) else {
        data.foreach{ a => emitVert(a); emitEdge(a, sram)}
        ens.foreach{ a => emitVert(a); emitEn(a, sram)}
        bank.foreach{ind => ind.foreach{ a => emitEdge(a, sram) }}
        ofs.foreach{o => emitEdge(o,sram) }
      }

    case _ => super.emitNode(lhs, rhs)
  }

  override protected def emitFileFooter() {
    super.emitFileFooter()
  }
    
}
