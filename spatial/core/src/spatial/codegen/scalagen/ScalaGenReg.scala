package spatial.codegen.scalagen

import argon.core._
import argon.codegen.scalagen.ScalaCodegen
import spatial.nodes._
import spatial.nodes._

trait ScalaGenReg extends ScalaCodegen with ScalaGenMemories {

  override protected def remap(tp: Type[_]): String = tp match {
    case tp: RegType[_] => src"Ptr[${tp.child}]"
    case _ => super.remap(tp)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case op@ArgInNew(init)  => emit(src"val $lhs = ${op.mR}().init($init)")
    case op@ArgOutNew(init) => emit(src"val $lhs = ${op.mR}().init($init)")
    case op@HostIONew(init) => emit(src"val $lhs = ${op.mR}().init($init)")
    case op@RegNew(init)    => emitMem(lhs, src"$init")
    case RegReset(reg, en) => 
      val init = reg match {
        case Def(RegNew(init)) => init
      }
      emit(src"val $lhs = if ($en) $reg.set($init)")
    case RegRead(reg)       => emit(src"val $lhs = $reg.value")
    case RegWrite(reg,v,en) => emit(src"val $lhs = if ($en) $reg.set($v)")
    case _ => super.emitNode(lhs, rhs)
  }

}
