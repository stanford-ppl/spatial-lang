package spatial.codegen.scalagen

import argon.core._
import spatial.banking._
import spatial.nodes._

trait ScalaGenLUTs extends ScalaGenMemories {

  override protected def remap(tp: Type[_]): String = tp match {
    case tp: LUTType[_] => src"Ptr[BankedMemory[${tp.child}]]"
    case _ => super.remap(tp)
  }
  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case op@LUTNew(_,elems) => emitBankedInitMem(lhs,Some(elems))(op.mT)

    case _: LUTLoad[_] => throw new Exception(s"Cannot generate unbanked LUT load.\n${str(lhs)}")

    case op@BankedLUTLoad(lut,bank,ofs,ens) => emitBankedLoad(lhs,lut,bank,ofs,ens)(op.mT)

    case _ => super.emitNode(lhs, rhs)
  }

}
