package spatial.codegen.scalagen

import argon.core._
import argon.nodes._
import spatial.aliases._
import argon.emul.FixedPoint

trait ScalaGenSpatialFltPt extends ScalaGenBits {

  override protected def remap(tp: Type[_]): String = tp match {
    case FltPtType(_,_) => "FloatPoint"
    case _ => super.remap(tp)
  }

  override protected def quoteConst(c: Const[_]): String = (c.tp, c) match {
    case (FltPtType(g,e), Const(c: FloatPoint)) => s"""FloatPoint(BigDecimal("$c"), FltFormat(${g-1},$e))"""
    case _ => super.quoteConst(c)
  }

  override def invalid(tp: Type[_]) = tp match {
    case FltPtType(g,e) => src"FloatPoint.invalid(FltFormat(${g-1},$e))"
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
    case FltConvert(x) =>
      val FltPtType(g,e) = lhs.tp
      emit(src"val $lhs = $x.toFloatPoint(FltFormat(${g-1},$e))")

    case FltPtToFixPt(x) =>
      val FixPtType(s,i,f) = lhs.tp
      emit(src"val $lhs = $x.toFixedPoint(FixFormat($s,$i,$f))")

    case StringToFltPt(x) =>
      val FltPtType(g,e) = lhs.tp
      emit(src"val $lhs = FloatPoint($x, FltFormat(${g-1},$e))")
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


    case FltRandom(Some(max)) =>
      val FltPtType(g,e) = lhs.tp
      emit(src"val $lhs = FloatPoint.random($max, FltFormat(${g-1},$e))")

    case FltRandom(None) =>
      val FltPtType(g,e) = lhs.tp
      emit(src"val $lhs = FloatPoint.random(FltFormat(${g-1},$e))")


    case _ => super.emitNode(lhs, rhs)
  }

}
