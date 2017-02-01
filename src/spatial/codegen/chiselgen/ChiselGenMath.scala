package spatial.codegen.chiselgen

import argon.codegen.chiselgen.ChiselCodegen
import spatial.api.MathExp
import spatial.SpatialConfig

trait ChiselGenMath extends ChiselCodegen {
  val IR: MathExp
  import IR._

  override def quote(s: Exp[_]): String = {
    if (SpatialConfig.enableNaming) {
      s match {
        case lhs: Sym[_] =>
          val Op(rhs) = lhs
          rhs match {
            case FixRandom(x)=> s"x${lhs.id}_fixrnd"
            case FixNeg(x:Exp[_]) => s"x${lhs.id}_neg${quoteOperand(x)}"
            case FixAdd(x:Exp[_],y:Exp[_]) => s"x${lhs.id}_sum${quoteOperand(x)}_${quoteOperand(y)}"
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
    case Const(xx:Int) => s"$xx"
    case _ => "unk"
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case FixAbs(x)  => emit(src"val $lhs = if ($x < 0) -$x else $x")

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