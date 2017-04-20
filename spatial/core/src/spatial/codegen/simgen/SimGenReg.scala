package spatial.codegen.simgen

import argon.codegen.scalagen.ScalaCodegen
import spatial.SpatialExp
import spatial.api.RegExp

trait SimGenReg extends SimCodegen {
  val IR: SpatialExp
  import IR._

  override protected def remap(tp: Type[_]): String = tp match {
    case tp: RegType[_] => src"Array[${tp.child}]"
    case _ => super.remap(tp)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case ArgInNew(init)  => emit(src"val $lhs = Array($init)")
    case ArgOutNew(init) => emit(src"val $lhs = Array($init)")
    case RegNew(init)    => emit(src"val $lhs = Array($init)")
    case RegRead(reg)    => emit(src"val $lhs = $reg.apply(0)")
    case RegWrite(reg,v,en) => emit(src"val $lhs = if ($en) $reg.update(0, $v)")
    case _ => super.emitNode(lhs, rhs)
  }

}
