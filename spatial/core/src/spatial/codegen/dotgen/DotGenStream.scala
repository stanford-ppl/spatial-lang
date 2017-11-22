package spatial.codegen.dotgen

import argon.codegen.dotgen.DotCodegen
import argon.core._
import spatial.aliases._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._

trait DotGenStream extends DotCodegen with DotGenReg {

  override def attr(n:Exp[_]) = n match {
    case _ if isStreamIn(n) => super.attr(n).shape(box).style(filled).color(gold)
    case _ if isStreamOut(n) => super.attr(n).shape(box).style(filled).color(gold)
    case _ => super.attr(n)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case StreamInNew(_) if fringeOf(lhs).nonEmpty => emitVert(lhs)
    case StreamOutNew(_) if fringeOf(lhs).nonEmpty => emitVert(lhs)

    case BankedStreamRead(strm, ens) =>
      if (config.dotDetail == 0) emitMemRead(lhs) else {
        emitVert(lhs)
        emitEdge(strm, lhs)
        ens.foreach{emitEn(_,strm)}
      }

    case BankedStreamWrite(strm, data, ens) =>
      if (config.dotDetail == 0) emitMemWrite(lhs) else {
        data.foreach{emitEdge(_, strm)}
        ens.foreach{emitEn(_,strm)}
      }

    case _ => super.emitNode(lhs, rhs)
  }

  override protected def emitFileFooter() {
    super.emitFileFooter()
  }

}
