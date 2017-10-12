package spatial.codegen.dotgen

import argon.core._
import spatial.nodes._

trait DotGenRetiming extends DotGenReg {

  override def attr(n:Exp[_]) = n match {
    case lhs: Sym[_] => lhs match {
      case Def(DelayLine(size, data)) => super.attr(n).shape(diamond).label(src"""x${lhs.id}\nD$size""")
      case _ => super.attr(n)
    }
    case _ => super.attr(n)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case DelayLine(size, data) => if (config.dotDetail > 0) {emitVert(lhs); emitRetime(data, lhs)}
    case _ => super.emitNode(lhs, rhs)
  }

  override protected def emitFileFooter() {
    super.emitFileFooter()
  }
}
