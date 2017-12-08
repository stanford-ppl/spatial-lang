package spatial.codegen.scalagen

import argon.core._
import spatial.aliases._
import spatial.banking._
import spatial.nodes._
import spatial.utils._

trait ScalaGenRegFile extends ScalaGenMemories {

  override protected def remap(tp: Type[_]): String = tp match {
    case tp: RegFileType[_] => src"ShiftableMemory[${tp.child}]"
    case _ => super.remap(tp)
  }

  private def shiftIn[T:Type:Bits](lhs: Exp[_], rf: Exp[RegFile[T]], inds: Seq[Exp[Index]], d: Int, data: Exp[_], isVec: Boolean, en: Exp[Bit])(implicit ctx: SrcCtx): Unit = {
    val len = if (isVec) lenOf(data) else 1
    val dims = stagedDimsOf(rf)
    val size = dims(d)
    val stride = (dims.drop(d+1).map(quote) :+ "1").mkString("*")
    val inst = instanceOf(rf)

    open(src"val $lhs = if ($en) {")
      emit(src"val ofs = ${flattenAddress(dims,inds,None)}")
      emit(src"val stride = $stride")
      open(src"for (j <- $size-1 to 0 by - 1) {")
        if (isVec) emit(src"if (j < $len) $rf.update(ofs+($len-1-j)*stride, $data(j)) else $rf.update(ofs + j*stride, $rf.apply(ofs + (j - $len)*stride))")
        else       emit(src"if (j < $len) $rf.update(ofs+j*stride, $data) else $rf.update(ofs + j*stride, $rf.apply(ofs + (j - $len)*stride))")
      close("}")
    close("}")
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case op@RegFileNew(_, inits) => emitBankedInitMem(lhs,inits)(op.mT)
    case _: RegFileLoad[_]  => throw new Exception(s"Cannot generate unbanked RegFile load.\n${str(lhs)}")
    case _: RegFileStore[_] => throw new Exception(s"Cannot generate unbanked RegFile store.\n${str(lhs)}")
    case RegFileReset(rf, en) => emit(src"val $lhs = if ($en) $rf.reset()")

    case RegFileShiftIn(rf,data,addr,en,axis) =>
      val ctx = s""""${lhs.ctx}""""
      emit(src"val $lhs = if ($en) $rf.shiftIn($ctx, Seq($addr), $axis, $data)")
    case RegFileVectorShiftIn(rf,data,addr,en,axis,len) =>
      val ctx = s""""${lhs.ctx}""""
      emit(src"val $lhs = if ($en) $rf.shiftInVec($ctx, Seq($addr), $axis, $data)")

    case op@BankedRegFileLoad(rf,bank,ofs,ens)       => emitBankedLoad(lhs,rf,bank,ofs,ens)(op.mT)
    case op@BankedRegFileStore(rf,data,bank,ofs,ens) => emitBankedStore(lhs,rf,data,bank,ofs,ens)(op.mT)
    case _ => super.emitNode(lhs, rhs)
  }

}
