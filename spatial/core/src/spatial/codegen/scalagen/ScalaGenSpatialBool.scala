package spatial.codegen.scalagen

import argon.core._
import argon.nodes._
import spatial.aliases._
import argon.emul.FixedPoint


trait ScalaGenSpatialBool extends ScalaGenBits {

  override protected def remap(tp: Type[_]): String = tp match {
    case BooleanType => "Bool"
    case _ => super.remap(tp)
  }

  override protected def quoteConst(c: Const[_]): String = c match {
    case Const(true)  => "TRUE"
    case Const(false) => "FALSE"
    case _ => super.quoteConst(c)
  }

  override def invalid(tp: Type[_]) = tp match {
    case BooleanType => "Bool(false,false)"
    case _ => super.invalid(tp)
  }


  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case Not(x)    => emit(src"val $lhs = !$x")
    case And(x,y)  => emit(src"val $lhs = $x && $y")
    case Or(x,y)   => emit(src"val $lhs = $x || $y")
    case XOr(x,y)  => emit(src"val $lhs = $x !== $y")
    case XNor(x,y) => emit(src"val $lhs = $x === $y")

    case RandomBoolean(None) => emit(src"val $lhs = Bool(java.util.concurrent.ThreadLocalRandom.current().nextBoolean())")
    case RandomBoolean(Some(max)) => emit(src"val $lhs = Bool(java.util.concurrent.ThreadLocalRandom.current().nextBoolean() && $max)")
    case StringToBoolean(x) => 
      emit(src"val $lhs = ${x}.toInt != 0")
      x match {
        case Def(ArrayApply(array, i)) => 
          array match {
            case Def(InputArguments()) => 
              val ii = i match {case c: Const[_] => c match {case Const(c: FixedPoint) => c.toInt; case _ => -1}; case _ => -1}
              if (cliArgs.contains(ii)) cliArgs += (ii -> s"${cliArgs(ii)} / ${lhs.name.getOrElse(s"${lhs.ctx}")}")
              else cliArgs += (ii -> lhs.name.getOrElse(s"${lhs.ctx}"))
            case _ =>
          }
        case _ =>          
      }

    case _ => super.emitNode(lhs, rhs)
  }
}
