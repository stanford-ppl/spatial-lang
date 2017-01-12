package spatial.codegen.scalagen

import argon.codegen.scalagen.ScalaCodegen
import spatial.spec.HostTransferExp

trait ScalaGenHostTransfer extends ScalaCodegen {
  val IR: HostTransferExp
  import IR._

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case SetArg(reg, v) => emit(src"val $lhs = $reg.update(0, $v)")
    case GetArg(reg)    => emit(src"val $lhs = $reg.apply(0)")
    case SetMem(dram, data) => emit(src"val $lhs = System.arraycopy($data, 0, $dram, 0, $data.length)")
    case GetMem(dram, data) => emit(src"val $lhs = System.arraycopy($dram, 0, $data, 0, $dram.length)")
    case _ => super.emitNode(lhs, rhs)
  }

}
