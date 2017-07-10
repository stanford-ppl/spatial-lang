package spatial.nodes

import argon.core._
import spatial.aliases._

trait DRAMType[T] {
  def child: Type[T]
  def isPrimitive = false
}
case class DRAM1Type[T:Bits](child: Type[T]) extends Type[DRAM1[T]] with DRAMType[T] {
  override def wrapped(x: Exp[DRAM1[T]]) = new DRAM1(x)(child,bits[T])
  override def typeArguments = List(child)
  override def stagedClass = classOf[DRAM1[T]]
}
case class DRAM2Type[T:Bits](child: Type[T]) extends Type[DRAM2[T]] with DRAMType[T] {
  override def wrapped(x: Exp[DRAM2[T]]) = new DRAM2(x)(child,bits[T])
  override def typeArguments = List(child)
  override def stagedClass = classOf[DRAM2[T]]
}
case class DRAM3Type[T:Bits](child: Type[T]) extends Type[DRAM3[T]] with DRAMType[T] {
  override def wrapped(x: Exp[DRAM3[T]]) = new DRAM3(x)(child,bits[T])
  override def typeArguments = List(child)
  override def stagedClass = classOf[DRAM3[T]]
}
case class DRAM4Type[T:Bits](child: Type[T]) extends Type[DRAM4[T]] with DRAMType[T] {
  override def wrapped(x: Exp[DRAM4[T]]) = new DRAM4(x)(child,bits[T])
  override def typeArguments = List(child)
  override def stagedClass = classOf[DRAM4[T]]
}
case class DRAM5Type[T:Bits](child: Type[T]) extends Type[DRAM5[T]] with DRAMType[T] {
  override def wrapped(x: Exp[DRAM5[T]]) = new DRAM5(x)(child,bits[T])
  override def typeArguments = List(child)
  override def stagedClass = classOf[DRAM5[T]]
}


/** IR Nodes **/
case class DRAMNew[T:Type:Bits,C[_]<:DRAM[_]](dims: Seq[Exp[Index]], zero: Exp[T])(implicit cT: Type[C[T]]) extends Alloc[C[T]] {
  def mirror(f:Tx) = DRAM.alloc[T,C](f(dims):_*)
  val bT = bits[T]
}

case class GetDRAMAddress[T:Type:Bits](dram: Exp[DRAM[T]]) extends Op[Int64] {
  def mirror(f:Tx) = DRAM.addr(f(dram))
}

