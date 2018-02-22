package argon.codegen.chiselgen

import argon.core._
import argon.nodes._

trait ChiselGenFltPt extends ChiselCodegen {

  override protected def remap(tp: Type[_]): String = tp match {
    case FloatType()  => "Float"
    case DoubleType() => "Double"
    case _ => super.remap(tp)
  }

  override protected def needsFPType(tp: Type[_]): Boolean = tp match {
      case FloatType() => true
      case DoubleType() => true
      case _ => super.needsFPType(tp)
  }

  override protected def quoteConst(c: Const[_]): String = (c.tp, c) match {
    // recFNFromFN(8, 24, Mux(faddCode === io.opcode, io.b, getFloatBits(1.0f).S))
    case (FloatType(), Const(cc)) => cc.toString + src".FlP(8, 24)"
    case (DoubleType(), Const(c)) => "DspReal(" + c.toString + ")"
    case _ => super.quoteConst(c)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case FltNeg(x)   => emit(src"val $lhs = -$x")
    case FltAdd(x,y) => emit(src"val $lhs = $x + $y")
    case FltSub(x,y) => emit(src"val $lhs = $x - $y")
    case FltMul(x,y) => emit(src"val $lhs = $x *-* $y")
    case FltDiv(x,y) => emit(src"val $lhs = $x /-/ $y")
    case FltLt(x,y)  => emit(src"val $lhs = $x < $y")
    case FltLeq(x,y) => emit(src"val $lhs = $x <= $y")
    case FltNeq(x,y) => emit(src"val $lhs = $x != $y")
    case FltEql(x,y) => emit(src"val $lhs = $x === $y")
    case FltRandom(x) => lhs.tp match {
      case FloatType()  => emit(src"val $lhs = chisel.util.Random.nextFloat()")
      case DoubleType() => emit(src"val $lhs = chisel.util.Random.nextDouble()")
    }
    case FltConvert(x) => lhs.tp match {
      case FloatType()  => emit(src"val $lhs = $x.toFloat")
      case DoubleType() => emit(src"val $lhs = $x.toDouble")
    }
    case FltPtToFixPt(x) => lhs.tp match {
      case IntType()  => emit(src"val $lhs = $x.toInt")
      case LongType() => emit(src"val $lhs = $x.toLong")
      case FixPtType(s,d,f) => emit(src"val $lhs = $x.toFloat")
    }
    case StringToFltPt(x) => lhs.tp match {
      case DoubleType() => emit(src"val $lhs = $x.toDouble")
      case FloatType()  => emit(src"val $lhs = $x.toFloat")
    }
    case _ => super.emitNode(lhs, rhs)
  }

}
