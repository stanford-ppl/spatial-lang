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
  @api def load(mem: FIFO[T], is: Seq[Index], en: Bit): T = mem.deq(en)
  @api def store(mem: FIFO[T], is: Seq[Index], data: T, en: Bit): MUnit = mem.enq(data, en)

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
) extends LocalWriterOp(fifo,value=data,en=en) {
  def mirror(f:Tx) = FIFO.enq(f(fifo),f(data),f(en))
  val mT = typ[T]
  val bT = bits[T]
}
case class FIFODeq[T:Type:Bits](
  fifo: Exp[FIFO[T]],
  en:   Exp[Bit]
) extends LocalReadModifyOp[T](fifo,en=en) {
  def mirror(f:Tx) = FIFO.deq(f(fifo), f(en))
  val mT = typ[T]
  val bT = bits[T]
}
case class FIFOPeek[T:Type:Bits](fifo: Exp[FIFO[T]]) extends LocalReadStatusOp[T](fifo) {
  def mirror(f:Tx) = FIFO.peek(f(fifo))
  val mT = typ[T]
  val bT = bits[T]
}
case class FIFOEmpty[T:Type:Bits](fifo: Exp[FIFO[T]]) extends LocalReadStatusOp[Bit](fifo) {
  def mirror(f:Tx) = FIFO.is_empty(f(fifo))
  val mT = typ[T]
  val bT = bits[T]
}
case class FIFOFull[T:Type:Bits](fifo: Exp[FIFO[T]]) extends LocalReadStatusOp[Bit](fifo) {
  def mirror(f:Tx) = FIFO.is_full(f(fifo))
  val mT = typ[T]
  val bT = bits[T]
}
case class FIFOAlmostEmpty[T:Type:Bits](fifo: Exp[FIFO[T]]) extends LocalReadStatusOp[Bit](fifo) {
  def mirror(f:Tx) = FIFO.is_almost_empty(f(fifo))
  val mT = typ[T]
  val bT = bits[T]
}
case class FIFOAlmostFull[T:Type:Bits](fifo: Exp[FIFO[T]]) extends LocalReadStatusOp[Bit](fifo) {
  def mirror(f:Tx) = FIFO.is_almost_full(f(fifo))
  val mT = typ[T]
  val bT = bits[T]
}
case class FIFONumel[T:Type:Bits](fifo: Exp[FIFO[T]]) extends LocalReadStatusOp[Index](fifo) {
  def mirror(f:Tx) = FIFO.numel(f(fifo))
  val mT = typ[T]
  val bT = bits[T]
}

case class ParFIFODeq[T:Type:Bits](
  fifo: Exp[FIFO[T]],
  ens:  Seq[Exp[Bit]]
)(implicit val vT: Type[VectorN[T]]) extends ParLocalReadModifyOp[VectorN[T]](fifo,ens=ens) {
  def mirror(f:Tx) = FIFO.par_deq(f(fifo),f(ens))
  val mT = typ[T]
}

case class ParFIFOEnq[T:Type:Bits](
  fifo: Exp[FIFO[T]],
  data: Seq[Exp[T]],
  ens:  Seq[Exp[Bit]]
) extends ParLocalWriterOp(fifo,values=data,ens=ens) {
  def mirror(f:Tx) = FIFO.par_enq(f(fifo),f(data),f(ens))
  val mT = typ[T]
}
