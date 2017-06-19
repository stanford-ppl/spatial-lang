package spatial.nodes

import argon.core._
import forge._
import spatial.aliases._

/** Staged Types **/
trait VectorType[T] { this: Type[_] =>
  def child: Type[T]
  def width: Int
  def isPrimitive = false
  override def equals(obj: Any) = obj match {
    case that: VectorType[_] => this.child == that.child
    case _ => false
  }
}

case class VecType[N:INT,T:Bits](child: Type[T]) extends Type[Vec[N,T]] with VectorType[T] with CanBits[Vec[N,T]] {
  val width: Int = INT[N].v
  override def wrapped(x: Exp[Vec[N,T]]) = Vec[N,T](x)(INT[N], child, bits[T])
  override def typeArguments = List(child)
  override def stagedClass = classOf[Vec[N,T]]
  protected def getBits(children: Seq[Type[_]]): Option[Bits[Vec[N,T]]] = Some(Vec.vecBits[N,T](INT[N],child,bits[T]))
}

trait VectorBits[T] {
  def width: Int
  def bT: Bits[T]
  def length: Int = width * bT.length
}

class VecBits[N:INT,T:Type:Bits] extends Bits[Vec[N,T]] with VectorBits[T] {
  type V[X] = Vec[N,X]
  val width: Int = INT[N].v
  val bT: Bits[T] = bits[T]
  @api def zero: V[T] = wrap(Vector.fromseq[T,V](Seq.fill(width){ bT.zero.s }))
  @api def one: V[T] = wrap(Vector.fromseq[T,V](Seq.fill(width){ bT.one.s }))
  @api def random(max: Option[V[T]]) = {
    val maxes = Seq.tabulate(width) { i => bT.random(max.map(_.apply(i))).s }
    wrap(Vector.fromseq[T, V](maxes))
  }
}


/** IR Nodes **/
case class ListVector[T:Type:Bits,V[_]<:Vector[_]](elems: Seq[Exp[T]])(implicit vT: Type[V[T]]) extends PrimitiveAlloc[V[T]] {
  def mirror(f:Tx) = Vector.fromseq[T,V](f(elems))
}
case class VectorApply[T:Type](vector: Exp[Vector[T]], index: Int) extends Op[T] {
  def mirror(f:Tx) = Vector.select(f(vector), index)
}
case class VectorSlice[T:Type:Bits,V[_]<:Vector[_]](vector: Exp[Vector[T]], end: Int, start: Int)(implicit vT: Type[V[T]]) extends Op[V[T]] {
  def mirror(f:Tx) = Vector.slice[T,V](f(vector), end, start)
}
case class VectorConcat[T:Type:Bits,V[_]<:Vector[_]](vectors: Seq[Exp[Vector[T]]])(implicit vT: Type[V[T]]) extends Op[V[T]] {
  def mirror(f:Tx) = Vector.concat[T,V](f(vectors))
}