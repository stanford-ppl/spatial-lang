package spatial.codegen.scalagen

import argon.ops.FixPtExp
import spatial.api.SRAMExp

trait ScalaGenSRAM extends ScalaGenMemories {
  val IR: SRAMExp with FixPtExp
  import IR._

  override protected def remap(tp: Type[_]): String = tp match {
    case tp: SRAMType[_] => src"Array[${tp.child}]"
    case _ => super.remap(tp)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case op@SRAMNew(dims) => if (enableMemGen) emit(src"""val $lhs = Array.fill(${dims.map(quote).mkString("*")})(${invalid(op.mT)})""")
    case op@SRAMLoad(sram, dims, is, ofs, en) =>
      open(src"val $lhs = {")
        oobApply(op.mT,sram,lhs,is){ emit(src"""if ($en) $sram.apply(${flattenAddress(dims,is,Some(ofs))}) else ${invalid(op.mT)}""") }
      close("}")

    case op@SRAMStore(sram, dims, is, ofs, v, en) =>
      open(src"val $lhs = {")
        oobUpdate(op.mT,sram,lhs,is){ emit(src"if ($en) $sram.update(${flattenAddress(dims,is,Some(ofs))}, $v)") }
      close("}")

    case _ => super.emitNode(lhs, rhs)
  }
}
