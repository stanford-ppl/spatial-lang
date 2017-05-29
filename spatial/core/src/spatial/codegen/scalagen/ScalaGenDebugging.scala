package spatial.codegen.scalagen

import argon.codegen.scalagen.ScalaCodegen
import spatial.SpatialExp
import spatial.lang.DebuggingExp

trait ScalaGenDebugging extends ScalaCodegen {
  val IR: SpatialExp
  import IR._

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]) = rhs match {
    case PrintIf(en,msg)             => emit(src"val $lhs = if ($en) System.out.print($msg)")
    case PrintlnIf(en,msg)           => emit(src"val $lhs = if ($en) System.out.println($msg)")
    case AssertIf(en,cond,Some(msg)) => emit(src"val $lhs = if ($en) assert($cond, $msg)")
    case AssertIf(en,cond,None)      => emit(src"val $lhs = if ($en) assert($cond)")
    case _ => super.emitNode(lhs, rhs)
  }
}
