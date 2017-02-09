package spatial.api

import argon.core.Staging
import argon.typeclasses.BitsExp
import spatial.SpatialExp


trait VectorApi extends VectorExp { this: SpatialExp => }

trait VectorExp extends Staging with BitsExp {
  this: SpatialExp =>

  /** Infix methods **/
  case class Vector[T:Staged:Bits](s: Exp[Vector[T]]) {
    // Nothing here for now
  }

  private[spatial] def vectorize[T:Staged:Bits](elems: Seq[Exp[T]])(implicit ctx: SrcCtx): Exp[Vector[T]] = vector_new(elems)

  /** Staged Types **/
  case class VectorType[T:Bits](child: Staged[T]) extends Staged[Vector[T]] {
    override def unwrapped(x: Vector[T]) = x.s
    override def wrapped(x: Exp[Vector[T]]) = Vector(x)(child, bits[T])
    override def typeArguments = List(child)
    override def stagedClass = classOf[Vector[T]]
    override def isPrimitive = false
  }
  implicit def vectorType[T:Staged:Bits]: Staged[Vector[T]] = VectorType(typ[T])

  /** IR Nodes **/
  case class ListVector[T:Staged:Bits](elems: Seq[Exp[T]]) extends Op[Vector[T]] {
    def mirror(f:Tx) = vector_new(f(elems))
  }
  case class VectorApply[T:Staged:Bits](vector: Exp[Vector[T]], index: Int) extends Op[T] {
    def mirror(f:Tx) = vector_apply(f(vector), index)
  }
  case class VectorSlice[T:Staged:Bits](vector: Exp[Vector[T]], start: Int, end: Int) extends Op[Vector[T]] {
    def mirror(f:Tx) = vector_slice(f(vector), start, end)
  }

  /** Constructors **/
  private[spatial] def vector_new[T:Staged:Bits](elems: Seq[Exp[T]])(implicit ctx: SrcCtx): Exp[Vector[T]] = {
    stage(ListVector(elems))(ctx)
  }

  private[spatial] def vector_apply[T:Staged:Bits](vector: Exp[Vector[T]], index: Int)(implicit ctx: SrcCtx): Exp[T] = vector match {
    case Op(ListVector(elems)) =>
      if (index < 0 || index >= elems.length) {
        new InvalidVectorApplyIndex(vector, index)
        fresh[T]
      }
      else elems(index)
    case _ => stage(VectorApply(vector, index))(ctx)
  }

  private[spatial] def vector_slice[T:Staged:Bits](vector: Exp[Vector[T]], start: Int, end: Int)(implicit ctx: SrcCtx): Exp[Vector[T]] = vector match {
    case Op(ListVector(elems)) =>
      if (start >= end) {
        new InvalidVectorSlice(vector, start, end)
        fresh[Vector[T]]
      }
      else {
        val from = Math.max(0, start)
        val until = Math.min(elems.length, end)
        vector_new(elems.slice(from, until))
      }
    case _ => stage(VectorSlice(vector, start, end))(ctx)
  }

  private[spatial] def lenOf(x: Exp[_])(implicit ctx: SrcCtx): Int = x match {
    case Op(ListVector(elems)) => elems.length
    case _ => throw new UndefinedDimensionsError(x, None)
  }
}