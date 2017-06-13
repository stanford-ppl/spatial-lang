package spatial.codegen.dotgen

import argon.codegen.dotgen.DotCodegen
import argon.core.Config
import argon.nodes._
import spatial.compiler._
import spatial.utils._

trait DotGenStruct extends DotCodegen with DotGenReg {

  override def attr(n:Exp[_]) = n match {
    case n if isStreamIn(n) => super.attr(n).shape(box).style(filled).color(gold)
    case n if isStreamOut(n) => super.attr(n).shape(box).style(filled).color(gold)
    case n => super.attr(n)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case SimpleStruct(items) => if (Config.dotDetail > 0) {
      emitVert(lhs)
      items.foreach{a => emitEdge(a._2, lhs, a._1)}
    }
    case _ => super.emitNode(lhs, rhs)
  }

  override protected def emitFileFooter() {
    super.emitFileFooter()
  }

}
