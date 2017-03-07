package spatial.codegen.simgen

import argon.ops.AssertExp

trait SimGenAssert extends SimCodegen {
  val IR: AssertExp
  import IR._

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]) = rhs match {
    case Assert(cond, Some(msg)) =>
      emit(src"val $lhs = assert($cond, $msg)")
    case Assert(cond, None) =>
      emit(src"val $lhs = assert($cond)")

    case _ => super.emitNode(lhs, rhs)
  }
}
