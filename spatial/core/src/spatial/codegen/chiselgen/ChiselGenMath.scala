package spatial.codegen.chiselgen

import argon.codegen.chiselgen.ChiselCodegen
import argon.ops.{FixPtExp, FltPtExp}
import spatial.api.MathExp
import spatial.{SpatialConfig, SpatialExp}
import spatial.analysis.SpatialMetadataExp

trait ChiselGenMath extends ChiselCodegen {
  val IR: SpatialExp
  import IR._

  override def quote(s: Exp[_]): String = {
    if (SpatialConfig.enableNaming) {
      s match {
        case lhs: Sym[_] =>
          lhs match {
            case Def(FixRandom(x)) => s"x${lhs.id}_fixrnd"
            case Def(FixNeg(x:Exp[_]))  => s"""x${lhs.id}_${nameOf(lhs).getOrElse(s"neg${quoteOperand(x)}")}"""
            case Def(FixAdd(x:Exp[_],y:Exp[_]))  => s"""x${lhs.id}_${nameOf(lhs).getOrElse("sum")}"""
            case Def(FixSub(x:Exp[_],y:Exp[_]))  => s"""x${lhs.id}_${nameOf(lhs).getOrElse("sub")}"""
            case Def(FixDiv(x:Exp[_],y:Exp[_]))  => s"""x${lhs.id}_${nameOf(lhs).getOrElse("div")}"""
            case Def(FixMul(x:Exp[_],y:Exp[_]))  => s"""x${lhs.id}_${nameOf(lhs).getOrElse("mul")}"""
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
    case FixAbs(x)  => emit(src"val $lhs = Mux(${x} < 0.U, -$x, $x)")

    case FltAbs(x)  => emit(src"val $lhs = Mux(${x} < 0.U, -$x, $x)")
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

    case FltPow(x,y) => if (emitEn) throw new Exception("Pow not implemented in hardware yet!")
    case FixFloor(x) => emit(src"val $lhs = Utils.floor($x)")
    case FixCeil(x) => emit(src"val $lhs = Utils.ceil($x)")

    case FltSin(x) =>
      throw new TrigInAccelException(lhs)
    case FltCos(x) =>
      throw new TrigInAccelException(lhs)
    case FltTan(x) =>
      throw new TrigInAccelException(lhs)
    case FltSinh(x) =>
      throw new TrigInAccelException(lhs)
    case FltCosh(x) =>
      throw new TrigInAccelException(lhs)
    case FltTanh(x) =>
      throw new TrigInAccelException(lhs)
    case FltAsin(x) =>
      throw new TrigInAccelException(lhs)
    case FltAcos(x) =>
      throw new TrigInAccelException(lhs)
    case FltAtan(x) =>
      throw new TrigInAccelException(lhs)

    case Mux(sel, a, b) => 
      lhs.tp match { 
        case FixPtType(s,d,f) => 
          emitGlobalWire(s"""val ${quote(lhs)} = Wire(new FixedPoint($s,$d,$f))""")
        case _ =>
          emitGlobalWire(s"""val ${quote(lhs)} = Wire(UInt(${bitWidth(lhs.tp)}.W))""")
      }
      emit(src"${lhs}.r := Mux(($sel), ${a}.r, ${b}.r)")

    // Assumes < and > are defined on runtime type...
    case Min(a, b) => emit(src"val $lhs = Mux(($a < $b), $a, $b)")
    case Max(a, b) => emit(src"val $lhs = Mux(($a > $b), $a, $b)")

    case _ => super.emitNode(lhs, rhs)
  }

}