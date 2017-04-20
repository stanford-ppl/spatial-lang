package spatial.codegen.scalagen

import argon.codegen.scalagen.ScalaCodegen
import spatial.SpatialExp
import spatial.api.RangeExp

trait ScalaGenRange extends ScalaCodegen{
  val IR: SpatialExp
  import IR._

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]) = rhs match {
    case RangeForeach(start, end, step, func, i) =>
      open(src"val $lhs = for ($i <- $start until $end by $step) {")
      emitBlock(func)
      close("}")
    case _ => super.emitNode(lhs, rhs)
  }
}
