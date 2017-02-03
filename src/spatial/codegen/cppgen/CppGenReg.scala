package spatial.codegen.cppgen

import argon.codegen.cppgen.CppCodegen
import spatial.api.RegExp
import spatial.SpatialConfig
import spatial.analysis.SpatialMetadataExp

trait CppGenReg extends CppCodegen {
  val IR: RegExp with SpatialMetadataExp
  import IR._

  var argIns: List[Sym[Reg[_]]] = List()
  var argOuts: List[Sym[Reg[_]]] = List()

  override def quote(s: Exp[_]): String = {
    if (SpatialConfig.enableNaming) {
      s match {
        case lhs: Sym[_] =>
          val Op(rhs) = lhs
          rhs match {
            case ArgInNew(_)=> s"x${lhs.id}_argin"
            case ArgOutNew(_) => s"x${lhs.id}_argout"
            case RegNew(_) => s"x${lhs.id}_reg"
            case RegRead(reg:Sym[_]) => s"x${lhs.id}_readx${reg.id}"
            case RegWrite(reg:Sym[_],_,_) => s"x${lhs.id}_writex${reg.id}"
            case _ => super.quote(s)
          }
        case _ => super.quote(s)
      }
    } else {
      super.quote(s)
    }
  } 

  override protected def remap(tp: Staged[_]): String = tp match {
    case tp: RegType[_] => src"Array[${tp.typeArguments.head}]"
    case _ => super.remap(tp)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case ArgInNew(init)  => emit("// Do what?")
    case ArgOutNew(init) => emit("// Do what?")
    case _ => super.emitNode(lhs, rhs)
  }

  override protected def emitFileFooter() {
    super.emitFileFooter()
  }
}
