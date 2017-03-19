package spatial.codegen.simgen

import argon.ops.VoidExp

trait SimGenVoid extends SimCodegen {
  val IR: VoidExp
  import IR._

  override protected def quoteConst(c: Const[_]): String = c match {
    case Const(()) => "()"
    case _ => super.quoteConst(c)
  }
}
