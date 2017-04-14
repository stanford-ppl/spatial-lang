package spatial.codegen.simgen

import argon.ops.FltPtExp
import spatial.SpatialExp

trait SimGenFltPt extends SimCodegen {
  val IR: SpatialExp
  import IR._

  override protected def remap(tp: Type[_]): String = tp match {
    case FltPtType(_,_) if hw => "Number"
    case FloatType()  => "Float"
    case DoubleType() => "Double"
    case _ => super.remap(tp)
  }

  override protected def quoteConst(c: Const[_]): String = (c.tp, c) match {
    case (FltPtType(g,e), Const(c: BigDecimal)) if hw => src"Number($c, true, FloatPoint($g,$e))"
    case (FloatType(), Const(c: BigDecimal)) => c.toString + "f"
    case (DoubleType(), Const(c: BigDecimal)) => c.toString
    case _ => super.quoteConst(c)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case FltNeg(x)   => delay(lhs, rhs, src"-$x")
    case FltAdd(x,y) => delay(lhs, rhs, src"$x + $y")
    case FltSub(x,y) => delay(lhs, rhs, src"$x - $y")
    case FltMul(x,y) => delay(lhs, rhs, src"$x * $y")
    case FltDiv(x,y) => delay(lhs, rhs, src"$x / $y")
    case FltLt(x,y)  => delay(lhs, rhs, src"$x < $y")
    case FltLeq(x,y) => delay(lhs, rhs, src"$x <= $y")

    case FltNeq(x,y) if hw => delay(lhs, rhs, src"$x !== $y")
    case FltEql(x,y) if hw => delay(lhs, rhs, src"$x === $y")
    case FltConvert(x) if hw => lhs.tp match {
      case FltPtType(g,e) => delay(lhs, rhs, src"Number($x.value, $x.valid, FloatPoint($g,$e))")
    }

    case FltNeq(x,y) => emit(src"val $lhs = $x != $y")
    case FltEql(x,y) => emit(src"val $lhs = $x == $y")
    case FltConvert(x) => lhs.tp match {
      case FloatType()  => emit(src"val $lhs = $x.toFloat")
      case DoubleType() => emit(src"val $lhs = $x.toDouble")
    }

    case FltRandom(Some(max)) if !hw => lhs.tp match {
      case FloatType()  => emit(src"val $lhs = scala.util.Random.nextFloat() * $max")
      case DoubleType() => emit(src"val $lhs = scala.util.Random.nextDouble() * $max")
    }
    case FltRandom(None) if !hw => lhs.tp match {
      case FloatType()  => emit(src"val $lhs = scala.util.Random.nextFloat()")
      case DoubleType() => emit(src"val $lhs = scala.util.Random.nextDouble()")
    }

    case _ => super.emitNode(lhs, rhs)
  }

}
