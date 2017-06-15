package spatial.lang

import argon.internals._
import forge._
import spatial.nodes._

import scala.language.higherKinds

/** Infix methods **/
trait Vector[T] { this: MetaAny[_] =>
  def s: Exp[Vector[T]]
  def width: Int
  @api def apply(i: Int): T

  @api def apply(range: Range)(implicit mT: Type[T], bT: Bits[T]): VectorN[T] = {
    val wordLength = this.width
    val start = range.start.map(_.s).getOrElse(int32(wordLength-1))
    val end = range.end.s
    val step = range.step.map(_.s).getOrElse(int32(1))

    step match {
      case Const(s: BigDecimal) if s == 1 =>
      case _ =>
        error(ctx, "Strides for bit slicing are not currently supported.")
        error(ctx)
    }

    (start,end) match {
      case (Const(x1: BigDecimal), Const(x2: BigDecimal)) =>
        val msb = Math.max(x1.toInt, x2.toInt)
        val lsb = Math.min(x1.toInt, x2.toInt)
        val length = msb - lsb + 1
        implicit val vT = VectorN.typeFromLen[T](length)
        // Slice is inclusive on both
        // TODO: Why is .asInstanceOf required here?
        wrap(Vector.slice[T,VectorN](s.asInstanceOf[Exp[Vector[T]]], msb, lsb))

      case _ =>
        error(ctx, "Apply range for bit slicing must be statically known.")
        error(ctx, c"Found $start :: $end")
        error(ctx)
        implicit val vT = VectorN.typeFromLen[T](this.width)
        wrap(fresh[VectorN[T]])
    }
  }

  // @api def reverse()(implicit mT: Meta[T], bT: Bits[T]): VectorN[T] = { // TODO: Implementme
  // }

  // TODO: Why is .asInstanceOf required here?
  @generate
  @api def takeJJ$JJ$1to128(offset: Int)(implicit mT: Type[T], bT: Bits[T]): VectorJJ[T] = {
    wrap(Vector.slice[T,VectorJJ](s.asInstanceOf[Exp[Vector[T]]], offset+JJ-1, offset))
  }
}

object Vector {
  // Everything is represented in big-endian internally (normal Array order with 0 first)

  /** Static methods **/
  @generate
  @api def LittleEndian$JJ$1to128[T:Type:Bits](xII$II$1toJJ: T): VectorJJ[T] = {
    val eII$II$1toJJ = xII.s
    wrap(Vector.fromseq[T,VectorJJ](Seq(eII$II$1toJJ).reverse))
  }
  @generate
  @api def BigEndian$JJ$1to128[T:Type:Bits](xII$II$1toJJ: T): VectorJJ[T] = {
    val eII$II$1toJJ = xII.s
    wrap(Vector.fromseq[T,VectorJJ](Seq(eII$II$1toJJ)))
  }

  // Aliases for above (with method names I can immediately understand)
  @generate
  @api def ZeroLast$JJ$1to128[T:Type:Bits](xII$II$1toJJ: T): VectorJJ[T] = {
    val eII$II$1toJJ = xII.s
    wrap(Vector.fromseq[T,VectorJJ](Seq(eII$II$1toJJ).reverse))
  }
  @generate
  @api def ZeroFirst$JJ$1to128[T:Type:Bits](xII$II$1toJJ: T): VectorJJ[T] = {
    val eII$II$1toJJ = xII.s
    wrap(Vector.fromseq[T,VectorJJ](Seq(eII$II$1toJJ)))
  }


  /** Constructors **/
  @internal def fromseq[T:Type:Bits,V[_]<:Vector[_]](elems: Seq[Exp[T]])(implicit vT: Type[V[T]]): Exp[V[T]] = {
    stage(ListVector[T,V](elems))(ctx)
  }

  @internal def concat[T:Type:Bits,V[_]<:Vector[_]](vectors: Seq[Exp[Vector[T]]])(implicit vT: Type[V[T]]): Exp[V[T]] = {
    stage(VectorConcat[T,V](vectors))(ctx)
  }

  @internal def select[T:Type](vector: Exp[Vector[T]], index: Int): Exp[T] = vector match {
    case Op(ListVector(elems)) =>
      if (index < 0 || index >= elems.length) {
        new InvalidVectorApplyIndex(vector, index)
        fresh[T]
      }
      else elems(index)  // Little endian
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

  @internal def slice[T:Type:Bits,V[_]<:Vector[_]](vector: Exp[Vector[T]], end: Int, start: Int)(implicit vT: Type[V[T]]): Exp[V[T]] = vector match {
    case Op(ListVector(elems)) =>
      if (start >= end) {
        new InvalidVectorSlice(vector, start, end)
        fresh[V[T]]
      }
      else {
        Vector.fromseq[T,V](elems.slice(start, end+1)) // end is inclusive
      }
    case _ =>
      stage(VectorSlice[T,V](vector, end, start))(ctx)
  }
}

object Vectorize {
  @api def BigEndian[T:Type:Bits](elems: T*): VectorN[T] = {
    implicit val vT = VectorN.typeFromLen[T](elems.length)
    vT.wrapped(Vector.fromseq[T,VectorN](elems.map(_.s)))
  }
  @api def ZeroFirst[T:Type:Bits](elems: T*): VectorN[T] = {
    implicit val vT = VectorN.typeFromLen[T](elems.length)
    vT.wrapped(Vector.fromseq[T,VectorN](elems.map(_.s)))
  }


  @api def LittleEndian[T:Type:Bits](elems: T*): VectorN[T] = {
    implicit val vT = VectorN.typeFromLen[T](elems.length)
    vT.wrapped(Vector.fromseq[T,VectorN](elems.reverse.map(_.s)))
  }
  @api def ZeroLast[T:Type:Bits](elems: T*): VectorN[T] = {
    implicit val vT = VectorN.typeFromLen[T](elems.length)
    vT.wrapped(Vector.fromseq[T,VectorN](elems.reverse.map(_.s)))
  }
}

case class Vec[N:INT,T:Type:Bits](s: Exp[Vec[N,T]]) extends MetaAny[Vec[N,T]] with Vector[T] {
  val width = INT[N].v
  @api def apply(i: Int): T = wrap(Vector.select(s,i))

  @api def ===(that: Vec[N,T]): MBoolean = reduceTree(Seq.tabulate(width){i => this.apply(i) === that.apply(i) }){(a,b) => a && b }
  @api def =!=(that: Vec[N,T]): MBoolean = reduceTree(Seq.tabulate(width){i => this.apply(i) =!= that.apply(i) }){(a,b) => a || b }
  @api def toText = MString.ify(this)
}
object Vec {
  implicit def vecTyp[N:INT,T:Type:Bits]: Type[Vec[N,T]] = VecType[N,T](typ[T])
  implicit def vecBits[N:INT,T:Type:Bits]: Bits[Vec[N,T]] = new VecBits[N,T]
}

// This type is a bit of a hack (but a very useful hack) to get around the fact that we often can't statically
// say how large a given Vector will be. Since this type doesn't have implicit Type or Bits evidence, users
// will have to explicitly convert this type to a Vector## type for most operations.
case class VectorN[T:Type:Bits](width: Int, s: Exp[VectorN[T]])(implicit myType: Type[VectorN[T]]) extends MetaAny[VectorN[T]] with Vector[T] {
  @api def apply(i: Int): T = wrap(Vector.select(s,i))
  @api def ===(that: VectorN[T]): MBoolean = reduceTree(Seq.tabulate(width){i => this.apply(i) === that.apply(i) }){(a,b) => a && b }
  @api def =!=(that: VectorN[T]): MBoolean = reduceTree(Seq.tabulate(width){i => this.apply(i) =!= that.apply(i) }){(a,b) => a || b }
  @api def toText = MString.ify(this)

  @generate
  @api def asVectorJJ$JJ$1to128: VectorJJ[T] = {
    if (width != JJ) {
      implicit val bT: Bits[VectorN[T]] = VectorN.bitsFromLen[T](width, myType)
      DataConversionOps(this).as[VectorJJ[T]]
    }
    else wrap(s.asInstanceOf[Exp[VectorJJ[T]]])
  }
}
object VectorN {
  def typeFromLen[T:Type:Bits](len: Int): Type[VectorN[T]] = new Type[VectorN[T]] with VectorType[T] with CanBits[VectorN[T]] {
    def width: Int = len
    def child = typ[T]
    override def wrapped(x: Exp[VectorN[T]]) = VectorN(width,x)(child,bits[T],this)
    override def typeArguments = List(child)
    override def stagedClass = classOf[VectorN[T]]
    override def isPrimitive = false
    protected def getBits(children: Seq[Type[_]]): Option[Bits[VectorN[T]]] = Some(bitsFromLen(len, this)(child,bits[T]))
  }

  def bitsFromLen[T:Type:Bits](len: Int, vT: Type[VectorN[T]]): Bits[VectorN[T]] = new Bits[VectorN[T]] with VectorBits[T] {
    val width: Int = len
    val bT: Bits[T] = bits[T]
    private implicit val vvT: Type[VectorN[T]] = vT

    @api override def zero: VectorN[T] = {
      val zeros = Seq.fill(width){ bT.zero.s }
      vT.wrapped(Vector.fromseq[T,VectorN](zeros))
    }
    @api override def one: VectorN[T] = {
      val ones = Seq.fill(width){ bT.one.s }
      vT.wrapped(Vector.fromseq[T,VectorN](ones))
    }
    @api override def random(max: Option[VectorN[T]]) = {
      val maxes = Seq.tabulate(width){i => bT.random(max.map(_.apply(i))).s }
      vT.wrapped(Vector.fromseq[T,VectorN](maxes))
    }
  }

}


trait LowPriorityVectorImplicits {
  implicit def vectorNFakeType[T:Type:Bits](implicit ctx: SrcCtx): Type[VectorN[T]] = {
    error(ctx, u"VectorN value cannot be used directly as a staged type")
    error("Add a type conversion here using .asVector#, where # is the length of the vector")
    error(ctx)
    VectorN.typeFromLen[T](-1)
  }
}
trait VectorApi extends LowPriorityVectorImplicits
