package spatial.codegen.scalagen

import argon.codegen.scalagen.ScalaCodegen
import spatial.api.ShiftRegExp

trait ScalaGenShiftReg extends ScalaCodegen {
  val IR: ShiftRegExp
  import IR._

  override protected def remap(tp: Staged[_]): String = tp match {
    case tp: ShiftRegType[_] => src"Array[${tp.child}]"
    case _ => super.remap(tp)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case ShiftRegNew(_, init)    => emit(src"val $lhs = Array($init)")
    case ShiftRegRead(reg)    => emit(src"val $lhs = $reg.apply(0)")
    case ShiftRegWrite(reg,v,en) => emit(src"val $lhs = if ($en) $reg.update(0, $v)")
    case _ => super.emitNode(lhs, rhs)
  }

}
