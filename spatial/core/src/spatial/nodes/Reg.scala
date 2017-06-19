package spatial.nodes

import argon.core._
import forge._
import spatial.aliases._

case class RegType[T:Bits](child: Type[T]) extends Type[Reg[T]] {
  override def wrapped(x: Exp[Reg[T]]) = new Reg(x)(child,bits[T])
  override def typeArguments = List(child)
  override def stagedClass = classOf[Reg[T]]
  override def isPrimitive = false
}

class RegIsMemory[T:Type:Bits] extends Mem[T, Reg] {
  @api def load(mem: Reg[T], is: Seq[Index], en: Bit): T = mem.value
  @api def store(mem: Reg[T], is: Seq[Index], data: T, en: Bit): MUnit = MUnit(Reg.write(mem.s, data.s, en.s))
  @api def iterators(mem: Reg[T]): Seq[Counter] = Seq(Counter(0, 1, 1, 1))
}

/** IR Nodes **/
case class ArgInNew[T:Type:Bits](init: Exp[T]) extends Alloc[Reg[T]] {
  def mirror(f:Tx) = ArgIn.alloc[T](f(init))
  val mT = typ[T]
  val bT = bits[T]
}
case class ArgOutNew[T:Type:Bits](init: Exp[T]) extends Alloc[Reg[T]] {
  def mirror(f:Tx) = ArgOut.alloc[T](f(init))
  val mT = typ[T]
  val bT = bits[T]
}
case class RegNew[T:Type:Bits](init: Exp[T]) extends Alloc[Reg[T]] {
  def mirror(f:Tx) = Reg.alloc[T](f(init))
  val mT = typ[T]
  val bT = bits[T]
}
case class HostIONew[T:Type:Bits](init: Exp[T]) extends Alloc[Reg[T]] {
  def mirror(f:Tx) = HostIO.alloc[T](f(init))
  val mT = typ[T]
  val bT = bits[T]
}

case class RegRead[T:Type:Bits](reg: Exp[Reg[T]]) extends Op[T] {
  def mirror(f:Tx) = Reg.read(f(reg))
  val mT = typ[T]
  val bT = bits[T]
  override def aliases = Nil
}
case class RegWrite[T:Type:Bits](reg: Exp[Reg[T]], data: Exp[T], en: Exp[Bit]) extends EnabledOp[MUnit](en) {
  def mirror(f:Tx) = Reg.write(f(reg),f(data), f(en))
  val mT = typ[T]
  val bT = bits[T]
}
case class RegReset[T:Type:Bits](reg: Exp[Reg[T]], en: Exp[Bit]) extends EnabledOp[MUnit](en) {
  def mirror(f:Tx) = Reg.reset(f(reg), f(en))
  val mT = typ[T]
  val bT = bits[T]
}
