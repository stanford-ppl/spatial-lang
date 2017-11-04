package spatial.nodes

import argon.core._
import forge._
import spatial.aliases._
import spatial.utils._

/** Type classes **/
// --- Staged
case class FIFOType[T:Bits](child: Type[T]) extends Type[FIFO[T]] {
  override def wrapped(x: Exp[FIFO[T]]) = new FIFO(x)(child,bits[T])
  override def typeArguments = List(child)
  override def stagedClass = classOf[FIFO[T]]
  override def isPrimitive = false
}

// --- Memory
class FIFOIsMemory[T:Type:Bits] extends Mem[T,FIFO] {
  @api def load(mem: FIFO[T], addr: Seq[Index], en: Bit): T = mem.deq(en)
  @api def store(mem: FIFO[T], data: T, addr: Seq[Index], en: Bit): MUnit = mem.enq(data, en)

  @api def iterators(mem: FIFO[T]): Seq[Counter] = Seq(Counter(0,stagedSizeOf(mem),1,1))

  def par(mem: FIFO[T]): Option[Index] = mem.p
}


/** IR Nodes **/
case class FIFONew[T:Type:Bits](size: Exp[Index]) extends Alloc[FIFO[T]] {
  def mirror(f:Tx) = FIFO.alloc[T](f(size))
  val mT = typ[T]
  val bT = bits[T]
}
case class FIFOEnq[T:Type:Bits](
  fifo: Exp[FIFO[T]],
  data: Exp[T],
  en:   Exp[Bit]
) extends LocalWriterOp[T](fifo,data,en=en) {
  def mirror(f:Tx) = FIFO.enq(f(fifo),f(data),f(en))
}
case class FIFODeq[T:Type:Bits](
  fifo: Exp[FIFO[T]],
  en:   Exp[Bit]
) extends LocalReadModifyOp[T,T](fifo,en=en) {
  def mirror(f:Tx) = FIFO.deq(f(fifo), f(en))
}
case class FIFOPeek[T:Type:Bits](fifo: Exp[FIFO[T]]) extends LocalReadStatusOp[T,T](fifo) {
  def mirror(f:Tx) = FIFO.peek(f(fifo))
}
case class FIFOEmpty[T:Type:Bits](fifo: Exp[FIFO[T]]) extends LocalReadStatusOp[T,Bit](fifo) {
  def mirror(f:Tx) = FIFO.is_empty(f(fifo))
}
case class FIFOFull[T:Type:Bits](fifo: Exp[FIFO[T]]) extends LocalReadStatusOp[T,Bit](fifo) {
  def mirror(f:Tx) = FIFO.is_full(f(fifo))
}
case class FIFOAlmostEmpty[T:Type:Bits](fifo: Exp[FIFO[T]]) extends LocalReadStatusOp[T,Bit](fifo) {
  def mirror(f:Tx) = FIFO.is_almost_empty(f(fifo))
}
case class FIFOAlmostFull[T:Type:Bits](fifo: Exp[FIFO[T]]) extends LocalReadStatusOp[T,Bit](fifo) {
  def mirror(f:Tx) = FIFO.is_almost_full(f(fifo))
}
case class FIFONumel[T:Type:Bits](fifo: Exp[FIFO[T]]) extends LocalReadStatusOp[T,Index](fifo) {
  def mirror(f:Tx) = FIFO.numel(f(fifo))
}

case class BankedFIFODeq[T:Type:Bits](
  fifo: Exp[FIFO[T]],
  ens:  Seq[Exp[Bit]]
)(implicit val vT: Type[VectorN[T]]) extends BankedReadModifyOp[T](fifo, ens=ens) {
  def mirror(f:Tx) = FIFO.banked_deq(f(fifo),f(ens))
}

case class BankedFIFOEnq[T:Type:Bits](
  fifo: Exp[FIFO[T]],
  data: Seq[Exp[T]],
  ens:  Seq[Exp[Bit]]
) extends BankedWriterOp[T](fifo,data, ens=ens) {
  def mirror(f:Tx) = FIFO.banked_enq(f(fifo),f(data),f(ens))
}

