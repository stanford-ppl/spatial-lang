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
        (boundOf.get(start), boundOf.get(end), boundOf.get(step)).zipped.foreach { case (bstart, bend, bstep) =>
          dbg(s"$lhs, bstart=$bstart, bend=$bend, bstep=$bstep, par=$parInt")
          assert((bend - bstart) % (bstep * parInt) == 0, 
            s"Cannot handle unaligned iterator range:" + 
            s"${start.name}=$bstart" + 
            s"${end.name}=$bend" +
            s"${step.name}=$bstep" + 
            s"${par.name}=$parInt"
          )
        }
      case CounterChainNew(counters) =>
        emit(lhs, s"CounterChain(List(${counters.mkString(",")}))", rhs)
      case _ => super.emitNode(lhs, rhs)
    }
  }
}
