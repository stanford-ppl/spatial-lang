package spatial.api

import argon.core.Staging
import argon.ops.TextExp
import argon.typeclasses.{BitsExp, CustomBitWidths}
import spatial.SpatialExp

import forge.generate

trait VectorApi extends VectorExp { this: SpatialExp => }

@generate
trait VectorExp extends Staging with BitsExp with TextExp with CustomBitWidths {
  this: SpatialExp =>

  def VecType[T:Meta:Bits](w: Int): Type[Vector[T]] = new Meta[Vector[T]] with VectorType[T] with CanBits[Vector[T]] { self =>
    val width: Int = w
    val child: Meta[T] = meta[T]
    override def wrapped(x: Exp[Vector[T]]): Vector[T] = new VectorN[T](w,x)(child,bits[T],self)
    override def typeArguments = List(child)
    override def stagedClass = classOf[Vector[T]]
    override def isPrimitive = false
    protected def getBits(children: Seq[Meta[_]]): Option[Bits[Vector[T]]] = Some(VecBits[T](w))
  }
  def VecBits[T:Meta:Bits](w: Int): Bits[Vector[T]] = new Bits[Vector[T]] {
    val width: Int = w
    val bT: Bits[T] = bits[T]
    override def zero(implicit ctx: SrcCtx): Vector[T] = VecType(w).wrapped(vectorize(Seq.fill(w){bT.zero.s}))
    override def one(implicit ctx: SrcCtx): Vector[T] = VecType(w).wrapped(vectorize(Seq.fill(w){bT.one.s}))
    override def random(max: Option[Vector[T]])(implicit ctx: SrcCtx) = {
      def rand(i: Int): T = bT.random(max.map(_.apply(i)))
      VecType(w).wrapped(vectorize(Seq.tabulate(w){i => rand(i).s }))
    }
    override def length = width * bT.length
  }

  /** Infix methods **/
  abstract class Vec[T,V[_]](implicit vT: Meta[V[T]]) extends MetaAny[V[T]] {
    def width: Int
    def apply(i: Int)(implicit ctx: SrcCtx): T
  }

  type Vector[T] = VectorN[T]

  case class VectorN[T:Meta:Bits](width: Int, s: Exp[VectorN[T]])(implicit vT: Meta[VectorN[T]]) extends Vec[T,VectorN] {
    def apply(i: Int)(implicit ctx: SrcCtx): T = wrap(vector_apply[T,Vector](s,i))

    def ===(that: VectorN[T])(implicit ctx: SrcCtx): Bool = if (this.width == that.width) {
      reduceTree(Seq.tabulate(width) { i =>
        this.apply(i) === that.apply(i)
      }){(a,b) => a && b }
    }
    else false

    def =!=(that: VectorN[T])(implicit ctx: SrcCtx): Bool = if (this.width == that.width) {
      reduceTree(Seq.tabulate(width){ i =>
        this.apply(i) =!= that.apply(i)
      }){(a,b) => a || b}
    }
    else false

    def toText(implicit ctx: SrcCtx): Text = textify(this)
  }

  object Vector {
    def apply$II$1to2[T:Meta:Bits](xJJ$JJ$1toII: T)(implicit ctx: SrcCtx): VectorII[T] = {
      type V[T] = VectorII[T]
      val eJJ$JJ$1toII = xJJ.s
      wrap(vector_new[T,V](Seq(eJJ$JJ$1toII)))
    }
  }

  private[spatial] def vectorize[T:Meta:Bits](elems: Seq[Exp[T]])(implicit ctx: SrcCtx): Exp[Vector[T]] = {
    implicit val vT = VecType[T](elems.length)
    vector_new[T,Vector](elems)
  }

  /** Staged Types **/
  trait VectorType[T] {
    def child: Meta[T]
    def width: Int
  }


  /** IR Nodes **/
  case class ListVector[T:Type:Bits,V[T]](elems: Seq[Exp[T]])(implicit vT: Type[V[T]]) extends Op[V[T]] {
    def mirror(f:Tx) = vector_new[T,V](f(elems))
  }
  case class VectorApply[T:Type,V[T]](vector: Exp[V[T]], index: Int)(implicit vT: Type[V[T]]) extends Op[T] {
    def mirror(f:Tx) = vector_apply(f(vector), index)
  }
  case class VectorSlice[T:Type:Bits,V[T]](vector: Exp[Vector[T]], start: Int, end: Int)(implicit vT: Type[V[T]]) extends Op[V[T]] {
    def mirror(f:Tx) = vector_slice[T,V](f(vector), start, end)
  }

  /** Constructors **/
  private[spatial] def vector_new[T:Type:Bits,V[T]](elems: Seq[Exp[T]])(implicit ctx: SrcCtx, vT: Type[V[T]]): Exp[V[T]] = {
    stage(ListVector[T,V](elems))(ctx)
  }

  private[spatial] def vector_apply[T:Type,V[T]](vector: Exp[V[T]], index: Int)(implicit ctx: SrcCtx, vT: Type[V[T]]): Exp[T] = vector match {
    case Op(ListVector(elems)) =>
      if (index < 0 || index >= elems.length) {
        new InvalidVectorApplyIndex(vector, index)
        fresh[T]
      }
      else elems(index)
    case _ =>
      stage(VectorApply(vector, index))(ctx)
  }

  private[spatial] def vector_slice[T:Type:Bits,V[T]](vector: Exp[Vector[T]], start: Int, end: Int)(implicit ctx: SrcCtx, vT: Type[V[T]]): Exp[V[T]] = vector match {
    case Op(ListVector(elems)) =>
      if (start >= end) {
        new InvalidVectorSlice(vector, start, end)
        fresh[V[T]]
      }
      else {
        val from = Math.max(0, start)
        val until = Math.min(elems.length, end)
        vector_new[T,V](elems.slice(from, until))
      }
    case _ =>
      stage(VectorSlice[T,V](vector, start, end))(ctx)
  }

  private[spatial] def lenOf(x: Exp[_])(implicit ctx: SrcCtx): Int = x.tp match {
    case tp: VectorType[_] => tp.width
    case _ => throw new UndefinedDimensionsError(x, None)
  }


  case class VectorJJ$JJ$1to2[T:Meta:Bits](s: Exp[VectorJJ[T]]) extends Vec[T,VectorJJ] {
    val width: Int = JJ
    def apply(i: Int)(implicit ctx: SrcCtx): T = wrap(vector_apply[T,VectorJJ](s,i))
    def ===(that: VectorJJ[T])(implicit ctx: SrcCtx): Bool = {
      reduceTree(Seq.tabulate(width) { i =>
        this.apply(i) === that.apply(i)
      }){(a,b) => a && b }
    }

    def =!=(that: VectorJJ[T])(implicit ctx: SrcCtx): Bool = {
      reduceTree(Seq.tabulate(width){ i =>
        this.apply(i) =!= that.apply(i)
      }){(a,b) => a || b}
    }

    def toText(implicit ctx: SrcCtx) = textify(this)
  }

  case class VectorJJType$JJ$1to2[T:Bits](child: Meta[T]) extends Meta[VectorJJ[T]] with VectorType[T] with CanBits[VectorJJ[T]] {
    val width: Int = JJ
    override def wrapped(x: Exp[VectorJJ[T]]) = VectorJJ(x)(child, bits[T])
    override def typeArguments = List(child)
    override def stagedClass = classOf[VectorJJ[T]]
    override def isPrimitive = false
    protected def getBits(children: Seq[Meta[_]]): Option[Bits[VectorJJ[T]]] = Some(new VectorJJBits[T]()(child,bits[T]))
  }
  implicit def vectorJJType$JJ$1to2[T:Meta:Bits]: Meta[VectorJJ[T]] = VectorJJType[T](meta[T])

  class VectorJJBits$JJ$1to2[T:Meta:Bits] extends Bits[VectorJJ[T]] {
    val width: Int = JJ
    val bT: Bits[T] = bits[T]
    override def zero(implicit ctx: SrcCtx): VectorJJ[T] = Vector(bT.zero$II$1toJJ)
    override def one(implicit ctx: SrcCtx): VectorJJ[T] = Vector(bits[T].one$II$1toJJ)
    override def random(max: Option[VectorJJ[T]])(implicit ctx: SrcCtx) = {
      val randII$II$1toJJ = bT.random(max.map(_.apply(II)))
      Vector(randII$II$1toJJ)
    }
    override def length = width * bT.length
  }

  implicit def vectorJJBits$JJ$1to2[T:Meta:Bits]: Bits[VectorJJ[T]] = new VectorJJBits[T]

}
