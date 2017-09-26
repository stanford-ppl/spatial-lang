package spatial.nodes

import argon.core._
import spatial.aliases._

case class StreamInType[T:Bits](child: Type[T]) extends Type[StreamIn[T]] {
  override def wrapped(x: Exp[StreamIn[T]]) = StreamIn(x)(child, bits[T])
  override def typeArguments = List(child)
  override def stagedClass = classOf[StreamIn[T]]
  override def isPrimitive = false
}

case class StreamOutType[T:Bits](child: Type[T]) extends Type[StreamOut[T]] {
  override def wrapped(x: Exp[StreamOut[T]]) = StreamOut(x)(child, bits[T])
  override def typeArguments = List(child)
  override def stagedClass = classOf[StreamOut[T]]
  override def isPrimitive = false
}

case class BufferedOutType[T:Bits](child: Type[T]) extends Type[BufferedOut[T]] {
  override def wrapped(x: Exp[BufferedOut[T]]) = BufferedOut(x)(child, bits[T])
  override def typeArguments = List(child)
  override def stagedClass = classOf[BufferedOut[T]]
  override def isPrimitive = false
}


/** IR Nodes **/
case class StreamInNew[T:Type:Bits](bus: Bus) extends Alloc[StreamIn[T]] {
  def mirror(f: Tx) = StreamIn.alloc[T](bus)
  val mT = typ[T]
}

case class StreamOutNew[T:Type:Bits](bus: Bus) extends Alloc[StreamOut[T]] {
  def mirror(f: Tx) = StreamOut.alloc[T](bus)
  val mT = typ[T]
}

case class StreamRead[T:Type:Bits](stream: Exp[StreamIn[T]], en: Exp[Bit]) extends LocalReaderOp[T](stream,en=en) {
  def mirror(f:Tx) = StreamIn.read(f(stream), f(en))
  val mT = typ[T]
  val bT = bits[T]
}

case class StreamWrite[T:Type:Bits](
  stream: Exp[StreamOut[T]],
  data:   Exp[T],
  en:     Exp[Bit]
) extends LocalWriterOp(stream,value=data,en=en) {
  def mirror(f:Tx) = StreamOut.write(f(stream), f(data), f(en))
  val mT = typ[T]
  val bT = bits[T]
}

case class BufferedOutNew[T:Type:Bits](dims: Seq[Exp[Index]], bus: Bus) extends Alloc[BufferedOut[T]] {
  def mirror(f:Tx) = BufferedOut.alloc[T](f(dims), bus)
  val mT = typ[T]
}

case class BufferedOutWrite[T:Type:Bits](
  buffer: Exp[BufferedOut[T]],
  data:   Exp[T],
  is:     Seq[Exp[Index]],
  en:     Exp[Bit]
) extends LocalWriterOp(buffer,value=data,addr=is,en=en) {
  def mirror(f:Tx) = BufferedOut.write[T](f(buffer),f(data),f(is),f(en))
  val mT = typ[T]
  val bT = bits[T]
}

case class ParStreamRead[T:Type:Bits](
  stream: Exp[StreamIn[T]],
  ens:    Seq[Exp[Bit]]
)(implicit val vT: Type[VectorN[T]]) extends ParLocalReaderOp[VectorN[T]](stream, ens=ens) {
  def mirror(f:Tx) = StreamIn.par_read(f(stream),f(ens))
  val mT = typ[T]
  val bT = bits[T]
}

case class ParStreamWrite[T:Type:Bits](
  stream: Exp[StreamOut[T]],
  data:   Seq[Exp[T]],
  ens:    Seq[Exp[Bit]]
) extends ParLocalWriterOp(stream, values=data, ens=ens) {
  def mirror(f:Tx) = StreamOut.par_write(f(stream),f(data),f(ens))
  val mT = typ[T]
  val bT = bits[T]
}