package spatial.nodes

import argon.core._
import argon.transform.Transformer
import forge._
import spatial.aliases._
import spatial.utils._

/** Abstract IR Nodes **/
case class DenseTransfer[T,C[T]](
  dram:   Exp[DRAM[T]],
  local:  Exp[C[T]],
  ofs:    Seq[Exp[Index]],
  lens:   Seq[Exp[Index]],
  strides:Seq[Exp[Index]],
  units:  Seq[Boolean],
  p:      Const[Index],
  isLoad: Boolean,
  iters:  List[Bound[Index]]
)(implicit val mem: Mem[T,C], val mT: Type[T], val bT: Bits[T], val mC: Type[C[T]], mD: Type[DRAM[T]])
  extends DRAMTransfer with LocalReader[MUnit] with LocalWriter[MUnit]
{
  def isStore: Boolean = !isLoad

  override def localReads: Seq[LocalRead] = if (isStore) LocalRead(local,addr=iters) else Nil
  override def localWrites: Seq[LocalWrite] = if (isLoad) LocalWrite(local,addr=iters) else Nil

  var isAlign = false

  def mirror(f:Tx): Exp[MUnit] = DRAMTransfers.op_dense_transfer(f(dram),f(local),f(ofs),f(lens),f(strides),units,p,isLoad,isAlign,iters)

  override def inputs = dyns(dram, local) ++ dyns(ofs) ++ dyns(lens)
  override def binds  = iters
  override def aliases = Nil

  @internal def expand(f:Tx): Exp[MUnit] = {
    DRAMTransfersInternal.copy_dense(f(dram),f(local),f(ofs),f(lens),f(strides),units,p,isLoad,isAlign)(mT,bT,mem,mC,mD,ctx,state).s
  }
}

case class SparseTransfer[T:Type:Bits](
  dram:   Exp[DRAM[T]],
  local:  Exp[SRAM1[T]],
  addrs:  Exp[SRAM1[Index]],
  size:   Exp[Index],
  p:      Const[Index],
  isLoad: Boolean,
  i:      Bound[Index]
)(implicit mD: Type[DRAM[T]]) extends DRAMTransfer with LocalReader[MUnit] with LocalWriter[MUnit] {
  def isStore: Boolean = !isLoad

  override def localReads: Seq[LocalRead] = if (isLoad) LocalRead(addrs) else LocalRead(local) ++ LocalRead(addrs)
  override def localWrites: Seq[LocalWrite] = if (isLoad) LocalWrite(local) else Nil

  def mirror(f:Tx) = DRAMTransfers.op_sparse_transfer(f(dram),f(local),f(addrs),f(size),p,isLoad,i)

  override def inputs = dyns(dram, local, addrs, size, p)
  override def binds = List(i)
  override def aliases = Nil
  val mT = typ[T]
  val bT = bits[T]

  @internal def expand(f:Tx): Exp[MUnit] = {
    DRAMTransfersInternal.copy_sparse(f(dram),f(local),f(addrs),f(size),p,isLoad)(mT,bT,mD,ctx,state).s
  }
}

/** More general version for FIFO and FILO - should be able to replace SparseTransfer when confirmed working **/
case class SparseTransferMem[T,C[T],A[_]](
  dram:   Exp[DRAM[T]],
  local:  Exp[C[T]],
  addrs:  Exp[A[Index]],
  size:   Exp[Index],
  p:      Const[Index],
  isLoad: Boolean,
  i:      Bound[Index]
)(implicit val mT: Type[T],
  val bT:   Bits[T],
  val memC: Mem[T,C],
  val mC:   Type[C[T]],
  val memA: Mem[Index,A],
  val mA:   Type[A[Index]],
  val mD:   Type[DRAM[T]]
) extends DRAMTransfer with LocalReader[MUnit] with LocalWriter[MUnit] {
  def isStore: Boolean = !isLoad

  override def localReads: Seq[LocalRead] = if (isLoad) LocalRead(addrs) else LocalRead(local) ++ LocalRead(addrs)
  override def localWrites: Seq[LocalWrite] = if (isLoad) LocalWrite(local) else Nil

  def mirror(f:Tx) = DRAMTransfers.op_sparse_transfer_mem(f(dram),f(local),f(addrs),f(size),p,isLoad,i)

  override def inputs = dyns(dram, local, addrs, size, p)
  override def binds = List(i)
  override def aliases = Nil

  @internal def expand(f:Tx): Exp[MUnit] = {
    DRAMTransfersInternal.copy_sparse_mem(f(dram), f(local), f(addrs), f(size), p, isLoad)(mT,bT,memC,mC,memA,mA,mD,ctx,state).s
  }
}
