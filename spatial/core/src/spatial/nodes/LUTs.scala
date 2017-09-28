package spatial.nodes

import argon.core._
import spatial.aliases._

trait LUTType[T] {
  def child: Type[T]
  def isPrimitive = false
}
case class LUT1Type[T:Bits](child: Type[T]) extends Type[LUT1[T]] with LUTType[T] {
  override def wrapped(x: Exp[LUT1[T]]) = new LUT1(x)(child,bits[T])
  override def typeArguments = List(child)
  override def stagedClass = classOf[LUT1[T]]
}
case class LUT2Type[T:Bits](child: Type[T]) extends Type[LUT2[T]] with LUTType[T] {
  override def wrapped(x: Exp[LUT2[T]]) = new LUT2(x)(child,bits[T])
  override def typeArguments = List(child)
  override def stagedClass = classOf[LUT2[T]]
}
case class LUT3Type[T:Bits](child: Type[T]) extends Type[LUT3[T]] with LUTType[T] {
  override def wrapped(x: Exp[LUT3[T]]) = new LUT3(x)(child,bits[T])
  override def typeArguments = List(child)
  override def stagedClass = classOf[LUT3[T]]
}
case class LUT4Type[T:Bits](child: Type[T]) extends Type[LUT4[T]] with LUTType[T] {
  override def wrapped(x: Exp[LUT4[T]]) = new LUT4(x)(child,bits[T])
  override def typeArguments = List(child)
  override def stagedClass = classOf[LUT4[T]]
}
case class LUT5Type[T:Bits](child: Type[T]) extends Type[LUT5[T]] with LUTType[T] {
  override def wrapped(x: Exp[LUT5[T]]) = new LUT5(x)(child,bits[T])
  override def typeArguments = List(child)
  override def stagedClass = classOf[LUT5[T]]
}


/** IR Nodes **/
case class LUTNew[T:Type:Bits,C[_]<:LUT[_]](
  dims:  Seq[Int],
  elems: Seq[Exp[T]]
)(implicit cT: Type[C[T]]) extends Alloc[C[T]] {

  def mirror(f:Tx) = LUT.alloc[T,C](dims, f(elems))
  val mT = typ[T]
  val bT = bits[T]

  override def toString = {
    val es = elems.take(3).mkString(",")
    val more = if (elems.length > 3) s"... [${elems.length-3} more]" else ""
    s"LUTNew($dims, Seq($es$more))"
  }
}

case class LUTLoad[T:Type:Bits](
  lut:  Exp[LUT[T]],
  inds: Seq[Exp[Index]],
  en:   Exp[Bit]
) extends LocalReaderOp[T](lut,addr=inds,en=en) {
  def mirror(f:Tx) = LUT.load(f(lut),f(inds),f(en))
  override def aliases = Nil
  val mT = typ[T]
  val bT = bits[T]
}
