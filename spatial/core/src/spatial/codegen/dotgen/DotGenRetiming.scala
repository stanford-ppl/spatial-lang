package spatial.codegen.dotgen

import argon.codegen.dotgen._
import argon.core.Config
import spatial.SpatialExp
import spatial.{SpatialConfig, SpatialExp}


trait DotGenRetiming extends DotGenReg {
  val IR: SpatialExp
  import IR._

  override def attr(n:Exp[_]) = n match {
    case lhs: Sym[_] => lhs match {
      case Def(ValueDelay(size, data)) => super.attr(n).shape(diamond).label(src"""x${lhs.id}\nD$size""")
      case Def(ShiftRegNew(size, init)) => super.attr(n).shape(diamond).label(src"""x${lhs.id}\nD$size""")
      case Def(ShiftRegRead(shiftReg)) => super.attr(n).shape(point)
      case _ => super.attr(n)
    }
    case _ => super.attr(n)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case ValueDelay(size, data) => if (Config.dotDetail > 0) {emitVert(lhs); emitRetime(data, lhs)}
    case ShiftRegNew(size, init) => if (Config.dotDetail > 0) emitVert(lhs)
    case ShiftRegRead(shiftReg) => if (Config.dotDetail > 0) {emitVert(lhs); emitRetimeRead(lhs, shiftReg)}
    case ShiftRegWrite(shiftReg, data, en) => if (Config.dotDetail > 0) emitRetimeWrite(data.asInstanceOf[Sym[_]], shiftReg)
    case _ => super.emitNode(lhs, rhs)
  }

  override protected def emitFileFooter() {
    super.emitFileFooter()
  }
}
