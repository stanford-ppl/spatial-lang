package spatial.codegen.chiselgen

import argon.codegen.chiselgen.ChiselCodegen
import spatial.api.ShiftRegExp
import spatial.{SpatialConfig, SpatialExp}
import spatial.analysis.SpatialMetadataExp

trait ChiselGenRetiming extends ChiselCodegen {
  val IR: SpatialExp
  import IR._

  override def quote(s: Exp[_]): String = {
    if (SpatialConfig.enableNaming) {
      s match {
        case lhs: Sym[_] =>
          lhs match {
            case Def(ShiftRegNew(size, init)) => s"x${lhs.id}_delay$size"
            case Def(ShiftRegRead(sr)) => s"x${lhs.id}_srrd${quoteOperand2(sr)}"
            case _ => super.quote(s)
          }
        case _ => super.quote(s)
      }
    } else {
      super.quote(s)
    }
  } 

  def quoteOperand2(s: Exp[_]): String = s match { // TODO: Unify this with the one in math
    case ss:Sym[_] => s"x${ss.id}"
    case Const(xx:Exp[_]) => s"${boundOf(xx).toInt}"
    case _ => "unk"
  }


  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {

    case ShiftRegNew(size, init) => 
      emitGlobalRetiming(src"val $lhs = delayreg $size $init")

    case ShiftRegRead(shiftReg) => 
      emitGlobalRetiming(src"val $lhs = read $shiftReg")

    case ShiftRegWrite(shiftReg, data, en) => 
      emitGlobalRetiming(src"val $lhs = write $shiftReg $data $en")

    case _ =>
      super.emitNode(lhs, rhs)
  }

}