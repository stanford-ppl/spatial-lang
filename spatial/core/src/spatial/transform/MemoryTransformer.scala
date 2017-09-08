package spatial.transform

import argon.core._
import argon.transform.ForwardTransformer
import spatial.nodes._
import spatial.lang._
import spatial.metadata._
import spatial.utils._

case class MemoryTransformer(var IR: State) extends ForwardTransformer {
  override val name = "Memory Transformer"
  override val allowUnsafeSubst = true

  def allocSRAM[T:Type:Bits](dims: Seq[Exp[Index]])(implicit ctx: SrcCtx): Exp[SRAM[T]] = dims.length match {
    case 1 => SRAM.alloc[T,SRAM1](dims:_*)
    case 2 => SRAM.alloc[T,SRAM2](dims:_*)
    case 3 => SRAM.alloc[T,SRAM3](dims:_*)
    case 4 => SRAM.alloc[T,SRAM4](dims:_*)
    case 5 => SRAM.alloc[T,SRAM5](dims:_*)
    case _ =>
      // Should never occur, but if it does, should probably warn or give error here?
      implicit val sramType: Type[SRAM5[T]] = new Type[SRAM5[T]] with SRAMType[T] {
        override def wrapped(x: Exp[SRAM5[T]]) = throw new Exception("Cannot wrap internal SRAM type")
        override def typeArguments = List(child)
        override def stagedClass = classOf[SRAM5[T]]
        override def child: Type[T] = typ[T]
      }
      SRAM.alloc[T,SRAM5](dims:_*)(typ[T],bits[T],sramType,ctx,state)
  }
  def loadSRAM[T:Type:Bits](sram: Exp[SRAM[_]], dims: Seq[Exp[Index]], is: Seq[Exp[Index]], en: Exp[Bit])(implicit ctx: SrcCtx): Exp[T] = {
    SRAM.load(sram.asInstanceOf[Exp[SRAM[T]]],dims,is,FixPt.int32s(0),en)
  }

  override def transform[T:Type](lhs: Sym[T], rhs: Op[T])(implicit ctx: SrcCtx): Exp[T] = rhs match {
    case op @ LUTNew(dims, elems) =>
      val sram = allocSRAM(dims.map{d => int32s(d)})(op.mT,op.bT,ctx)
      initialDataOf(sram) = elems
      transferMetadata(lhs, sram)
      sram.asInstanceOf[Exp[T]] // Actually a lie, but its ok

    case op @ LUTLoad(lut, inds, en) =>
      val sram = f(lut).asInstanceOf[Exp[SRAM[_]]]
      val dims = stagedDimsOf(sram)
      val lhs2 = loadSRAM(sram,dims,f(inds),f(en))(op.mT,op.bT,ctx)
      transferMetadata(lhs, lhs2)
      lhs2

    case _ => super.transform(lhs, rhs)
  }
}
