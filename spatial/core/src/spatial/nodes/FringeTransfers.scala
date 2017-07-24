package spatial.nodes

import argon.core._
import spatial.aliases._

/** Fringe IR Nodes **/
case class FringeDenseLoad[T:Type:Bits](
  dram:       Exp[DRAM[T]],
  cmdStream:  Exp[StreamOut[BurstCmd]],
  dataStream: Exp[StreamIn[T]]
) extends FringeNode[MUnit] {
  def mirror(f:Tx) = FringeTransfers.fringe_dense_load(f(dram),f(cmdStream),f(dataStream))
  val bT = bits[T]
  val mT = typ[T]
}

case class FringeDenseStore[T:Type:Bits](
  dram:       Exp[DRAM[T]],
  cmdStream:  Exp[StreamOut[BurstCmd]],
  dataStream: Exp[StreamOut[MTuple2[T,Bit]]],
  ackStream:  Exp[StreamIn[Bit]]
) extends FringeNode[MUnit] {
  def mirror(f:Tx) = FringeTransfers.fringe_dense_store(f(dram),f(cmdStream),f(dataStream),f(ackStream))
  val bT = bits[T]
  val mT = typ[T]
}

case class FringeSparseLoad[T:Type:Bits](
  dram:       Exp[DRAM[T]],
  addrStream: Exp[StreamOut[Int64]],
  dataStream: Exp[StreamIn[T]]
) extends FringeNode[MUnit] {
  def mirror(f:Tx) = FringeTransfers.fringe_sparse_load(f(dram),f(addrStream),f(dataStream))
  val bT = bits[T]
  val mT = typ[T]
}

case class FringeSparseStore[T:Type:Bits](
  dram:      Exp[DRAM[T]],
  cmdStream: Exp[StreamOut[MTuple2[T,Int64]]],
  ackStream: Exp[StreamIn[Bit]]
) extends FringeNode[MUnit] {
  def mirror(f:Tx) = FringeTransfers.fringe_sparse_store(f(dram),f(cmdStream),f(ackStream))
  val bT = bits[T]
  val mT = typ[T]
}
