package spatial.codegen.scalagen

import argon.codegen.scalagen.ScalaCodegen
import argon.core._
import spatial.compiler._
import spatial.nodes._

trait ScalaGenDebugging extends ScalaCodegen {

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]) = rhs match {
    case PrintIf(en,msg)             => emit(src"val $lhs = if ($en) System.out.print($msg)")
    case PrintlnIf(en,msg)           => emit(src"val $lhs = if ($en) System.out.println($msg)")
    case AssertIf(en,cond,Some(msg)) => emit(src"val $lhs = if ($en) assert($cond, $msg)")
    case AssertIf(en,cond,None)      => emit(src"val $lhs = if ($en) assert($cond)")
    case _ => super.emitNode(lhs, rhs)
  }

}
