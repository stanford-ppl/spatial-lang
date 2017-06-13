package spatial.codegen.chiselgen

import argon.codegen.chiselgen.ChiselCodegen
import spatial.lang.ShiftRegExp
import spatial.metadata.SpatialMetadataExp
import spatial.{SpatialConfig, SpatialExp}
// import argon.ops.BoolExp

trait ChiselGenRetiming extends ChiselGenSRAM {
  val IR: SpatialExp
  import IR._


  override def quote(s: Exp[_]): String = {
    if (SpatialConfig.enableNaming) {
      s match {
        case lhs: Sym[_] =>
          lhs match {
            case Def(ValueDelay(size, data)) =>
              s"${quote(data)}_D${size}"
            case Def(ShiftRegNew(size, init)) => 
              if (size == 1) s"x${lhs.id}_latch"
              else s"x${lhs.id}_rt$size"
            case Def(ShiftRegRead(sr)) => s"x${lhs.id}_rt"
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

    case ValueDelay(size, data) => 
      // emit(src"""val $lhs = Utils.delay($data, $size)""")
      alphaconv_register(src"$lhs")
      lhs.tp match {
        case a:VectorType[_] =>
          logRetime(src"$lhs", src"$data", size, isVec = true, vecWidth = a.width, wire = newWire(lhs.tp), isBool = false/*although what about vec of bools?*/)
        case BoolType() =>
          logRetime(src"$lhs", src"$data", size, isVec = false, vecWidth = 0, wire = newWire(lhs.tp), isBool = true)
        case _ =>
          logRetime(src"$lhs", src"$data", size, isVec = false, vecWidth = 0, wire = newWire(lhs.tp), isBool = false)
      }

    case ShiftRegNew(size, init) => 
      emitGlobalRetiming(src"val $lhs = Module(new Retimer($size, ${bitWidth(lhs.tp.typeArguments.head)}))")
      init match {
        case Def(ListVector(_)) => 
          emitGlobalRetiming(src"// ${lhs} init is ${init}, must emit in ${controllerStack.head}")
          emit(src"${lhs}.io.input.init := ${init}.r")
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
      val parent = parentOf(lhs).get
      emit(src"${shiftReg}.io.input.data := ${data}.raw")
      emit(src"${shiftReg}.io.input.en := true.B //$en & ${parent}_datapath_en")

    case _ =>
      super.emitNode(lhs, rhs)
  }

  override protected def emitFileFooter() {

    emitGlobalWire(s"val max_retime = $maxretime")
    super.emitFileFooter()
  }


}