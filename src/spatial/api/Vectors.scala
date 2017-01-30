package spatial.api

import argon.ops._
import spatial._

trait VectorOps extends BitsOps { this: SpatialOps =>
  type Vector[T] <: VectorInfixOps[T]

  trait VectorInfixOps[T] {
    // Nothing here for now
  }

  implicit def vectorType[T:Bits]: Staged[Vector[T]]
}
trait VectorApi extends VectorOps with BitsApi { this: SpatialApi => }
trait VectorExp extends VectorOps with BitsExp { this: SpatialExp =>

  /** API **/
  case class Vector[T:Bits](s: Exp[Vector[T]]) extends VectorInfixOps[T] {
    // Nothing here for now
  }

  private[spatial] def vectorize[T:Bits](elems: Seq[Exp[T]])(implicit ctx: SrcCtx): Exp[Vector[T]] = vector_new(elems)

  /** Staged Types **/
  case class VectorType[T](bits: Bits[T]) extends Staged[Vector[T]] {
    override def unwrapped(x: Vector[T]) = x.s
    override def wrapped(x: Exp[Vector[T]]) = Vector(x)(bits)
    override def typeArguments = List(bits)
    override def stagedClass = classOf[Vector[T]]
    override def isPrimitive = false
  }
  implicit def vectorType[T:Bits]: Staged[Vector[T]] = VectorType(bits[T])

  /** IR Nodes **/
  case class ListVector[T:Bits](elems: Seq[Exp[T]]) extends Op[Vector[T]] { def mirror(f:Tx) = vector_new(f(elems)) }
  case class VectorApply[T:Bits](vector: Exp[Vector[T]], index: Int) extends Op[T] {
    def mirror(f:Tx) = vector_apply(f(vector), index)
  }
  case class VectorSlice[T:Bits](vector: Exp[Vector[T]], start: Int, end: Int) extends Op[Vector[T]] {
    def mirror(f:Tx) = vector_slice(f(vector), start, end)
  }

  /** Constructors **/
  private[spatial] def vector_new[T:Bits](elems: Seq[Exp[T]])(implicit ctx: SrcCtx): Exp[Vector[T]] = {
    stage(ListVector(elems))(ctx)
  }

  private[spatial] def vector_apply[T:Bits](vector: Exp[Vector[T]], index: Int)(implicit ctx: SrcCtx): Exp[T] = vector match {
    case Op(ListVector(elems)) =>
      if (index < 0 || index >= elems.length) {
        new InvalidVectorApplyIndex(vector, index)
        fresh[T]
      }
      else elems(index)
    case _ => stage(VectorApply(vector, index))(ctx)
  }

  private[spatial] def vector_slice[T:Bits](vector: Exp[Vector[T]], start: Int, end: Int)(implicit ctx: SrcCtx): Exp[Vector[T]] = vector match {
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