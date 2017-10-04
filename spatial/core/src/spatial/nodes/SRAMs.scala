package spatial.nodes

import argon.core._
import forge._
import spatial.aliases._
import spatial.utils._

/** Staged Type **/
trait SRAMType[T] {
  def child: Type[T]
  def isPrimitive = false
}
case class SRAM1Type[T:Bits](child: Type[T]) extends Type[SRAM1[T]] with SRAMType[T] {
  override def wrapped(x: Exp[SRAM1[T]]) = new SRAM1(x)(child,bits[T])
  override def typeArguments = List(child)
  override def stagedClass = classOf[SRAM1[T]]
}
case class SRAM2Type[T:Bits](child: Type[T]) extends Type[SRAM2[T]] with SRAMType[T] {
  override def wrapped(x: Exp[SRAM2[T]]) = new SRAM2(x)(child,bits[T])
  override def typeArguments = List(child)
  override def stagedClass = classOf[SRAM2[T]]
}
case class SRAM3Type[T:Bits](child: Type[T]) extends Type[SRAM3[T]] with SRAMType[T] {
  override def wrapped(x: Exp[SRAM3[T]]) = new SRAM3(x)(child,bits[T])
  override def typeArguments = List(child)
  override def stagedClass = classOf[SRAM3[T]]
}
case class SRAM4Type[T:Bits](child: Type[T]) extends Type[SRAM4[T]] with SRAMType[T] {
  override def wrapped(x: Exp[SRAM4[T]]) = new SRAM4(x)(child,bits[T])
  override def typeArguments = List(child)
  override def stagedClass = classOf[SRAM4[T]]
}
case class SRAM5Type[T:Bits](child: Type[T]) extends Type[SRAM5[T]] with SRAMType[T] {
  override def wrapped(x: Exp[SRAM5[T]]) = new SRAM5(x)(child,bits[T])
  override def typeArguments = List(child)
  override def stagedClass = classOf[SRAM5[T]]
}

class SRAMIsMemory[T:Type:Bits,C[T]](implicit mC: Type[C[T]], ev: C[T] <:< SRAM[T]) extends Mem[T,C] {
  @api def load(mem: C[T], is: Seq[Index], en: Bit): T = {
    wrap(SRAM.load(mem.s, stagedDimsOf(mem.s), unwrap(is), lift[Int,Index](0).s, en.s))
  }
  @api def store(mem: C[T], is: Seq[Index], data: T, en: Bit): MUnit = {
    wrap(SRAM.store[T](mem.s, stagedDimsOf(mem.s),unwrap(is),lift[Int,Index](0).s,data.s,en.s))
  }
  @api def iterators(mem: C[T]): Seq[Counter] = {
    stagedDimsOf(mem.s).zipWithIndex.map{case (d,i) =>
      val par = if (i == stagedDimsOf(mem.s).length-1) mem.p.getOrElse(lift(1)) else lift(1)
      Counter(0, wrap(d), 1, par)
    }
  }
  def par(mem: C[T]) = ev(mem).p
}


/** IR Nodes **/
case class SRAMNew[T:Type:Bits,C[_]<:SRAM[_]](dims: Seq[Exp[Index]])(implicit cT: Type[C[T]]) extends Alloc[C[T]] {
  def mirror(f:Tx) = SRAM.alloc[T,C](f(dims):_*)
  val mT = typ[T]
  val bT = bits[T]
}
case class SRAMLoad[T:Type:Bits](
  mem:  Exp[SRAM[T]],
  dims: Seq[Exp[Index]],
  is:   Seq[Exp[Index]],
  ofs:  Exp[Index],
  en:   Exp[Bit]
) extends LocalReaderOp[T](mem,addr=is,en=en) {
  def mirror(f:Tx) = SRAM.load(f(mem), f(dims), f(is), f(ofs), f(en))
  val mT = typ[T]
  val bT = bits[T]
}
case class SRAMStore[T:Type:Bits](
  mem:  Exp[SRAM[T]],
  dims: Seq[Exp[Index]],
  is:   Seq[Exp[Index]],
  ofs:  Exp[Index],
  data: Exp[T],
  en:   Exp[Bit]
) extends LocalWriterOp(mem,value=data,addr=is,en=en) {
  def mirror(f:Tx) = SRAM.store(f(mem), f(dims), f(is), f(ofs), f(data), f(en))
  val mT = typ[T]
  val bT = bits[T]
}


case class ParSRAMLoad[T:Type:Bits](
  sram: Exp[SRAM[T]],
  addr: Seq[Seq[Exp[Index]]],
  ens:  Seq[Exp[Bit]]
)(implicit val vT: Type[VectorN[T]]) extends ParLocalReaderOp[VectorN[T]](sram,addrs=addr,ens=ens) {
  def mirror(f:Tx) = SRAM.par_load(f(sram), addr.map{inds => f(inds)}, f(ens))
  val mT = typ[T]
  val bT = bits[T]
}

case class ParSRAMStore[T:Type:Bits](
  sram: Exp[SRAM[T]],
  addr: Seq[Seq[Exp[Index]]],
  data: Seq[Exp[T]],
  ens:  Seq[Exp[Bit]]
) extends ParLocalWriterOp(sram,values=data,addrs=addr,ens=ens) {
  def mirror(f:Tx) = SRAM.par_store(f(sram),addr.map{inds => f(inds)},f(data),f(ens))
  val mT = typ[T]
  val bT = bits[T]
}