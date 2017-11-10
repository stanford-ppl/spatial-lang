package spatial.nodes

import argon.core._
import forge._
import spatial.aliases._
import spatial.utils._

case class FILOType[T:Bits](child: Type[T]) extends Type[FILO[T]] {
  override def wrapped(x: Exp[FILO[T]]) = FILO(x)(child,bits[T])
  override def typeArguments = List(child)
  override def stagedClass = classOf[FILO[T]]
  override def isPrimitive = false
}

// --- Memory
class FILOIsMemory[T:Type:Bits] extends Mem[T,FILO] {
  @api def load(mem: FILO[T], addr: Seq[Index], en: Bit): T = mem.pop(en)
  @api def store(mem: FILO[T], data: T, addr: Seq[Index], en: Bit): MUnit = mem.push(data, en)
  @api def iterators(mem: FILO[T]): Seq[Counter] = Seq(Counter(0,stagedSizeOf(mem),1,1))
  def par(mem: FILO[T]) = mem.p
}


/** IR Nodes **/
case class FILONew[T:Type:Bits](size: Exp[Index]) extends Alloc[FILO[T]] {
  def mirror(f:Tx) = FILO.alloc[T](f(size))
  val mT = typ[T]
  val bT = bits[T]
}
case class FILOPush[T:Type:Bits](
  filo: Exp[FILO[T]],
  data: Exp[T],
  en:   Exp[Bit]
) extends EnqueueLikeOp[T](filo,data,en=en) {
  def mirror(f:Tx) = FILO.push(f(filo),f(data),f(en))
}
case class FILOPop[T:Type:Bits](filo: Exp[FILO[T]], en: Exp[Bit]) extends DequeueLikeOp[T,T](filo,en=en) {
  def mirror(f:Tx) = FILO.pop(f(filo), f(en))
}
case class FILOPeek[T:Type:Bits](filo: Exp[FILO[T]]) extends StatusReaderOp[T,T](filo) {
  def mirror(f:Tx) = FILO.peek(f(filo))
}
case class FILOEmpty[T:Type:Bits](filo: Exp[FILO[T]]) extends StatusReaderOp[T,Bit](filo) {
  def mirror(f:Tx) = FILO.is_empty(f(filo))
}
case class FILOFull[T:Type:Bits](filo: Exp[FILO[T]]) extends StatusReaderOp[T,Bit](filo) {
  def mirror(f:Tx) = FILO.is_full(f(filo))
}
case class FILOAlmostEmpty[T:Type:Bits](filo: Exp[FILO[T]]) extends StatusReaderOp[T,Bit](filo) {
  def mirror(f:Tx) = FILO.is_almost_empty(f(filo))
}
case class FILOAlmostFull[T:Type:Bits](filo: Exp[FILO[T]]) extends StatusReaderOp[T,Bit](filo) {
  def mirror(f:Tx) = FILO.is_almost_full(f(filo))
}
case class FILONumel[T:Type:Bits](filo: Exp[FILO[T]]) extends StatusReaderOp[T,Index](filo) {
  def mirror(f:Tx) = FILO.numel(f(filo))
}

case class BankedFILOPop[T:Type:Bits](
  filo: Exp[FILO[T]],
  ens:  Seq[Exp[Bit]]
)(implicit val vT: Type[VectorN[T]]) extends BankedDequeueLikeOp[T](filo, ens=ens) {
  def mirror(f:Tx) = FILO.banked_pop(f(filo),f(ens))
}
case class BankedFILOPush[T:Type:Bits](
  filo: Exp[FILO[T]],
  data: Seq[Exp[T]],
  ens:  Seq[Exp[Bit]]
) extends BankedEnqueueLikeOp[T](filo,data, ens=ens) {
  def mirror(f:Tx) = FILO.banked_push(f(filo),f(data),f(ens))
}