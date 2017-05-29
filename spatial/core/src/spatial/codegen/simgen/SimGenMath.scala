package spatial.codegen.simgen

import argon.ops.{FixPtExp, FltPtExp}
import spatial.SpatialExp
import spatial.lang.MathExp

trait SimGenMath extends SimCodegen {
  val IR: SpatialExp
  import IR._

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case Min(a, b) if hw => delay(lhs, rhs, src"Number.min($a, $b)")
    case Max(a, b) if hw => delay(lhs, rhs, src"Number.max($a, $b)")
    case FixAbs(x) if hw => delay(lhs, rhs, src"Number.abs($x)")
    case FltAbs(x) if hw => delay(lhs, rhs, src"Number.abs($x)")
    case FltLog(x) if hw => delay(lhs, rhs, src"Number.log($x)")
    case FltExp(x) if hw => delay(lhs, rhs, src"Number.exp($x)")
    case FltSqrt(x) if hw => delay(lhs, rhs, src"Number.sqrt($x)")
    case Mux(sel, a, b) if hw => delay(lhs, rhs, src"if ($sel.value) $a else $b")


    case FixAbs(x) => emit(src"val $lhs = if ($x < 0) -$x else $x")
    case FltAbs(x)  => emit(src"val $lhs = if ($x < 0) -$x else $x")
    case FltLog(x)  => x.tp match {
      case DoubleType() => emit(src"val $lhs = Math.log($x)")
      case FloatType()  => emit(src"val $lhs = Math.log($x.toDouble).toFloat")
    }
    case FltExp(x)  => x.tp match {
      case DoubleType() => emit(src"val $lhs = Math.exp($x)")
      case FloatType()  => emit(src"val $lhs = Math.exp($x.toDouble).toFloat")
    }
    case FltSqrt(x) => x.tp match {
      case DoubleType() => emit(src"val $lhs = Math.sqrt($x)")
      case FloatType()  => emit(src"val $lhs = Math.sqrt($x.toDouble).toFloat")
    }

    case Mux(sel, a, b) => emit(src"val $lhs = if ($sel) $a else $b")

    // Assumes < and > are defined on runtime type...
    case Min(a, b) => emit(src"val $lhs = if ($a < $b) $a else $b")
    case Max(a, b) => emit(src"val $lhs = if ($a > $b) $a else $b")

    case _ => super.emitNode(lhs, rhs)
  }

}