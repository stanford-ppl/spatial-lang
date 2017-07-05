package spatial.codegen.scalagen

import argon.core._
import argon.nodes._
import spatial.aliases._


trait ScalaGenSpatialFixPt extends ScalaGenBits {

  override protected def remap(tp: Type[_]): String = tp match {
    case FixPtType(_,_,_) => "Number"
    case _ => super.remap(tp)
  }

  override protected def quoteConst(c: Const[_]): String = (c.tp,c) match {
    case (FixPtType(sign,int,frac), Const(c: BigDecimal)) => 
      if(int > 32 | (!sign & int == 32)) s"Number(BigDecimal(${c}L),true,FixedPoint($sign,$int,$frac))"
      else s"Number(BigDecimal($c),true,FixedPoint($sign,$int,$frac))"
    case _ => super.quoteConst(c)
  }

  override def invalid(tp: Type[_]) = tp match {
    case FixPtType(s,i,f) => src"X(FixedPoint($s,$i,$f))"
    case _ => super.invalid(tp)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case FixInv(x)   => emit(src"val $lhs = ~$x")
    case FixNeg(x)   => emit(src"val $lhs = -$x")
    case FixAdd(x,y) => emit(src"val $lhs = $x + $y")
    case FixSub(x,y) => emit(src"val $lhs = $x - $y")
    case FixMul(x,y) => emit(src"val $lhs = $x * $y")
    case FixDiv(x,y) => emit(src"val $lhs = $x / $y")
    case FixAnd(x,y) => emit(src"val $lhs = $x & $y")
    case FixOr(x,y)  => emit(src"val $lhs = $x | $y")
    case FixLt(x,y)  => emit(src"val $lhs = $x < $y")
    case FixLeq(x,y) => emit(src"val $lhs = $x <= $y")
    case FixMod(x,y) => emit(src"val $lhs = $x % $y")
    case FixXor(x,y)  => emit(src"val $lhs = $x ^ $y")

    case FixLsh(x,y) => emit(src"val $lhs = $x << $y")
    case FixRsh(x,y) => emit(src"val $lhs = $x >> $y")
    case FixURsh(x,y) => emit(src"val $lhs = $x >>> $y")

    case SatAdd(x,y) => emit(src"val $lhs = $x <+> $y")
    case SatSub(x,y) => emit(src"val $lhs = $x <-> $y")
    case SatMul(x,y) => emit(src"val $lhs = $x <*> $y")
    case SatDiv(x,y) => emit(src"val $lhs = $x </> $y")
    case UnbMul(x,y) => emit(src"val $lhs = $x *& $y")
    case UnbDiv(x,y) => emit(src"val $lhs = $x /& $y")
    case UnbSatMul(x,y) => emit(src"val $lhs = $x <*&> $y")
    case UnbSatDiv(x,y) => emit(src"val $lhs = $x </&> $y")

    case FixNeq(x,y) => emit(src"val $lhs = $x !== $y")
    case FixEql(x,y) => emit(src"val $lhs = $x === $y")
    case FixConvert(x) => lhs.tp match {
      case FixPtType(s,i,f) => emit(src"val $lhs = Number($x.value, $x.valid, FixedPoint($s,$i,$f))")
    }
    case FixPtToFltPt(x) => lhs.tp match {
      case FltPtType(g,e) => emit(src"val $lhs = Number($x.value, $x.valid, FloatPoint($g,$e))")
    }
    case StringToFixPt(x) => lhs.tp match {
      case FixPtType(s,i,f) => emit(src"val $lhs = Number($x, FixedPoint($s,$i,$f))")
    }
    case FixRandom(Some(max)) => lhs.tp match {
      case FixPtType(s,i,f) => emit(src"val $lhs = Number.random($max, FixedPoint($s,$i,$f))")
    }
    case FixRandom(None) => lhs.tp match {
      case FixPtType(s,i,f) => emit(src"val $lhs = Number.random(FixedPoint($s,$i,$f))")
    }
    case Char2Int(x) => 
      emit(src"val $lhs = ${x}(0).toInt")
    case Int2Char(x) => 
      emit(src"val $lhs = ${x}.toChar")


    case _ => super.emitNode(lhs, rhs)
  }
}
