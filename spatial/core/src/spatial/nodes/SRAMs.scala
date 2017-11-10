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
  @api def load(mem: C[T], addr: Seq[Index], en: Bit): T = {
    wrap(SRAM.load(mem.s, unwrap(addr), en.s))
  }
  @api def store(mem: C[T], data: T, addr: Seq[Index], en: Bit): MUnit = {
    wrap(SRAM.store[T](mem.s, data.s, unwrap(addr), en.s))
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
  addr: Seq[Exp[Index]],
  en:   Exp[Bit]
) extends ReaderOp[T,T](mem,addr,en) {
  def mirror(f:Tx) = SRAM.load(f(mem), f(addr), f(en))
}

case class SRAMStore[T:Type:Bits](
  mem:  Exp[SRAM[T]],
  data: Exp[T],
  addr: Seq[Exp[Index]],
  en:   Exp[Bit]
) extends WriterOp[T](mem,data,addr,en) {
  def mirror(f:Tx) = SRAM.store(f(mem), f(data), f(addr), f(en))
}

case class SRAMLoadVector[T:Type:Bits](
  mem:  Exp[SRAM[T]],
  addr: Seq[Exp[Index]],
  en:   Exp[Bit],
  dim:  Int,
  len:  Int
)(implicit vT: Type[VectorN[T]]) extends VectorReaderOp[T](mem,addr,en,dim=dim,len=len) {
  def mirror(f:Tx) = SRAM.load_vector(f(mem),f(addr),f(en),dim,len)
}

case class SRAMStoreVector[T:Type:Bits](
  mem:  Exp[SRAM[T]],
  data: Exp[VectorN[T]],
  addr: Seq[Exp[Index]],
  en:   Exp[Bit],
  dim:  Int,
  len:  Int
) extends VectorWriterOp[T](mem,data,addr,en,dim=dim,len=len) {
  def mirror(f:Tx) = SRAM.store_vector(f(mem),f(data),f(addr),f(en),dim,len)
}


case class BankedSRAMLoad[T:Type:Bits](
  sram: Exp[SRAM[T]],         // SRAM
  bank: Seq[Seq[Exp[Index]]], // Vector of multi-dimensional bank addresses
  addr: Seq[Exp[Index]],      // Vector of bank offsets
  ens:  Seq[Exp[Bit]]         // Enables
)(implicit val vT: Type[VectorN[T]]) extends BankedReaderOp[T](sram, bank, addr, ens) {
  def mirror(f:Tx) = SRAM.banked_load(f(sram),bank.map{a => f(a)}, f(addr), f(ens))
}

case class BankedSRAMStore[T:Type:Bits](
  sram: Exp[SRAM[T]],         // SRAM
  data: Seq[Exp[T]],          // Write data
  bank: Seq[Seq[Exp[Index]]], // Vector of multi-dimensional bank addresses
  addr: Seq[Exp[Index]],      // Vector of bank offsets
  ens:  Seq[Exp[Bit]]         // Enables
) extends BankedWriterOp[T](sram, data, bank, addr, ens) {
  def mirror(f:Tx) = SRAM.banked_store(f(sram), f(data), bank.map{a => f(a)}, f(addr), f(ens))
}