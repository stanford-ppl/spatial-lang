package spatial.codegen.simgen

import argon.ops.StringCastExp

trait SimGenStringCast extends SimCodegen {
  val IR: StringCastExp
  import IR._

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]) = rhs match {
    case StringToFltPt(x) => lhs.tp match {
      case DoubleType() => emit(src"val $lhs = $x.toDouble")
      case FloatType()  => emit(src"val $lhs = $x.toFloat")
    }

    case StringToFixPt(x) => lhs.tp match {
      case IntType()  => emit(src"val $lhs = $x.toInt")
      case LongType() => emit(src"val $lhs = $x.toLong")
    }

    case StringToBool(x) => emit(src"val $lhs = $x.toBoolean")

    case _ => super.emitNode(lhs, rhs)
  }
}
