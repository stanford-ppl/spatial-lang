package spatial.codegen.scalagen

import argon.core._
import argon.nodes._
import spatial.aliases._
import argon.emul.FixedPoint


trait ScalaGenSpatialFixPt extends ScalaGenBits {

  override protected def remap(tp: Type[_]): String = tp match {
    case FixPtType(_,_,_) => "FixedPoint"
    case _ => super.remap(tp)
  }

  override protected def quoteConst(c: Const[_]): String = (c.tp,c) match {
    case (FixPtType(sign,int,frac), Const(c: FixedPoint)) =>
      if(int > 32 | (!sign & int == 32)) s"""FixedPoint(BigDecimal("$c"),FixFormat($sign,$int,$frac))"""
      else s"""FixedPoint(BigDecimal("$c"),FixFormat($sign,$int,$frac))"""
    case _ => super.quoteConst(c)
  }

  override def invalid(tp: Type[_]) = tp match {
    case FixPtType(s,i,f) => src"FixedPoint.invalid(FixFormat($s,$i,$f))"
    case _ => super.invalid(tp)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case FixInv(x)   => emit(src"val $lhs = ~$x")
    case FixNeg(x)   => emit(src"val $lhs = -$x")
    case FixAdd(x,y) => emit(src"val $lhs = $x + $y")
    case FixSub(x,y) => emit(src"val $lhs = $x - $y")
    case FixMul(x,y) => emit(src"val $lhs = $x * $y")
    case FixDiv(x,y) => emit(src"val $lhs = $x / $y")
    case FixMod(x,y) => emit(src"val $lhs = $x % $y")
    case FixAnd(x,y) => emit(src"val $lhs = $x & $y")
    case FixOr(x,y)  => emit(src"val $lhs = $x | $y")
    case FixLt(x,y)  => emit(src"val $lhs = $x < $y")
    case FixLeq(x,y) => emit(src"val $lhs = $x <= $y")
    case FixXor(x,y) => emit(src"val $lhs = $x ^ $y")

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
    case FixConvert(x) =>
      val FixPtType(s, i, f) = lhs.tp
      emit(src"val $lhs = $x.toFixedPoint(FixFormat($s,$i,$f))")

    case FixPtToFltPt(x) =>
      val FltPtType(g,e) = lhs.tp
      emit(src"val $lhs = $x.toFloatPoint(FltFormat(${g-1},$e))")

    case StringToFixPt(x) =>
      val FixPtType(s,i,f) = lhs.tp
      emit(src"val $lhs = FixedPoint($x, FixFormat($s,$i,$f))")
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

    case FixRandom(Some(max)) =>
      val FixPtType(s,i,f) = lhs.tp
      emit(src"val $lhs = FixedPoint.random($max, FixFormat($s,$i,$f))")

    case FixRandom(None) =>
      val FixPtType(s,i,f) = lhs.tp
      emit(src"val $lhs = FixedPoint.random(FixFormat($s,$i,$f))")

    case FixUnif() =>
      val FixPtType(s,i,f) = lhs.tp
      emit(src"val $lhs = FixedPoint.random(FixFormat($s,$i,$f))")
      //emit(src"val $lhs = FixedPoint(BigDecimal(scala.util.Random.nextDouble()), FixFormat($s,$i,$f))")

    case Char2Int(x) => 
      emit(src"val $lhs = ${x}(0).toInt")
    case Int2Char(x) => 
      emit(src"val $lhs = ${x}.toChar")


    case _ => super.emitNode(lhs, rhs)
  }
}
