package spatial.codegen.scalagen

import argon.core._
import spatial.aliases._
import spatial.nodes._
import spatial.utils._

trait ScalaGenSRAM extends ScalaGenMemories {

  override protected def remap(tp: Type[_]): String = tp match {
    case tp: SRAMType[_] => src"Array[${tp.child}]"
    case _ => super.remap(tp)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case op@SRAMNew(dims) => emitMem(lhs, src"""$lhs = Array.fill(${dims.map(quote).mkString("*")})(${invalid(op.mT)})""")
    case op@SRAMLoad(sram, dims, is, ofs, en) =>
      open(src"val $lhs = {")
        oobApply(op.mT,sram,lhs,is){ emit(src"""if ($en) $sram.apply(${flattenAddress(dims,is,Some(ofs))}) else ${invalid(op.mT)}""") }
      close("}")

    case op@SRAMStore(sram, dims, is, ofs, v, en) =>
      open(src"val $lhs = {")
        oobUpdate(op.mT,sram,lhs,is){ emit(src"if ($en) $sram.update(${flattenAddress(dims,is,Some(ofs))}, $v)") }
      close("}")

    case op@ParSRAMLoad(sram,inds,ens) =>
      val dims = stagedDimsOf(sram)
      open(src"val $lhs = {")
      inds.indices.foreach{i =>
        open(src"val a$i = {")
        oobApply(op.mT,sram,lhs,inds(i)){ emit(src"""if (${ens(i)}) $sram.apply(${flattenAddress(dims, inds(i))}) else ${invalid(op.mT)}""") }
        close("}")
      }
      emit(src"Array[${op.mT}](" + inds.indices.map{i => src"a$i"}.mkString(", ") + ")")
      close("}")

    case op@ParSRAMStore(sram,inds,data,ens) =>
      val dims = stagedDimsOf(sram)
      open(src"val $lhs = {")
      inds.indices.foreach{i =>
        oobUpdate(op.mT, sram, lhs,inds(i)){ emit(src"if (${ens(i)}) $sram.update(${flattenAddress(dims, inds(i))}, ${data(i)})") }
      }
      close("}")

    case _ => super.emitNode(lhs, rhs)
  }
}
