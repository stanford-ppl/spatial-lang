package spatial.codegen.simgen

import argon.ops.IfThenElseExp
import spatial.SpatialExp

trait SimGenIfThenElse extends SimCodegen {
  val IR: SpatialExp
  import IR._

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case IfThenElse(cond, thenp, elsep) =>
      open(src"val $lhs = {")
      open(src"if ($cond) { ")
      emitBlock(thenp)
      close("}")
      open("else {")
      emitBlock(elsep)
      close("}")
      close("}")

    case _ => super.emitNode(lhs, rhs)
  }

}
