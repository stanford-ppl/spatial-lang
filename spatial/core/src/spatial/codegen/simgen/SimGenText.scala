package spatial.codegen.simgen

import argon.core.compiler._
import argon.util.escapeString
import spatial.SpatialExp

trait SimGenText extends SimCodegen {
  val IR: SpatialExp
  import IR._

  override protected def remap(tp: Type[_]): String = tp match {
    case TextType => "String"
    case _ => super.remap(tp)
  }

  override protected def quoteConst(c: Const[_]): String = c match {
    case Const(c: String) => escapeString(c)
    case _ => super.quoteConst(c)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case ToString(x) => emit(src"val $lhs = $x.toString")
    case TextConcat(x,y) => emit(src"val $lhs = $x + $y")
    case TextEquals(x,y) => emit(src"val $lhs = $x == $y")
    case TextDiffer(x,y) => emit(src"val $lhs = $x != $y")
    case _ => super.emitNode(lhs, rhs)
  }

}
