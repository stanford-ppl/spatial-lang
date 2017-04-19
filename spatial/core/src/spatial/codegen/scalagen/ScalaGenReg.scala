package spatial.codegen.scalagen

import argon.codegen.scalagen.ScalaCodegen
import argon.ops.FixPtExp
import spatial.SpatialExp
import spatial.api.RegExp

trait ScalaGenReg extends ScalaCodegen with ScalaGenMemories {
  val IR: SpatialExp
  import IR._

  override protected def remap(tp: Type[_]): String = tp match {
    case tp: RegType[_] => src"Array[${tp.child}]"
    case _ => super.remap(tp)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case op@ArgInNew(init)  => emit(src"val $lhs = ${op.mR}($init)")
    case op@ArgOutNew(init) => emit(src"val $lhs = ${op.mR}($init)")
    case op@HostIONew(init) => emit(src"val $lhs = ${op.mR}($init)")
    case op@RegNew(init) => if (enableMemGen) emit(src"val $lhs = ${op.mR}($init)")
    case RegRead(reg)    => emit(src"val $lhs = $reg.apply(0)")
    case RegWrite(reg,v,en) => emit(src"val $lhs = if ($en) $reg.update(0, $v)")
    case _ => super.emitNode(lhs, rhs)
  }

}
