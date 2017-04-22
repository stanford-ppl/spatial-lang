package spatial.codegen.chiselgen

import argon.codegen.chiselgen.ChiselCodegen
import spatial.api.ShiftRegExp
import spatial.{SpatialConfig, SpatialExp}
import spatial.analysis.SpatialMetadataExp

trait ChiselGenRetiming extends ChiselGenSRAM {
  val IR: SpatialExp
  import IR._

  override def quote(s: Exp[_]): String = {
    if (SpatialConfig.enableNaming) {
      s match {
        case lhs: Sym[_] =>
          lhs match {
            case Def(ShiftRegNew(size, init)) => s"x${lhs.id}_retimer$size"
            case Def(ShiftRegRead(sr)) => s"x${lhs.id}_retimer${quoteOperand2(sr)}"
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
      emitGlobalRetiming(src"val $lhs = Module(new Retimer($size, ${bitWidth(lhs.tp.typeArguments.head)}))")
      init match {
        case Def(ListVector(_)) => 
          emitGlobalRetiming(src"// ${lhs} init is ${init}, must emit in ${controllerStack.head}")
          emit(src"${lhs}.io.input.init := ${init}.raw")
        case _ => 
          emitGlobalRetiming(src"${lhs}.io.input.init := ${init}.raw")
      }

    case ShiftRegRead(shiftReg) => 
      emit(src"val $lhs = Wire(${newWire(lhs.tp)})")
      lhs.tp match {
        case a:VectorType[_] =>
          emit(src"(0 until ${a.width}).foreach{i => ${lhs}(i).raw := ${shiftReg}.io.output.data(${bitWidth(lhs.tp)/a.width}*(i+1)-1, ${bitWidth(lhs.tp)/a.width}*i)}")
        case _ =>
          emit(src"$lhs.raw := ${shiftReg}.io.output.data")
      }
      

    case ShiftRegWrite(shiftReg, data, en) => 
      emit(src"${shiftReg}.io.input.data := ${data}.raw")
      emit(src"${shiftReg}.io.input.en := $en")

    case _ =>
      super.emitNode(lhs, rhs)
  }

}