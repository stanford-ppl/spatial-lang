package spatial.codegen.simgen

import argon.ops.MixedNumericExp

trait SimGenMixedNumeric extends SimCodegen with SimGenFixPt with SimGenFltPt {
  val IR: MixedNumericExp
  import IR._

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]) = rhs match {
    case FixPtToFltPt(x) if hw => lhs.tp match {
      case FltPtType(g,e) => delay(lhs,rhs,src"Number($x.value, $x.valid, FloatPoint($g,$e))")
    }
    case FltPtToFixPt(x) if hw => lhs.tp match {
      case FixPtType(s,i,f) => delay(lhs,rhs,src"Number($x.value, $x.valid, FixedPoint($s,$i,$f))")
    }

    case FixPtToFltPt(x) => lhs.tp match {
      case DoubleType() => emit(src"val $lhs = $x.toDouble")
      case FloatType()  => emit(src"val $lhs = $x.toFloat")
    }
    case FltPtToFixPt(x) => lhs.tp match {
      case IntType()  => emit(src"val $lhs = $x.toInt")
      case LongType() => emit(src"val $lhs = $x.toLong")
    }
    case _ => super.emitNode(lhs, rhs)
  }
}
