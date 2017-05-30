package spatial.nodes

import spatial.compiler._
import forge._

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

  @api def iterators(mem: FIFO[T]): Seq[Counter] = Seq(Counter(0,sizeOf(mem),1,1))
}


/** IR Nodes **/
case class FIFONew[T:Type:Bits](size: Exp[Index]) extends Op2[T,FIFO[T]] {
  def mirror(f:Tx) = FIFO.alloc[T](f(size))
  val mT = typ[T]
  val bT = bits[T]
}
case class FIFOEnq[T:Type:Bits](fifo: Exp[FIFO[T]], data: Exp[T], en: Exp[Bit]) extends EnabledOp[MUnit](en) {
  def mirror(f:Tx) = FIFO.enq(f(fifo),f(data),f(en))
  val mT = typ[T]
  val bT = bits[T]
}
case class FIFODeq[T:Type:Bits](fifo: Exp[FIFO[T]], en: Exp[Bit]) extends EnabledOp[T](en) {
  def mirror(f:Tx) = FIFO.deq(f(fifo), f(en))
  val mT = typ[T]
  val bT = bits[T]
}
case class FIFOEmpty[T:Type:Bits](fifo: Exp[FIFO[T]]) extends Op[Bit] {
  def mirror(f:Tx) = FIFO.is_empty(f(fifo))
  val mT = typ[T]
  val bT = bits[T]
}
case class FIFOFull[T:Type:Bits](fifo: Exp[FIFO[T]]) extends Op[Bit] {
  def mirror(f:Tx) = FIFO.is_full(f(fifo))
  val mT = typ[T]
  val bT = bits[T]
}
case class FIFOAlmostEmpty[T:Type:Bits](fifo: Exp[FIFO[T]]) extends Op[Bit] {
  def mirror(f:Tx) = FIFO.is_almost_empty(f(fifo))
  val mT = typ[T]
  val bT = bits[T]
}
case class FIFOAlmostFull[T:Type:Bits](fifo: Exp[FIFO[T]]) extends Op[Bit] {
  def mirror(f:Tx) = FIFO.is_almost_full(f(fifo))
  val mT = typ[T]
  val bT = bits[T]
}
case class FIFONumel[T:Type:Bits](fifo: Exp[FIFO[T]]) extends Op[Index] {
  def mirror(f:Tx) = FIFO.numel(f(fifo))
  val mT = typ[T]
  val bT = bits[T]
}
