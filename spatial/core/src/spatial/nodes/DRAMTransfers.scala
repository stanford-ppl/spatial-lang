package spatial.nodes

import argon.internals._
import spatial.compiler._

/** Abstract IR Nodes **/
case class DenseTransfer[T,C[T]](
  dram:   Exp[DRAM[T]],
  local:  Exp[C[T]],
  ofs:    Seq[Exp[Index]],
  lens:   Seq[Exp[Index]],
  units:  Seq[Boolean],
  p:      Const[Index],
  isLoad: Boolean,
  iters:  List[Bound[Index]]
)(implicit val mem: Mem[T,C], val mT: Type[T], val bT: Bits[T], val mC: Type[C[T]], mD: Type[DRAM[T]]) extends DRAMTransfer {

  def isStore = !isLoad

  def mirror(f:Tx): Exp[MUnit] = op_dense_transfer(f(dram),f(local),f(ofs),f(lens),units,p,isLoad,iters)

  override def inputs = dyns(dram, local) ++ dyns(ofs) ++ dyns(lens)
  override def binds  = iters
  override def aliases = Nil

  def expand(f:Tx)(implicit ctx: SrcCtx): Exp[MUnit] = {
    copy_dense(f(dram),f(local),f(ofs),f(lens),units,p,isLoad)(mT,bT,mem,mC,mD,ctx).s
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
)(implicit mD: Type[DRAM[T]]) extends DRAMTransfer {
  def isStore = !isLoad

  def mirror(f:Tx) = op_sparse_transfer(f(dram),f(local),f(addrs),f(size),p,isLoad,i)

  override def inputs = dyns(dram, local, addrs, size, p)
  override def binds = List(i)
  override def aliases = Nil
  val mT = typ[T]
  val bT = bits[T]

  def expand(f:Tx)(implicit ctx: SrcCtx): Exp[MUnit] = {
    copy_sparse(f(dram),f(local),f(addrs),f(size),p,isLoad)(mT,bT,mD,ctx).s
  }
}

/** Fringe IR Nodes **/
case class FringeDenseLoad[T:Type:Bits](
  dram:       Exp[DRAM[T]],
  cmdStream:  Exp[StreamOut[BurstCmd]],
  dataStream: Exp[StreamIn[T]]
) extends FringeNode[MUnit] {
  def mirror(f:Tx) = fringe_dense_load(f(dram),f(cmdStream),f(dataStream))
  val bT = bits[T]
  val mT = typ[T]
}

case class FringeDenseStore[T:Type:Bits](
  dram:       Exp[DRAM[T]],
  cmdStream:  Exp[StreamOut[BurstCmd]],
  dataStream: Exp[StreamOut[MTuple2[T,Bit]]],
  ackStream:  Exp[StreamIn[Bit]]
) extends FringeNode[MUnit] {
  def mirror(f:Tx) = fringe_dense_store(f(dram),f(cmdStream),f(dataStream),f(ackStream))
  val bT = bits[T]
  val mT = typ[T]
}

case class FringeSparseLoad[T:Type:Bits](
  dram:       Exp[DRAM[T]],
  addrStream: Exp[StreamOut[Int64]],
  dataStream: Exp[StreamIn[T]]
) extends FringeNode[MUnit] {
  def mirror(f:Tx) = fringe_sparse_load(f(dram),f(addrStream),f(dataStream))
  val bT = bits[T]
  val mT = typ[T]
}

case class FringeSparseStore[T:Type:Bits](
  dram:      Exp[DRAM[T]],
  cmdStream: Exp[StreamOut[MTuple2[T,Int64]]],
  ackStream: Exp[StreamIn[Bit]]
) extends FringeNode[MUnit] {
  def mirror(f:Tx) = fringe_sparse_store(f(dram),f(cmdStream),f(ackStream))
  val bT = bits[T]
  val mT = typ[T]
}