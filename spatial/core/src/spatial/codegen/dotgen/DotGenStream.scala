package spatial.codegen.dotgen

import argon.codegen.dotgen.DotCodegen
import spatial.SpatialExp
import argon.Config

trait DotGenStream extends DotCodegen with DotGenReg {
  val IR: SpatialExp
  import IR._

  override def attr(n:Exp[_]) = n match {
    case n if isStreamIn(n) => super.attr(n).shape(box).style(filled).color(gold)
    case n if isStreamOut(n) => super.attr(n).shape(box).style(filled).color(gold)
    case n => super.attr(n)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case StreamInNew(bus) if fringeOf(lhs).nonEmpty => emitVert(lhs)
    case StreamOutNew(bus) if fringeOf(lhs).nonEmpty => emitVert(lhs)
    case StreamRead(stream, en) => if (Config.dotDetail > 0) {emitVert(lhs); emitEdge(stream,lhs); emitEn(en,lhs)} else {emitMemRead(lhs)}
    case StreamWrite(stream, data, en) => if (Config.dotDetail > 0) {emitEdge(data,stream); emitEn(en,stream)} else {emitMemWrite(lhs)}
    case _ => super.emitNode(lhs, rhs)
  }

  override protected def emitFileFooter() {
    super.emitFileFooter()
  }

}
