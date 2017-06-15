package spatial.codegen.scalagen

import argon.internals._
import spatial.compiler._
import spatial.nodes._

trait ScalaGenMath extends ScalaGenBits {

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case FixAbs(x)  => emit(src"val $lhs = if ($x < 0) -$x else $x")

    case FltAbs(x)  => emit(src"val $lhs = if ($x < 0) -$x else $x")

    case FltLog(x) => emit(src"val $lhs = Number.log($x)")
    case FltExp(x) => emit(src"val $lhs = Number.exp($x)")
    case FltSqrt(x) => emit(src"val $lhs = Number.sqrt($x)")

    case FltSin(x) => emit(src"val $lhs = Number.sin($x)")
    case FltCos(x) => emit(src"val $lhs = Number.cos($x)")
    case FltTan(x) => emit(src"val $lhs = Number.tan($x)")
    case FltSinh(x) => emit(src"val $lhs = Number.sinh($x)")
    case FltCosh(x) => emit(src"val $lhs = Number.cosh($x)")
    case FltTanh(x) => emit(src"val $lhs = Number.tanh($x)")
    case FltAsin(x) => emit(src"val $lhs = Number.asin($x)")
    case FltAcos(x) => emit(src"val $lhs = Number.acos($x)")
    case FltAtan(x) => emit(src"val $lhs = Number.atan($x)")
    case FixFloor(x) => emit(src"val $lhs = ${x}.floor()")
    case FixCeil(x) => emit(src"val $lhs = ${x}.ceil()")
    case FltPow(x,exp) => emit(src"val $lhs = Number.pow($x, $exp);")


    case Mux(sel, a, b) => emit(src"val $lhs = if ($sel) $a else $b")
    case op @ OneHotMux(selects,datas) =>
      open(src"val $lhs = {")
        selects.indices.foreach { i =>
          emit(src"""${if (i == 0) "if" else "else if"} (${selects(i)}) { ${datas(i)} }""")
        }
        emit(src"else { ${invalid(op.mT)} }")
      close("}")

    // Assumes < and > are defined on runtime type...
    case Min(a, b) => emit(src"val $lhs = if ($a < $b) $a else $b")
    case Max(a, b) => emit(src"val $lhs = if ($a > $b) $a else $b")

    case _ => super.emitNode(lhs, rhs)
  }

}