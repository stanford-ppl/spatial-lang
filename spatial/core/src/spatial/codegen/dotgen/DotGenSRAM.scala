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
    case op@SRAMNew(dimensions) => emitVert(lhs)
    case SRAMLoad(sram, dims, is, ofs, en)     => emitMemRead(lhs)
    case SRAMStore(sram, dims, is, ofs, v, en) => emitMemWrite(lhs)
    case _ => super.emitNode(lhs, rhs)
  }

  override protected def emitFileFooter() {
    super.emitFileFooter()
  }
    
}
