package spatial.codegen.simgen

import argon.ops.FixPtExp

trait SimGenFixPt extends SimCodegen {
  val IR: FixPtExp
  import IR._

  override protected def remap(tp: Staged[_]): String = tp match {
    case FixPtType(_,_,_) if hw => "Number"
    case IntType() if !hw       => "Int"
    case LongType() if !hw      => "Long"
    case _ => super.remap(tp)
  }

  override protected def quoteConst(c: Const[_]): String = (c.tp,c) match {
    case (FixPtType(sign,int,frac), Const(c: BigDecimal)) if hw =>
      src"Number(BigDecimal($c),true,FixedPoint($sign,$int,$frac))"

    case (IntType(), Const(c: BigDecimal))  => c.toString
    case (LongType(), Const(c: BigDecimal)) => c.toString
    case _ => super.quoteConst(c)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case FixInv(x)   => delay(lhs, rhs, src"~$x")
    case FixNeg(x)   => delay(lhs, rhs, src"-$x")
    case FixAdd(x,y) => delay(lhs, rhs, src"$x + $y")
    case FixSub(x,y) => delay(lhs, rhs, src"$x - $y")
    case FixMul(x,y) => delay(lhs, rhs, src"$x * $y")
    case FixDiv(x,y) => delay(lhs, rhs, src"$x / $y")
    case FixAnd(x,y) => delay(lhs, rhs, src"$x & $y")
    case FixOr(x,y)  => delay(lhs, rhs, src"$x | $y")
    case FixLt(x,y)  => delay(lhs, rhs, src"$x < $y")
    case FixLeq(x,y) => delay(lhs, rhs, src"$x <= $y")
    case FixMod(x,y) => delay(lhs, rhs, src"$x % $y")

    case FixNeq(x,y) if hw => delay(lhs, rhs, src"$x !== $y")
    case FixEql(x,y) if hw => delay(lhs, rhs, src"$x === $y")
    case FixConvert(x) if hw => lhs.tp match {
      case FixPtType(s,i,f) => delay(lhs, rhs, src"Number($x.value, $x.valid, FixedPoint($s,$i,$f))")
    }

    case FixNeq(x,y) => emit(src"val $lhs = $x != $y")
    case FixEql(x,y) => emit(src"val $lhs = $x == $y")
    case FixConvert(x) => lhs.tp match {
      case IntType()  => emit(src"val $lhs = $x.toInt")
      case LongType() => emit(src"val $lhs = $x.toLong")
    }

    case FixRandom(Some(max)) if !hw => lhs.tp match {
      case IntType()  => emit(src"val $lhs = scala.util.Random.nextInt($max)")
      case LongType() => emit(src"val $lhs = scala.util.Random.nextLong() % $max")
    }
    case FixRandom(None) if !hw => lhs.tp match {
      case IntType()  => emit(src"val $lhs = scala.util.Random.nextInt()")
      case LongType() => emit(src"val $lhs = scala.util.Random.nextLong()")
    }


    case _ => super.emitNode(lhs, rhs)
  }
}
