package spatial.codegen.scalagen

import argon.core._
import spatial.aliases._
import spatial.nodes._

trait ScalaGenVarReg extends ScalaGenMemories {

  override protected def remap(tp: Type[_]): String = tp match {
    case tp: VarRegType[_] => src"Array[${tp.child}]"
    case _ => super.remap(tp)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case op@VarRegNew(init)    => emit(src"val $lhs = new ${op.mR}(1)") //emitMem(lhs, src"$lhs = new ${op.mR}(1)")
    case VarRegRead(reg)       => emit(src"val $lhs = $reg.apply(0)")
    case VarRegWrite(reg,v,en) => emit(src"val $lhs = if ($en) $reg.update(0, $v)")
    case _ => super.emitNode(lhs, rhs)
  }
}
