package spatial.api

import argon.core.Staging
import argon.typeclasses.{BitsExp, CustomBitWidths}
import spatial.SpatialExp


trait VectorApi extends VectorExp { this: SpatialExp => }

trait VectorExp extends Staging with BitsExp with CustomBitWidths {
  this: SpatialExp =>

  def Width[T](w: Int): INT[Vector[T]] = INT_T[Vector[T]](w)

  /** Infix methods **/
  case class Vector[T:Staged:Bits](s: Exp[Vector[T]]) {
    def apply(i: Int)(implicit ctx: SrcCtx): T = wrap(vector_apply(s, i))
  }

  private[spatial] def vectorize[T:Staged:Bits](elems: Seq[Exp[T]])(implicit ctx: SrcCtx): Exp[Vector[T]] = vector_new(elems)

  /** Staged Types **/
  case class VectorType[T:Bits](child: Staged[T], W: INT[Vector[T]]) extends Staged[Vector[T]] with BitsLookup[Vector[T]] {
    override def unwrapped(x: Vector[T]) = x.s
    override def wrapped(x: Exp[Vector[T]]) = Vector(x)(child, bits[T])
    override def typeArguments = List(child)
    override def stagedClass = classOf[Vector[T]]
    override def isPrimitive = false
    def width = W.v

    protected def getBits(children: Seq[Staged[_]]): Option[Bits[Vector[T]]] = Some(vectorBits[T](child,bits[T],W) )
  }
  implicit def vectorType[T:Staged:Bits](implicit W: INT[Vector[T]]): Staged[Vector[T]] = VectorType[T](typ[T], W)

  class VectorBits[T:Staged:Bits](implicit val W: INT[Vector[T]]) extends Bits[Vector[T]] {
    val n = W.v
    override def zero(implicit ctx: SrcCtx) = wrap(vectorize(unwrap(Seq.fill(n){ bits[T].zero })))
    override def one(implicit ctx: SrcCtx) = wrap(vectorize(unwrap(Seq.fill(n){ bits[T].one })))
    override def random(max: Option[Vector[T]])(implicit ctx: SrcCtx) = {
      wrap(vectorize(unwrap(Seq.tabulate(n){i => bits[T].random(max.map(_.apply(i))) } )))
    }
    override def length = n * bits[T].length
  }

  implicit def vectorBits[T:Staged:Bits](implicit W: INT[Vector[T]]): Bits[Vector[T]] = new VectorBits[T]


  /** IR Nodes **/
  case class ListVector[T:Staged:Bits](elems: Seq[Exp[T]])(implicit val W: INT[Vector[T]]) extends Op[Vector[T]] {
    def mirror(f:Tx) = vector_new(f(elems))
  }
  case class VectorApply[T:Staged:Bits](vector: Exp[Vector[T]], index: Int) extends Op[T] {
    def mirror(f:Tx) = vector_apply(f(vector), index)
  }
  case class VectorSlice[T:Staged:Bits](vector: Exp[Vector[T]], start: Int, end: Int)(implicit val W: INT[Vector[T]]) extends Op[Vector[T]] {
    def mirror(f:Tx) = vector_slice(f(vector), start, end)
  }

  /** Constructors **/
  private[spatial] def vector_new[T:Staged:Bits](elems: Seq[Exp[T]])(implicit ctx: SrcCtx): Exp[Vector[T]] = {
    implicit val W = Width[T](elems.length)
    stage(ListVector(elems))(ctx)
  }

  private[spatial] def vector_apply[T:Staged:Bits](vector: Exp[Vector[T]], index: Int)(implicit ctx: SrcCtx): Exp[T] = vector match {
    case Op(ListVector(elems)) =>
      if (index < 0 || index >= elems.length) {
        new InvalidVectorApplyIndex(vector, index)
        fresh[T]
      }
      else elems(index)
    case _ =>
      stage(VectorApply(vector, index))(ctx)
  }

  private[spatial] def vector_slice[T:Staged:Bits](vector: Exp[Vector[T]], start: Int, end: Int)(implicit ctx: SrcCtx): Exp[Vector[T]] = vector match {
    case Op(ListVector(elems)) =>
      if (start >= end) {
        new InvalidVectorSlice(vector, start, end)
        implicit val W = Width[T](0)
        fresh[Vector[T]]
      }
      else {
        val from = Math.max(0, start)
        val until = Math.min(elems.length, end)
        vector_new(elems.slice(from, until))
      }
    case _ =>
      implicit val W = Width[T](end - start)
      stage(VectorSlice(vector, start, end))(ctx)
  }

  private[spatial] def lenOf(x: Exp[_])(implicit ctx: SrcCtx): Int = x.tp match {
    case tp: VectorType[_] => tp.width
    case _ => throw new UndefinedDimensionsError(x, None)
  }
}