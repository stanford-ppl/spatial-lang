package spatial.codegen.scalagen

import argon.core._
import spatial.aliases._
import spatial.nodes._

trait ScalaGenHostTransfer extends ScalaGenMemories {

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case SetArg(reg, v) => emit(src"val $lhs = $reg.update(0, $v)")
    case GetArg(reg)    => emit(src"val $lhs = $reg.apply(0)")
    case op@SetMem(dram, data) =>
      open(src"val $lhs = {")
        open(src"for (i <- 0 until $data.length) {")
          oobUpdate(op.mT,dram,lhs,Nil){ oobApply(op.mT,data,lhs,Nil){ emit(src"$dram(i) = $data(i)") } }
        close("}")
      close("}")

    case op@GetMem(dram, data) =>
      open(src"val $lhs = {")
        open(src"for (i <- 0 until $data.length) {")
          oobUpdate(op.mT,data,lhs,Nil){ oobApply(op.mT,dram,lhs,Nil){ emit(src"$data(i) = $dram(i)") } }
        close("}")
      close("}")

    case _ => super.emitNode(lhs, rhs)
  }

}
