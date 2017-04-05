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

  @api def Vector$JJ$1to128[T:Meta:Bits](xII$II$1toJJ: T)(implicit ctx: SrcCtx): VectorJJ[T] = {
    //type V[T] = VectorJJ[T]
    val eII$II$1toJJ = xII.s
    wrap(vector_new[T,VectorJJ](Seq(eII$II$1toJJ)))
  }

  type VectorJJ$JJ$1to128[T] = Vec[_JJ,T]

  case class Vec[N:INT,T:Meta:Bits](s: Exp[Vec[N,T]]) extends MetaAny[Vec[N,T]] with Vector[T] {
    val width = INT[N].v
    @api def apply(i: Int): T = wrap(vector_apply(s,i))

    @api def ===(that: Vec[N,T]): Bool = reduceTree(Seq.tabulate(width) { i =>
      this.apply(i) === that.apply(i)
    }){(a,b) => a && b }

    @api def =!=(that: Vec[N,T]): Bool = reduceTree(Seq.tabulate(width){ i =>
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

  case class VecType[N:INT,T:Bits](child: Meta[T]) extends Meta[Vec[N,T]] with VectorType[T] with CanBits[Vec[N,T]] {
    val width: Int = INT[N].v
    override def wrapped(x: Exp[Vec[N,T]]) = Vec[N,T](x)(INT[N], child, bits[T])
    override def typeArguments = List(child)
    override def stagedClass = classOf[Vec[N,T]]
    protected def getBits(children: Seq[Meta[_]]): Option[Bits[Vec[N,T]]] = Some(vectorBits[N,T](INT[N],child,bits[T]))
  }
  implicit def vectorType[N:INT,T:Meta:Bits]: Meta[Vec[N,T]] = VecType[N,T](meta[T])

  trait VectorBits[T] {
    def width: Int
    def bT: Bits[T]
    def length: Int = width * bT.length
  }

  class VecBits[N:INT,T:Meta:Bits] extends Bits[Vec[N,T]] with VectorBits[T] {
    type V[X] = Vec[N,X]
    val width: Int = INT[N].v
    val bT: Bits[T] = bits[T]
    def zero(implicit ctx: SrcCtx): V[T] = wrap(vector_new[T,V](Seq.fill(width){ bT.zero.s }))
    def one(implicit ctx: SrcCtx): V[T] = wrap(vector_new[T,V](Seq.fill(width){ bT.one.s }))
    def random(max: Option[V[T]])(implicit ctx: SrcCtx) = {
      val maxes = Seq.tabulate(width) { i => bT.random(max.map(_.apply(i))).s }
      wrap(vector_new[T, V](maxes))
    }
  }

  implicit def vectorBits[N:INT,T:Meta:Bits]: Bits[Vec[N,T]] = new VecBits[N,T]


  // This type is a bit of a hack (but a very useful hack) to get around the fact that we often can't statically
  // say how large a given Vector will be. Since this type doesn't have implicit Type or Bits evidence, users
  // will have to explicitly convert this type to a Vector## type for most operations.
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

  private[spatial] def vectorNType[T:Type:Bits](len: Int): Type[VectorN[T]] = new Type[VectorN[T]] with VectorType[T] with CanBits[VectorN[T]] {
    def width: Int = len
    def child = typ[T]
    override def wrapped(x: Exp[VectorN[T]]) = VectorN(width,x)(child,bits[T],this)
    override def typeArguments = List(child)
    override def stagedClass = classOf[VectorN[T]]
    override def isPrimitive = false
    protected def getBits(children: Seq[Meta[_]]): Option[Bits[VectorN[T]]] = Some(vectorNBits(len, this)(child,bits[T]))
  }
  private[spatial] def vectorNBits[T:Type:Bits](len: Int, vT: Type[VectorN[T]]): Bits[VectorN[T]] = new Bits[VectorN[T]] with VectorBits[T] {
    val width: Int = len
    val bT: Bits[T] = bits[T]
    override def zero(implicit ctx: SrcCtx): VectorN[T] = {
      val zeros = Seq.fill(width){ bT.zero.s }
      vT.wrapped(vector_new[T,VectorN](zeros)(typ[T],bT,ctx,vT))
    }
    override def one(implicit ctx: SrcCtx): VectorN[T] = {
      val ones = Seq.fill(width){ bT.one.s }
      vT.wrapped(vector_new[T,VectorN](ones)(typ[T],bT,ctx,vT))
    }
    override def random(max: Option[VectorN[T]])(implicit ctx: SrcCtx) = {
      val maxes = Seq.tabulate(width){i => bT.random(max.map(_.apply(i))).s }
      vT.wrapped(vector_new[T,VectorN](maxes)(typ[T],bT,ctx,vT))
    }
  }


  /** IR Nodes **/
  case class ListVector[T:Type:Bits,V[_]<:Vector[_]](elems: Seq[Exp[T]])(implicit vT: Type[V[T]]) extends Op[V[T]] {
    def mirror(f:Tx) = vector_new[T,V](f(elems))
  }
  case class VectorApply[T:Type](vector: Exp[Vector[T]], index: Int) extends Op[T] {
    def mirror(f:Tx) = vector_apply(f(vector), index)
  }
  case class VectorSlice[T:Type:Bits,V[_]<:Vector[_]](vector: Exp[Vector[T]], end: Int, start: Int)(implicit vT: Type[V[T]]) extends Op[V[T]] {
    def mirror(f:Tx) = vector_slice[T,V](f(vector), end, start)
  }
  case class VectorConcat[T:Type:Bits,V[_]<:Vector[_]](vectors: Seq[Exp[Vector[T]]])(implicit vT: Type[V[T]]) extends Op[V[T]] {
    def mirror(f:Tx) = vector_concat[T,V](f(vectors))
  }

  /** Constructors **/
  private[spatial] def vector_new[T:Type:Bits,V[_]<:Vector[_]](elems: Seq[Exp[T]])(implicit ctx: SrcCtx, vT: Type[V[T]]): Exp[V[T]] = {
    stage(ListVector[T,V](elems))(ctx)
  }

  private[spatial] def vector_concat[T:Type:Bits,V[_]<:Vector[_]](vectors: Seq[Exp[Vector[T]]])(implicit ctx: SrcCtx, vT: Type[V[T]]): Exp[V[T]] = {
    stage(VectorConcat[T,V](vectors))(ctx)
  }

  private[spatial] def vector_apply[T:Type](vector: Exp[Vector[T]], index: Int)(implicit ctx: SrcCtx): Exp[T] = vector match {
    case Op(ListVector(elems)) =>
      if (index < 0 || index >= elems.length) {
        new InvalidVectorApplyIndex(vector, index)
        fresh[T]
      }
      else elems(elems.length - 1 - index)  // Little endian
    case _ =>
      // Attempt to give errors about out of bounds applies
      vector.tp match {
        case Bits(bV) =>
          if (index < 0 || index >= bV.length)
            new InvalidVectorApplyIndex(vector, index)
        case _ =>
          // Risky, but ignore warnings for now
      }
      stage(VectorApply(vector, index))(ctx)
  }

  private[spatial] def vector_slice[T:Type:Bits,V[_]<:Vector[_]](vector: Exp[Vector[T]], end: Int, start: Int)(implicit ctx: SrcCtx, vT: Type[V[T]]): Exp[V[T]] = vector match {
    case Op(ListVector(elems)) =>
      if (start >= end) {
        new InvalidVectorSlice(vector, start, end)
        fresh[V[T]]
      }
      else {
        val (from,until) = littleToBig(end,start,elems.length)
        vector_new[T,V](elems.slice(from, until))
      }
    case _ =>
      stage(VectorSlice[T,V](vector, end, start))(ctx)
  }

  private[spatial] def lenOf(x: Exp[_])(implicit ctx: SrcCtx): Int = x.tp match {
    case tp: VectorType[_] => tp.width
    case _ => throw new UndefinedDimensionsError(x, None)
  }


  def littleToBig(end: Int, start: Int, length: Int) = {
    val from = length - 1 - end
    val until = length - start // end is non-inclusive
    (from,until)
  }

}
