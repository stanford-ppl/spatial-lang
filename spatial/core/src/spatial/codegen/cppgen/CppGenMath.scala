package spatial.codegen.cppgen

import argon.codegen.cppgen.CppCodegen
import argon.ops.{FixPtExp, FltPtExp}
import spatial.api.MathExp
import spatial.{SpatialConfig, SpatialExp}
import spatial.analysis.SpatialMetadataExp

trait CppGenMath extends CppCodegen {
  val IR: SpatialExp
  import IR._

  override def quote(s: Exp[_]): String = {
    if (SpatialConfig.enableNaming) {
      s match {
        case lhs: Sym[_] =>
          lhs match {
            case Def(FixRandom(x))=> s"x${lhs.id}_fixrnd"
            case Def(FixNeg(x:Exp[_])) => s"x${lhs.id}_neg${quoteOperand(x)}"
            case Def(FixAdd(x:Exp[_],y:Exp[_])) => s"x${lhs.id}_sum${quoteOperand(x)}_${quoteOperand(y)}"
            case _ => super.quote(s)
          }
        case _ => super.quote(s)
      }
    } else {
      super.quote(s)
    }
  } 

  def quoteOperand(s: Exp[_]): String = s match {
    case ss:Sym[_] => s"x${ss.id}"
    case Const(xx:Exp[_]) => s"${boundOf(xx).toInt}"
    case _ => "unk"
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case FixAbs(x)  => emit(src"${lhs.tp} $lhs = abs($x);")

    case FltAbs(x)  => emit(src"${lhs.tp} $lhs = fabs($x);")
    case FltLog(x)  => x.tp match {
      case DoubleType() => emit(src"${lhs.tp} $lhs = Math.log($x)")
      case FloatType()  => emit(src"${lhs.tp} $lhs = Math.log($x.toDouble).toFloat")
    }
    case FltExp(x)  => x.tp match {
      case DoubleType() => emit(src"${lhs.tp} $lhs = Math.exp($x)")
      case FloatType()  => emit(src"${lhs.tp} $lhs = Math.exp($x.toDouble).toFloat")
    }
    case FltSqrt(x) => x.tp match {
      case DoubleType() => emit(src"${lhs.tp} $lhs = Math.sqrt($x)")
      case FloatType()  => emit(src"${lhs.tp} $lhs = Math.sqrt($x.toDouble).toFloat")
    }

    case Mux(sel, a, b) => 
      emit(src"${lhs.tp} $lhs;")
      emit(src"if ($sel){ $lhs = $a; } else { $lhs = $b; }")

    // Assumes < and > are defined on runtime type...
    case Min(a, b) => emit(src"${lhs.tp} $lhs = std::min($a,$b);")
    case Max(a, b) => emit(src"${lhs.tp} $lhs = std::max($a,$b);")

    case _ => super.emitNode(lhs, rhs)
  }

}