package spatial.api

import argon.core.Staging
import argon.ops.TextExp
import argon.typeclasses.{BitsExp, CustomBitWidths}
import spatial.SpatialExp
import forge._

trait VectorApi extends VectorExp { this: SpatialExp => }

@generate
trait VectorExp extends Staging with BitsExp with TextExp with CustomBitWidths {
  this: SpatialExp =>

  /** Infix methods **/
  trait Vector[T] { this: MetaAny[_] =>
    def s: Exp[Vector[T]]
    def width: Int
    @api def apply(i: Int): T
  }

  object Vector {
    def apply$JJ$1to2[T:Meta:Bits](xII$II$1toJJ: T)(implicit ctx: SrcCtx): VectorJJ[T] = {
      type V[T] = VectorJJ[T]
      val eII$II$1toJJ = xII.s
      wrap(vector_new[T,V](Seq(eII$II$1toJJ)))
    }
  }

  case class VectorJJ$JJ$1to2[T:Meta:Bits](s: Exp[VectorJJ[T]]) extends MetaAny[VectorJJ[T]] with Vector[T] {
    val width: Int = JJ
    @api def apply(i: Int): T = wrap(vector_apply(s,i))

    @api def ===(that: VectorJJ[T]): Bool = reduceTree(Seq.tabulate(width) { i =>
      this.apply(i) === that.apply(i)
    }){(a,b) => a && b }

    @api def =!=(that: VectorJJ[T]): Bool = reduceTree(Seq.tabulate(width){ i =>
      this.apply(i) =!= that.apply(i)
    }){(a,b) => a || b}

    @api def toText = textify(this)
  }

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

  case class VectorJJType$JJ$1to2[T:Bits](child: Meta[T]) extends Meta[VectorJJ[T]] with VectorType[T] with CanBits[VectorJJ[T]] {
    val width: Int = JJ
    override def wrapped(x: Exp[VectorJJ[T]]) = VectorJJ(x)(child, bits[T])
    override def typeArguments = List(child)
    override def stagedClass = classOf[VectorJJ[T]]
    protected def getBits(children: Seq[Meta[_]]): Option[Bits[VectorJJ[T]]] = Some(new VectorJJBits[T]()(child,bits[T]))
  }
  implicit def vectorJJType$JJ$1to2[T:Meta:Bits]: Meta[VectorJJ[T]] = VectorJJType[T](meta[T])

  trait VectorBits[T] {
    def width: Int
    def bT: Bits[T]
    def length: Int = width * bT.length
  }

  class VectorJJBits$JJ$1to2[T:Meta:Bits] extends Bits[VectorJJ[T]] with VectorBits[T] {
    val width: Int = JJ
    val bT: Bits[T] = bits[T]
    override def zero(implicit ctx: SrcCtx): VectorJJ[T] = Vector(bT.zero$II$1toJJ)
    override def one(implicit ctx: SrcCtx): VectorJJ[T] = Vector(bits[T].one$II$1toJJ)
    override def random(max: Option[VectorJJ[T]])(implicit ctx: SrcCtx) = {
      val randII$II$1toJJ = bT.random(max.map(_.apply(II)))
      Vector(randII$II$1toJJ)
    }
  }

  implicit def vectorJJBits$JJ$1to2[T:Meta:Bits]: Bits[VectorJJ[T]] = new VectorJJBits[T]


  // HACK: This isn't supposed to be user facing, so it really shouldn't have a frontend type
  // This is required to create the internal type right now, since Meta and Type are linked
  case class VectorN[T:Meta:Bits](width: Int, s: Exp[VectorN[T]])(implicit myType: Meta[VectorN[T]]) extends MetaAny[VectorN[T]] with Vector[T] {
    @api def apply(i: Int): T = wrap(vector_apply(s,i))
    @api def ===(that: VectorN[T]): Bool = reduceTree(Seq.tabulate(width) { i =>
      this.apply(i) === that.apply(i)
    }){(a,b) => a && b }
    @api def =!=(that: VectorN[T]): Bool = reduceTree(Seq.tabulate(width){ i =>
      this.apply(i) =!= that.apply(i)
    }){(a,b) => a || b}
    @api def toText = textify(this)
  }

  @internal def VecType[T:Type:Bits](len: Int): Type[VectorN[T]] = new Type[VectorN[T]] with VectorType[T] {
    def width: Int = len
    def child = typ[T]
    override def wrapped(x: Exp[VectorN[T]]) = VectorN(width,x)(child,bits[T],this)
    override def typeArguments = List(child)
    override def stagedClass = classOf[VectorN[T]]
    override def isPrimitive = false
  }


  /** IR Nodes **/
  case class ListVector[T:Type:Bits,V[_]<:Vector[_]](elems: Seq[Exp[T]])(implicit vT: Type[V[T]]) extends Op[V[T]] {
    def mirror(f:Tx) = vector_new[T,V](f(elems))
  }
  case class VectorApply[T:Type](vector: Exp[Vector[T]], index: Int) extends Op[T] {
    def mirror(f:Tx) = vector_apply(f(vector), index)
  }
  case class VectorSlice[T:Type:Bits,V[_]<:Vector[_]](vector: Exp[Vector[T]], start: Int, end: Int)(implicit vT: Type[V[T]]) extends Op[V[T]] {
    def mirror(f:Tx) = vector_slice[T,V](f(vector), start, end)
  }

  /** Constructors **/
  private[spatial] def vector_new[T:Type:Bits,V[_]<:Vector[_]](elems: Seq[Exp[T]])(implicit ctx: SrcCtx, vT: Type[V[T]]): Exp[V[T]] = {
    stage(ListVector[T,V](elems))(ctx)
  }

  private[spatial] def vector_apply[T:Type](vector: Exp[Vector[T]], index: Int)(implicit ctx: SrcCtx): Exp[T] = vector match {
    case Op(ListVector(elems)) =>
      if (index < 0 || index >= elems.length) {
        new InvalidVectorApplyIndex(vector, index)
        fresh[T]
      }
      else elems(index)
    case _ =>
      stage(VectorApply(vector, index))(ctx)
  }

  private[spatial] def vector_slice[T:Type:Bits,V[_]<:Vector[_]](vector: Exp[Vector[T]], start: Int, end: Int)(implicit ctx: SrcCtx, vT: Type[V[T]]): Exp[V[T]] = vector match {
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
}
