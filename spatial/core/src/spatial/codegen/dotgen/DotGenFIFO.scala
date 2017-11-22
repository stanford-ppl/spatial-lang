package spatial.codegen.dotgen

import argon.codegen.dotgen.DotCodegen
import argon.core._
import spatial.aliases._
import spatial.nodes._
import spatial.utils._

trait DotGenFIFO extends DotCodegen with DotGenReg {

  override def attr(n:Exp[_]) = n match {
    case n if isFIFO(n) => super.attr(n).shape(box).style(filled).color(gold)
    case n => super.attr(n)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case FIFONew(_)   => emitVert(lhs)

    case BankedFIFODeq(fifo, ens) =>
      if (config.dotDetail == 0) emitMemRead(lhs) else {
        emitVert(lhs)
        emitEdge(fifo, lhs)
        ens.foreach{ a => emitEn(a,lhs) }
      }

    case BankedFIFOEnq(fifo, data, ens) => emitMemWrite(lhs)
      if (config.dotDetail == 0) emitMemWrite(lhs) else {
        data.foreach{a => emitVert(a); emitEdge(a, fifo)}
        ens.foreach{a => emitVert(a); emitEn(a, lhs)}
      }

    case _ => super.emitNode(lhs, rhs)
  }
}
