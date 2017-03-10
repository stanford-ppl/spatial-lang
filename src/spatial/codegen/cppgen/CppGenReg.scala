package spatial.codegen.cppgen

import argon.codegen.cppgen.CppCodegen
import spatial.api.RegExp
import spatial.SpatialConfig
import spatial.analysis.SpatialMetadataExp
import spatial.SpatialExp

trait CppGenReg extends CppCodegen {
  val IR: RegExp with SpatialExp
  import IR._


  override def quote(s: Exp[_]): String = {
    if (SpatialConfig.enableNaming) {
      s match {
        case lhs: Sym[_] =>
          lhs match {
            case Def(ArgInNew(_))=> s"x${lhs.id}_argin"
            case Def(ArgOutNew(_)) => s"x${lhs.id}_argout"
            case Def(RegNew(_)) => s"x${lhs.id}_reg"
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

  override protected def remap(tp: Staged[_]): String = tp match {
    case tp: RegType[_] => src"${tp.typeArguments.head}"
    case _ => super.remap(tp)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case ArgInNew(init)  => 
      emit(src"//${lhs.tp}* $lhs = new ${lhs.tp} {0}; // Initialize cpp argin ???")
    case ArgOutNew(init) => 
      emit(src"//int32_t* $lhs = new int32_t {0}; // Initialize cpp argout ???")
    case RegRead(reg)    => 
      emit(src"${lhs.tp} $lhs = $reg;")
    case RegWrite(reg,v,en) => 
      emit(src"// $lhs $reg $v $en reg write")
    case _ => super.emitNode(lhs, rhs)
  }

  override protected def emitFileFooter() {
    super.emitFileFooter()
  }
}
