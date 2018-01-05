package spatial.codegen.scalagen

import argon.core._
import spatial.banking._
import spatial.nodes._

trait ScalaGenLUTs extends ScalaGenMemories {

  override protected def remap(tp: Type[_]): String = tp match {
    case tp: LUTType[_] => src"Array[Array[${tp.child}]]"
    case _ => super.remap(tp)
  }
  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case op@LUTNew(_,elems) => emitBankedInitMem(lhs,Some(elems))(op.mT)

    case _: LUTLoad[_] => throw new Exception(s"Cannot generate unbanked LUT load.\n${str(lhs)}")

    case op@BankedLUTLoad(lut,bank,ofs,ens) =>
      val banks = instanceOf(lut).nBanks
      open(src"val $lhs = {")
      bank.indices.foreach{i =>
        open(src"val a$i = {")
        oobBankedApply(op.mT,lut,lhs,bank(i),ofs(i)){
          val bankAddr = flattenConstDimsAddress(banks, bank(i))
          emit(src"""if (${ens(i)}) $lut.apply($bankAddr).apply(${ofs(i)}) else ${invalid(op.mT)}""")
        }
        close("}")
      }
      emit(src"Array[${op.mT}](" + bank.indices.map{i => src"a$i"}.mkString(", ") + ")")
      close("}")

    case _ => super.emitNode(lhs, rhs)
  }

}
