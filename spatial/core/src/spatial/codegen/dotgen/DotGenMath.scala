package spatial.codegen.dotgen

import argon.codegen.dotgen.DotCodegen
import argon.core._
import argon.nodes._
import spatial.aliases._
import spatial.nodes._

trait DotGenMath extends DotCodegen {

  override def attr(n:Exp[_]) = n match {
    case Def(FixAbs(_)) => super.attr(n).shape(circle).label("|x|")
    case Def(FltAbs(_)) => super.attr(n).shape(circle).label("|x|")
    case Def(FltLog(_)) => super.attr(n).shape(circle).label("log")
    case Def(FltExp(_)) => super.attr(n).shape(circle).label("exp")
    case Def(FltSqrt(_)) => super.attr(n).shape(circle).label("sqrt")
    case Def(_:Mux[_]) => super.attr(n).shape(mux)
    case Def(_:Min[_]) => super.attr(n).shape(mux).label("min")
    case Def(_:Max[_]) => super.attr(n).shape(mux).label("max")
    case _ => super.attr(n)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case FixAbs(x) => if (config.dotDetail > 0) { emitVert(lhs); emitEdge(x,lhs) }
    case FltAbs(x) => if (config.dotDetail > 0) { emitVert(lhs); emitEdge(x,lhs) }
    case FltLog(x) => if (config.dotDetail > 0) { emitVert(lhs); emitEdge(x,lhs) }
    case FltExp(x) => if (config.dotDetail > 0) { emitVert(lhs); emitEdge(x,lhs) }
    case FltSqrt(x) => if (config.dotDetail > 0) { emitVert(lhs); emitEdge(x,lhs) }

    case Mux(sel, a, b) => if (config.dotDetail > 0) {
      emitVert(lhs)
      emitEdge(a, lhs)
      emitEdge(b, lhs)
      emitSel(sel, lhs)
    }

    case Min(a, b) => if (config.dotDetail > 0) {
      emitVert(lhs)
      emitEdge(a, lhs)
      emitEdge(b, lhs)
    }

    case Max(a,b) => if (config.dotDetail > 0) {
      emitVert(lhs)
      emitEdge(a, lhs)
      emitEdge(b, lhs)
    }

    case _ => super.emitNode(lhs, rhs)
  }

}
