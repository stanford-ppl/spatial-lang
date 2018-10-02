package spatial.codegen.pirgen

import argon.core._
import spatial.nodes._
import spatial.utils._
import spatial.metadata._

trait PIRGenCounter extends PIRCodegen {
  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = {
    rhs match {
      case CounterNew(start, end, step, par) =>
        val parInt = getConstant(par).get.asInstanceOf[Int]
        emit(DefRhs(lhs, "Counter", "min"->start, "max"->end, "step"->step, "par"->parInt))
      case CounterChainNew(counters) =>
        emit(DefRhs(lhs, "CounterChain", counters.toList))
      case _ => super.emitNode(lhs, rhs)
    }
  }
}
