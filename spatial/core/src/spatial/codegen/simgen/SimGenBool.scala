package spatial.codegen.simgen

import argon.ops.BoolExp
import spatial.SpatialExp

trait SimGenBool extends SimCodegen {
  val IR: SpatialExp
  import IR._

  override protected def remap(tp: Type[_]): String = tp match {
    case BoolType if hw  => "Bit"
    case BoolType        => "Boolean"
    case _ => super.remap(tp)
  }

  override protected def quoteConst(c: Const[_]): String = c match {
    case Const(true)  if hw => "TRUE"
    case Const(false) if hw => "FALSE"
    case Const(c: Boolean)  => c.toString
    case _ => super.quoteConst(c)
  }


  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case Not(x)    => delay(lhs, rhs, src"!$x")
    case And(x,y)  => delay(lhs, rhs, src"$x && $y")
    case Or(x,y)   => delay(lhs, rhs, src"$x || $y")
    case XOr(x,y)  if hw => delay(lhs, rhs, src"$x !== $y")
    case XNor(x,y) if hw => delay(lhs, rhs, src"$x === $y")
    case XOr(x,y)     => emit(src"val $lhs = $x != $y")
    case XNor(x,y)    => emit(src"val $lhs = $x == $y")

    case RandomBool(None) => emit(src"val $lhs = java.util.concurrent.ThreadLocalRandom.current().nextBoolean()")
    case RandomBool(Some(max)) => emit(src"val $lhs = java.util.concurrent.ThreadLocalRandom.current().nextBoolean() && $max")
    case _ => super.emitNode(lhs, rhs)
  }
}
