package spatial.codegen.dotgen

import argon.codegen.dotgen.DotCodegen
import argon.Config
import spatial.SpatialExp

trait DotGenFIFO extends DotCodegen with DotGenReg {
  val IR: SpatialExp
  import IR._

  override def attr(n:Exp[_]) = n match {
    case n if isFIFO(n) => super.attr(n).shape(box).style(filled).color(gold)
    case n => super.attr(n)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case op@FIFONew(size)   => emitVert(lhs)
    case FIFOEnq(fifo,v,en) => 
      emitMemWrite(lhs)
      if (Config.dotDetail > 0) {emitEn(en, lhs)}
    case FIFODeq(fifo,en)   => 
      emitMemRead(lhs)
      if (Config.dotDetail > 0) {emitEn(en, lhs)}

    case _ => super.emitNode(lhs, rhs)
  }
}
