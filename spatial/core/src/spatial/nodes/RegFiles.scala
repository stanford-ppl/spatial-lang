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
  @api def load(mem: C[T], is: Seq[Index], en: Bit): T = wrap(RegFile.load(mem.s, unwrap(is), en.s))
  @api def store(mem: C[T], is: Seq[Index], data: T, en: Bit): MUnit = wrap(RegFile.store(mem.s, unwrap(is), data.s, en.s))
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
) extends LocalReaderOp[T](reg,addr=inds,en=en) {
  def mirror(f:Tx) = RegFile.load(f(reg),f(inds),f(en))
  override def aliases = Nil
  val mT = typ[T]
  val bT = bits[T]
}

case class RegFileStore[T:Type:Bits](
  reg:  Exp[RegFile[T]],
  inds: Seq[Exp[Index]],
  data: Exp[T],
  en:   Exp[Bit]
) extends LocalWriterOp(reg,addr=inds,value=data,en=en) {
  def mirror(f:Tx) = RegFile.store(f(reg),f(inds),f(data),f(en))
  val mT = typ[T]
  val bT = bits[T]
}

case class RegFileReset[T:Type:Bits](
  rf: Exp[RegFile[T]],
  en: Exp[Bit]
) extends LocalResetterOp(rf, en) {
  def mirror(f:Tx) = RegFile.reset(f(rf), f(en))
  val mT = typ[T]
  val bT = bits[T]
}

case class RegFileShiftIn[T:Type:Bits](
  reg:  Exp[RegFile[T]],
  inds: Seq[Exp[Index]],
  dim:  Int,
  data: Exp[T],
  en:   Exp[Bit]
) extends LocalWriterOp(reg,addr=inds,value=data,en=en) {
  def mirror(f:Tx) = RegFile.shift_in(f(reg),f(inds),dim,f(data),f(en))
  val mT = typ[T]
  val bT = bits[T]
}

case class ParRegFileShiftIn[T:Type:Bits](
  reg:  Exp[RegFile[T]],
  inds: Seq[Exp[Index]],
  dim:  Int,
  data: Exp[Vector[T]],
  en:   Exp[Bit]
) extends LocalWriterOp(reg,addr=inds,value=data,en=en) {
  def mirror(f:Tx) = RegFile.par_shift_in(f(reg),f(inds),dim,f(data),f(en))
}

case class ParRegFileLoad[T:Type:Bits](
  reg:  Exp[RegFile[T]],
  inds: Seq[Seq[Exp[Index]]],
  ens:  Seq[Exp[Bit]]
)(implicit val vT: Type[VectorN[T]]) extends ParLocalReaderOp[VectorN[T]](reg,addrs=inds,ens=ens) {
  def mirror(f:Tx) = RegFile.par_load(f(reg),inds.map(is => f(is)),f(ens))
  override def aliases = Nil
  val mT = typ[T]
}

case class ParRegFileStore[T:Type:Bits](
  reg:  Exp[RegFile[T]],
  inds: Seq[Seq[Exp[Index]]],
  data: Seq[Exp[T]],
  ens:  Seq[Exp[Bit]]
) extends ParLocalWriterOp(reg,addrs=inds,values=data,ens=ens) {
  def mirror(f:Tx) = RegFile.par_store(f(reg),inds.map(is => f(is)),f(data),f(ens))
  val mT = typ[T]
}