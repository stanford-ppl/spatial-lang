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
        emit(lhs, s"Counter(min=${quote(start)}, max=${quote(end)}, step=${quote(step)}, par=$parInt)", rhs)
      case CounterChainNew(counters) =>
        emit(lhs, s"CounterChain(List(${counters.mkString(",")}))", rhs)
      case _ => super.emitNode(lhs, rhs)
    }
  }
}
