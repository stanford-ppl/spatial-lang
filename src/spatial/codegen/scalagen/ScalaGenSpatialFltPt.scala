package spatial.codegen.scalagen

import argon.ops.{FixPtExp, FltPtExp}

trait ScalaGenSpatialFltPt extends ScalaGenBits {
  val IR: FltPtExp with FixPtExp
  import IR._

  override protected def remap(tp: Type[_]): String = tp match {
    case FltPtType(_,_) => "Number"
    case _ => super.remap(tp)
  }

  override protected def quoteConst(c: Const[_]): String = (c.tp, c) match {
    case (FltPtType(g,e), Const(c: BigDecimal)) => s"Number($c, true, FloatPoint($g,$e))"
    case _ => super.quoteConst(c)
  }

  override def invalid(tp: IR.Type[_]) = tp match {
    case FltPtType(g,e) => src"X(FloatPoint($g,$e))"
    case _ => super.invalid(tp)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case FltNeg(x)   => emit(src"val $lhs = -$x")
    case FltAdd(x,y) => emit(src"val $lhs = $x + $y")
    case FltSub(x,y) => emit(src"val $lhs = $x - $y")
    case FltMul(x,y) => emit(src"val $lhs = $x * $y")
    case FltDiv(x,y) => emit(src"val $lhs = $x / $y")
    case FltLt(x,y)  => emit(src"val $lhs = $x < $y")
    case FltLeq(x,y) => emit(src"val $lhs = $x <= $y")

    case FltNeq(x,y)   => emit(src"val $lhs = $x !== $y")
    case FltEql(x,y)   => emit(src"val $lhs = $x === $y")
    case FltConvert(x) => lhs.tp match {
      case FltPtType(g,e) => emit(src"val $lhs = Number($x.value, $x.valid, FloatPoint($g,$e))")
    }
    case FltPtToFixPt(x) => lhs.tp match {
      case FixPtType(s,i,f)  => emit(src"val $lhs = Number($x.value, $x.valid, FixedPoint($s,$i,$f))")
    }
    case StringToFltPt(x) => lhs.tp match {
      case FltPtType(g,e) => emit(src"val $lhs = Number($x, FloatPoint($g,$e))")
    }

    case FltRandom(Some(max)) => lhs.tp match {
      case FloatType()  => emit(src"val $lhs = Number(scala.util.Random.nextFloat() * $max)")
      case DoubleType() => emit(src"val $lhs = Number(scala.util.Random.nextDouble() * $max)")
    }
    case FltRandom(None) => lhs.tp match {
      case FloatType()  => emit(src"val $lhs = Number(scala.util.Random.nextFloat())")
      case DoubleType() => emit(src"val $lhs = Number(scala.util.Random.nextDouble()))")
    }

    case _ => super.emitNode(lhs, rhs)
  }

}
