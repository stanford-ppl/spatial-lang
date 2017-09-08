package spatial.nodes

import argon.core._
import spatial.aliases._

/** IR Nodes **/
case class SetArg[T:Type:Bits](reg: Exp[Reg[T]], value: Exp[T]) extends Op[MUnit] {
  def mirror(f:Tx) = HostTransferOps.set_arg(f(reg),f(value))
  val mT = typ[T]
  val bT = bits[T]
}

case class GetArg[T:Type:Bits](reg: Exp[Reg[T]]) extends Op[T] {
  def mirror(f:Tx) = HostTransferOps.get_arg(f(reg))
  val mT = typ[T]
  val bT = bits[T]
}

case class SetMem[T:Type:Bits](dram: Exp[DRAM[T]], data: Exp[MArray[T]]) extends Op[MUnit] {
  def mirror(f:Tx) = HostTransferOps.set_mem(f(dram),f(data))
  override def aliases = Nil
  val mT = typ[T]
}

case class GetMem[T:Type:Bits](dram: Exp[DRAM[T]], array: Exp[MArray[T]]) extends Op[MUnit] {
  def mirror(f:Tx) = HostTransferOps.get_mem(f(dram),f(array))
  override def aliases = Nil
  val mT = typ[T]
}
