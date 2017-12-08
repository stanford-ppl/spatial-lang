package spatial.nodes

import argon.core._
import forge._
import spatial.aliases._
import spatial.utils._

trait RegFileType[T] {
  def child: Type[T]
  def isPrimitive = false
}
case class RegFile1Type[T:Bits](child: Type[T]) extends Type[RegFile1[T]] with RegFileType[T] {
  override def wrapped(x: Exp[RegFile1[T]]) = new RegFile1(x)(child,bits[T])
  override def typeArguments = List(child)
  override def stagedClass = classOf[RegFile1[T]]
}
case class RegFile2Type[T:Bits](child: Type[T]) extends Type[RegFile2[T]] with RegFileType[T] {
  override def wrapped(x: Exp[RegFile2[T]]) = new RegFile2(x)(child,bits[T])
  override def typeArguments = List(child)
  override def stagedClass = classOf[RegFile2[T]]
}
case class RegFile3Type[T:Bits](child: Type[T]) extends Type[RegFile3[T]] with RegFileType[T] {
  override def wrapped(x: Exp[RegFile3[T]]) = new RegFile3(x)(child,bits[T])
  override def typeArguments = List(child)
  override def stagedClass = classOf[RegFile3[T]]
}

class RegFileIsMemory[T:Type:Bits,C[T]](implicit mC: Type[C[T]], ev: C[T] <:< RegFile[T]) extends Mem[T, C] {
  @api def load(mem: C[T], addr: Seq[Index], en: Bit): T = wrap(RegFile.load(mem.s, unwrap(addr), en.s))
  @api def store(mem: C[T], data: T, addr: Seq[Index], en: Bit): MUnit = wrap(RegFile.store(mem.s, unwrap(addr), data.s, en.s))
  @api def iterators(mem: C[T]): Seq[Counter] = stagedDimsOf(mem.s).map{d => Counter(0, wrap(d), 1, 1) }
  def par(mem: C[T]) = None
}


/** IR Nodes **/
case class RegFileNew[T:Type:Bits,C[_]<:RegFile[_]](
  dims:  Seq[Exp[Index]],
  inits: Option[Seq[Exp[T]]]
)(implicit cT: Type[C[T]]) extends Alloc[C[T]] {
  def mirror(f:Tx) = RegFile.alloc[T,C](inits.map(x => f(x)), f(dims):_*)
  val mT = typ[T]
  val bT = bits[T]
}

case class RegFileLoad[T:Type:Bits](
  reg:  Exp[RegFile[T]],
  inds: Seq[Exp[Index]],
  en:   Exp[Bit]
) extends ReaderOp[T,T](reg,addr=inds,en=en) {
  def mirror(f:Tx) = RegFile.load(f(reg),f(inds),f(en))
  override def aliases = Nil
}

case class RegFileStore[T:Type:Bits](
  reg:  Exp[RegFile[T]],
  addr: Seq[Exp[Index]],
  data: Exp[T],
  en:   Exp[Bit]
) extends WriterOp[T](reg,data,addr,en) {
  def mirror(f:Tx) = RegFile.store(f(reg),f(addr),f(data),f(en))
}

case class RegFileReset[T:Type:Bits](
  rf: Exp[RegFile[T]],
  en: Exp[Bit]
) extends ResetterOp(rf, en) {
  def mirror(f:Tx) = RegFile.reset(f(rf), f(en))
  val mT = typ[T]
  val bT = bits[T]
}

case class RegFileShiftIn[T:Type:Bits](
  reg:  Exp[RegFile[T]],
  data: Exp[T],
  addr: Seq[Exp[Index]],
  en:   Exp[Bit],
  ax:   Int
) extends EnqueueLikeOp[T](reg,data,addr,en) {
  def mirror(f:Tx) = RegFile.shift_in(f(reg),f(data),f(addr),f(en),ax)
}

case class RegFileVectorShiftIn[T:Type:Bits](
  reg:  Exp[RegFile[T]],
  data: Exp[Vector[T]],
  addr: Seq[Exp[Index]],
  en:   Exp[Bit],
  ax:   Int,
  len:  Int
) extends VectorEnqueueLikeOp[T](reg,data,addr,en,ax,len) {
  def mirror(f:Tx) = RegFile.vector_shift_in(f(reg),f(data),f(addr),f(en),axis)
}

case class BankedRegFileLoad[T:Type:Bits](
  reg:  Exp[RegFile[T]],
  bank: Seq[Seq[Exp[Index]]],
  addr: Seq[Exp[Index]],
  ens:  Seq[Exp[Bit]]
)(implicit val vT: Type[VectorN[T]]) extends BankedReaderOp[T](reg,bank,addr,ens) {
  def mirror(f:Tx) = RegFile.banked_load(f(reg),bank.map{a=>f(a)},f(addr),f(ens))
}

case class BankedRegFileStore[T:Type:Bits](
  reg:  Exp[RegFile[T]],
  data: Seq[Exp[T]],
  bank: Seq[Seq[Exp[Index]]],
  addr: Seq[Exp[Index]],
  ens:  Seq[Exp[Bit]]
) extends BankedWriterOp[T](reg,data,bank,addr,ens) {
  def mirror(f:Tx) = RegFile.banked_store(f(reg), f(data), bank.map{a=>f(a)}, f(addr), f(ens))
}
