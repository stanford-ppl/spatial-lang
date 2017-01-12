package spatial.codegen.scalagen

import argon.codegen.scalagen.ScalaCodegen
import spatial.spec.RegExp

trait ScalaGenReg extends ScalaCodegen {
  val IR: RegExp
  import IR._

  override protected def remap(tp: Staged[_]): String = tp match {
    case tp: RegType[_] => src"Array[${tp.typeArguments.head}]"
    case _ => super.remap(tp)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case ArgInNew(init)  => src"val $lhs = Array($init)"
    case ArgOutNew(init) => src"val $lhs = Array($init)"
    case RegNew(init)    => src"val $lhs = Array($init)"
    case RegRead(reg)    => src"val $lhs = $reg.apply(0)"
    case RegWrite(reg,v,en) => src"val $lhs = if ($en) $reg.update(0, $v)"
    case _ => super.emitNode(lhs, rhs)
  }

}
