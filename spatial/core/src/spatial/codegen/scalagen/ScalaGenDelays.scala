package spatial.codegen.scalagen

import argon.codegen.scalagen.ScalaCodegen
import argon.core._
import spatial.aliases._
import spatial.nodes._

trait ScalaGenDelays extends ScalaCodegen {

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case DelayLine(size,data)   => emit(src"val $lhs = $data")
    case _ => super.emitNode(lhs, rhs)
  }

}
