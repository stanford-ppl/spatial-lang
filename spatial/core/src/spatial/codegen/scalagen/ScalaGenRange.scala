package spatial.codegen.scalagen

import argon.core._
import argon.codegen.scalagen.ScalaCodegen
import spatial.aliases._
import spatial.nodes._

trait ScalaGenRange extends ScalaCodegen {

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]) = rhs match {
    case RangeForeach(start, end, step, func, i) =>
      open(src"val $lhs = for ($i <- $start until $end by $step) {")
      emitBlock(func)
      close("}")
    case _ => super.emitNode(lhs, rhs)
  }

}
