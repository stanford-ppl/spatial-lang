package spatial.codegen.cppgen

import argon.codegen.cppgen.CppCodegen
import argon.core._
import spatial.aliases._
import spatial.metadata._
import spatial.nodes._
import spatial.SpatialConfig

trait CppGenReg extends CppCodegen {

  override def quote(s: Exp[_]): String = {
    if (SpatialConfig.enableNaming) {
      s match {
        case lhs: Sym[_] =>
          lhs match {
            case Def(ArgInNew(_))=> s"x${lhs.id}_argin"
            case Def(ArgOutNew(_)) => s"x${lhs.id}_argout"
            case Def(RegNew(_)) => s"""x${lhs.id}_${lhs.name.getOrElse("reg")}"""
            case Def(RegRead(reg:Sym[_])) => s"x${lhs.id}_readx${reg.id}"
            case Def(RegWrite(reg:Sym[_],_,_)) => s"x${lhs.id}_writex${reg.id}"
            case _ => super.quote(s)
          }
        case _ => super.quote(s)
      }
    } else {
      super.quote(s)
    }
  } 

  override protected def remap(tp: Type[_]): String = tp match {
    case tp: RegType[_] => src"${tp.typeArguments.head}"
    case _ => super.remap(tp)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case ArgInNew(init)  => 
      argIns = argIns :+ lhs
      emit(src"${lhs.tp} $lhs = 0; // Initialize cpp argin ???")
    case ArgOutNew(init) => 
      argOuts = argOuts :+ lhs
      emit(src"//${lhs.tp}* $lhs = new int32_t {0}; // Initialize cpp argout ???")
    case HostIONew(init) => 
      argIOs = argIOs :+ lhs.asInstanceOf[Sym[Reg[_]]]
      emit(src"${lhs.tp} $lhs = 0; // Initialize cpp argout ???")
    case RegRead(reg)    => 
      emit(src"${lhs.tp} $lhs = $reg;")
    case RegWrite(reg,v,en) => 
      emit(src"// $lhs $reg $v $en reg write")
    case _ => super.emitNode(lhs, rhs)
  }

  override protected def emitFileFooter() {
    withStream(getStream("DE1SoC", "h")) {
      argIOs.foreach{a =>
        emit(src"""#define ${a.name.getOrElse("ERROR: Unnamed IO")} ${2+argMapping(a)._2}""")
      }
    }
    super.emitFileFooter()
  }
}
