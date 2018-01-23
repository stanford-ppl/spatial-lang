package spatial.codegen.scalagen

import argon.core._
import spatial.metadata._
import spatial.nodes._

trait ScalaGenSRAM extends ScalaGenMemories {

  override protected def remap(tp: Type[_]): String = tp match {
    case tp: SRAMType[_] => src"Ptr[BankedMemory[${tp.child}]]"
    case _ => super.remap(tp)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case op: SRAMNew[_,_] => emitBankedInitMem(lhs, initialDataOf.get(lhs))(mtyp(op.mT))
    case _: SRAMLoad[_]  => throw new Exception(s"Cannot generate unbanked SRAM load\n${str(lhs)}")
    case _: SRAMStore[_] => throw new Exception(s"Cannot generate unbanked SRAM store\n${str(lhs)}")

    case op@BankedSRAMLoad(sram,bank,ofs,ens)       => emitBankedLoad(lhs,sram,bank,ofs,ens)(op.mT)
    case op@BankedSRAMStore(sram,data,bank,ofs,ens) => emitBankedStore(lhs,sram,data,bank,ofs,ens)(op.mT)
    case _ => super.emitNode(lhs, rhs)
  }
}
