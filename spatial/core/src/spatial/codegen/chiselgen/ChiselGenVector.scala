package spatial.codegen.chiselgen

import argon.codegen.chiselgen.ChiselCodegen
import spatial.api.VectorExp
import spatial.{SpatialConfig, SpatialExp}

trait ChiselGenVector extends ChiselGenSRAM {
  val IR: SpatialExp
  import IR._

  override def quote(s: Exp[_]): String = {
    if (SpatialConfig.enableNaming) {
      s match {
        case lhs: Sym[_] =>
          lhs match {
            case Def(ListVector(_))=> s"x${lhs.id}_vecified"
            case Def(VectorApply(_,i:Int)) => s"x${lhs.id}_elem${i}"
            case Def(VectorSlice(_,s:Int,e:Int)) => s"x${lhs.id}_slice${s}to${e}"
            case _ => super.quote(s)
          }
        case _ => super.quote(s)
      }
    } else {
      super.quote(s)
    }
  } 

  override protected def remap(tp: Type[_]): String = tp match {
    case tp: VectorType[_] => src"Array[${tp.child}]"
    case _ => super.remap(tp)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case ListVector(elems)      => emit(src"val $lhs = Array(" + elems.map(quote).mkString(",") + ")")
    case VectorApply(vector, i) => emit(src"val $lhs = $vector.apply($i)")
    case VectorSlice(vector, start, end) => emit(src"val $lhs = $vector($start, $end)")

    // TODO: Use memcpy for these data <-> bits operations
    /*
      uint32_t dword = 0x4D91A2B4;
      float f;
      memcpy(&f, &dw, 4);
    */
    case e@DataAsBits(a) => e.mT match {
      case FltPtType(_,_)   => throw new Exception("Bit-wise operations not supported on floating point values yet")
      case FixPtType(s,d,f) => emit(src"val $lhs = ${a}.r")
      case BoolType()       => emit(src"val $lhs = ${a}.r")
    }

    case BitsAsData(v,mT) => mT match {
      case FltPtType(_,_)   => throw new Exception("Bit-wise operations not supported on floating point values yet")
      case FixPtType(s,i,f) => emit(src"val $lhs = Wire(${newWire(lhs.tp)})"); emit(src"${lhs}.r := ${v}.r")
      case BoolType()       => emit(src"val $lhs = $v // TODO: Need to do something fancy here?")
    }

    case _ => super.emitNode(lhs, rhs)
  }
}