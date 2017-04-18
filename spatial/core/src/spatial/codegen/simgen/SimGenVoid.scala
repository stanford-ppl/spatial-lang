package spatial.codegen.simgen

import argon.ops.VoidExp
import spatial.SpatialExp

trait SimGenVoid extends SimCodegen {
  val IR: SpatialExp
  import IR._

  override protected def quoteConst(c: Const[_]): String = c match {
    case Const(()) => "()"
    case _ => super.quoteConst(c)
  }
}
