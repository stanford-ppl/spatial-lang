package spatial.codegen.simgen

import spatial.SpatialExp
import spatial.lang.HostTransferExp

trait SimGenHostTransfer extends SimCodegen  {
  val IR: SpatialExp
  import IR._

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case SetArg(reg, v) => emit(src"val $lhs = $reg.update(0, $v)")
    case GetArg(reg)    => emit(src"val $lhs = $reg.apply(0)")
    //case SetMem(dram, data) => // TODO
    //case GetMem(dram, data) =>
    case _ => super.emitNode(lhs, rhs)
  }

}
