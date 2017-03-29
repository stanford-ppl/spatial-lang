package spatial.api

import argon.core.Staging
import argon.typeclasses.{BitsExp, CustomBitWidths}
import spatial.SpatialExp

trait VectorApi extends VectorExp { this: SpatialExp => }

trait VectorExp extends Staging with BitsExp with CustomBitWidths {
  this: SpatialExp =>

  /** Infix methods **/
  case class Vector[W:INT,T:Meta:Bits](s: Exp[Vector[W,T]]) extends MetaAny[Vector[W,T]] {
    private def width = INT[W].v
    def apply(i: Int)(implicit ctx: SrcCtx): T = wrap(vector_apply(s, i))
    override def ===(x: Vector[W,T])(implicit ctx: SrcCtx) = {
      reduceTree(List.tabulate(width){i => this(i) === x(i) }){_&&_}
    }
    override def =!=(x: Vector[W,T])(implicit ctx: SrcCtx) = {
      reduceTree(List.tabulate(width){i => this(i) =!= x(i) }){_||_}
    }
    override def toText(implicit ctx: SrcCtx) = textify(this)
  }

  /*object Vector {
    def apply[T](elems: Exp[T]*)(implicit ctx: SrcCtx, meta: Meta[T], bits: Bits[T]) = macro VectorMacros.vectorize
  }
  def vectorize[T](elems: Seq[Exp[T]])(implicit ctx: SrcCtx, meta: Meta[T], bits: Bits[T]) = macro VectorMacros.vectorize*/


  private[spatial] def vect[W:INT,T:Type:Bits](elems: Seq[Exp[T]])(implicit ctx: SrcCtx): Exp[Vector[W,T]] = vector_new(elems)

  /** Staged Types **/
  case class VectorType[W,T:Bits](w: INT[W], child: Meta[T]) extends Meta[Vector[W,T]] with CanBits[Vector[W,T]] {
    def width: Int = w.v
    override def wrapped(x: Exp[Vector[W,T]]) = Vector[W,T](x)(w, child, bits[T])
    override def typeArguments = List(child)
    override def stagedClass = classOf[Vector[W,T]]
    override def isPrimitive = false

    protected def getBits(children: Seq[Type[_]]): Option[Bits[Vector[W,T]]] = Some(vectorBits[W,T](w,child,bits[T]))
  }
  implicit def vectorType[W:INT,T:Meta:Bits]: Meta[Vector[W,T]] = VectorType[W,T](INT[W],meta[T])(bits[T])

  class VectorBits[W:INT,T:Meta:Bits] extends Bits[Vector[W,T]] {
    def width: Int = INT[W].v
    override def zero(implicit ctx: SrcCtx) = wrap(vect(unwrap(Seq.fill(width){ bits[T].zero })))
    override def one(implicit ctx: SrcCtx) = wrap(vect(unwrap(Seq.fill(width){ bits[T].one })))
    override def random(max: Option[Vector[W,T]])(implicit ctx: SrcCtx) = {
      wrap(vect(unwrap(Seq.tabulate(width){i => bits[T].random(max.map(_.apply(i))) } )))
    }
    override def length = width * bits[T].length
  }

  implicit def vectorBits[W:INT,T:Meta:Bits]: Bits[Vector[W,T]] = new VectorBits[W,T]


  /** IR Nodes **/
  case class ListVector[W:INT,T:Type:Bits](elems: Seq[Exp[T]]) extends Op[Vector[W,T]] {
    def mirror(f:Tx) = vector_new(f(elems))
  }
  case class VectorApply[W:INT,T:Type:Bits](vector: Exp[Vector[W,T]], index: Int) extends Op[T] {
    def mirror(f:Tx) = vector_apply(f(vector), index)
  }
  case class VectorSlice[W:INT,WW:INT,T:Type:Bits](vector: Exp[Vector[W,T]], start: Int, end: Int) extends Op[Vector[WW,T]] {
    def mirror(f:Tx) = vector_slice(f(vector), start, end)
  }

  /** Constructors **/
  private[spatial] def vector_new[W:INT,T:Type:Bits](elems: Seq[Exp[T]])(implicit ctx: SrcCtx): Exp[Vector[W,T]] = {
    stage(ListVector[W,T](elems))(ctx)
  }

  private[spatial] def vector_apply[W:INT,T:Type:Bits](vector: Exp[Vector[W,T]], index: Int)(implicit ctx: SrcCtx): Exp[T] = vector match {
    case Op(ListVector(elems)) =>
      if (index < 0 || index >= elems.length) {
        new InvalidVectorApplyIndex(vector, index)
        fresh[T]
      }
      else elems(index)
    case _ =>
      stage(VectorApply(vector, index))(ctx)
  }

  private[spatial] def vector_slice[W:INT,WW:INT,T:Type:Bits](vector: Exp[Vector[W,T]], start: Int, end: Int)(implicit ctx: SrcCtx): Exp[Vector[WW,T]] = vector match {
    case Op(ListVector(elems)) =>
      if (start >= end) {
        new InvalidVectorSlice(vector, start, end)
        fresh[Vector[WW,T]]
      }
      else {
        val from = Math.max(0, start)
        val until = Math.min(elems.length, end)
        vector_new[WW,T](elems.slice(from, until))
      }
    case _ =>
      stage(VectorSlice[W,WW,T](vector, start, end))(ctx)
  }

  private[spatial] def lenOf(x: Exp[_])(implicit ctx: SrcCtx): Int = x.tp match {
    case tp: VectorType[_,_] => tp.width
    case _ => throw new UndefinedDimensionsError(x, None)
  }
}

// wooo
trait VectorConstructors { this: VectorExp =>

}