package spatial.lang

import argon.core._
import forge._
import spatial.nodes._

import scala.language.higherKinds

/** Infix methods **/
trait Vector[T] { this: MetaAny[_] =>
  def s: Exp[Vector[T]]
  def width: Int

  /**
    * Returns the `i`'th element of this Vector.
    * Element 0 is always the least significant element.
    */
  @api def apply(i: Int): T

  /**
    * Returns a slice of the elements in this Vector as a VectorN.
    * The range must be statically determinable with a stride of 1.
    * The range is inclusive for both the start and end.
    * The `range` can be big endian (e.g. ``3::0``) or little endian (e.g. ``0::3``).
    * In both cases, element 0 is always the least significant element.
    *
    * For example, ``x(3::0)`` returns a Vector of the 4 least significant elements of ``x``.
    */
  @api def apply(range: Range)(implicit mT: Type[T], bT: Bits[T]): VectorN[T] = {
    val wordLength = this.width
    val start: Exp[Index] = range.start.map(_.s).getOrElse(int32s(wordLength-1))
    val end: Exp[Index] = range.end.s
    val step: Exp[Index] = range.step.map(_.s).getOrElse(int32s(1))

    step match {
      case Literal(s) if s == 1 =>
      case _ =>
        error(ctx, "Strides for bit slicing are not currently supported.")
        error(ctx)
    }

    (start,end) match {
      case (Literal(x1), Literal(x2)) =>
        val msb = java.lang.Math.max(x1.toInt, x2.toInt)
        val lsb = java.lang.Math.min(x1.toInt, x2.toInt)
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

  /**
    * Returns a slice of N elements of this Vector starting at the given `offset` from the
    * least significant element.
    * To satisfy Scala's static type analysis, each width has a separate method.
    *
    * For example, ``x.take3(1)`` returns the 3 least significant elements of x after the
    * least significant as a Vector3[T].
    */
  @generate
  @api def takeJJ$JJ$1to128(offset: Int)(implicit mT: Type[T], bT: Bits[T]): VectorJJ[T] = {
    wrap(Vector.slice[T,VectorJJ](s.asInstanceOf[Exp[Vector[T]]], offset+JJ-1, offset))
  }

  // @api def reverse()(implicit mT: Meta[T], bT: Bits[T]): VectorN[T] = { // TODO: Implementme
  // }



}

object Vector {
  // Everything is represented in big-endian internally (normal Array order with 0 first)

  /**
    * Creates a VectorX from the given X elements, where X is between 1 and 128.
    * The first element supplied is the most significant (Vector index of X - 1).
    * The last element supplied is the least significant (Vector index of 0).
    *
    * Note that this method is actually overloaded 128 times based on the number of supplied arguments.
    **/
  @generate
  @api def LittleEndian$JJ$1to128[T:Type:Bits](xII$II$1toJJ: T): VectorJJ[T] = {
    val eII$II$1toJJ = xII.s
    wrap(Vector.fromseq[T,VectorJJ](Seq(eII$II$1toJJ).reverse))
  }

  /**
    * Creates a VectorX from the given X elements, where X is between 1 and 128.
    * The first element supplied is the least significant (Vector index of 0).
    * The last element supplied is the most significant (Vector index of X - 1).
    *
    * Note that this method is actually overloaded 128 times based on the number of supplied arguments.
    **/
  @generate
  @api def BigEndian$JJ$1to128[T:Type:Bits](xII$II$1toJJ: T): VectorJJ[T] = {
    val eII$II$1toJJ = xII.s
    wrap(Vector.fromseq[T,VectorJJ](Seq(eII$II$1toJJ)))
  }

  // Aliases for above (with method names I can immediately understand)

  /** A mnemonic for LittleEndian (with reference to the zeroth element being specified last in order). **/
  @generate
  @api def ZeroLast$JJ$1to128[T:Type:Bits](xII$II$1toJJ: T): VectorJJ[T] = {
    val eII$II$1toJJ = xII.s
    wrap(Vector.fromseq[T,VectorJJ](Seq(eII$II$1toJJ).reverse))
  }

  /** A mnemonic for BigEndian (with reference to the zeroth element being specified first in order). **/
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
        new spatial.InvalidVectorApplyIndex(vector, index)
        fresh[T]
      }
      else elems(index)  // Little endian
    case _ =>
      // Attempt to give errors about out of bounds applies
      vector.tp match {
        case Bits(bV) =>
          if (index < 0 || index >= bV.length)
            new spatial.InvalidVectorApplyIndex(vector, index)
        case _ =>
        // Risky, but ignore warnings for now
      }
      stage(VectorApply(vector, index))(ctx)
  }

  @internal def slice[T:Type:Bits,V[_]<:Vector[_]](vector: Exp[Vector[T]], end: Int, start: Int)(implicit vT: Type[V[T]]): Exp[V[T]] = vector match {
    case Op(ListVector(elems)) =>
      if (start >= end) {
        new spatial.InvalidVectorSlice(vector, start, end)
        fresh[V[T]]
      }
      else {
        Vector.fromseq[T,V](elems.slice(start, end+1)) // end is inclusive
      }
    case _ =>
      stage(VectorSlice[T,V](vector, end, start))(ctx)
  }

  @internal def sliceN[T:Type:Bits](vector: Vector[T], end: Int, start: Int): VectorN[T] = {
    implicit val vT = VectorN.typeFromLen[T](end-start+1)
    vT.wrapped(slice[T,VectorN](vector.s, end, start))
  }
  @internal def concatN[T:Type:Bits](vectors: Seq[Vector[T]]): VectorN[T] = {
    val length = vectors.map(_.width).sum
    implicit val vT = VectorN.typeFromLen[T](length)
    vT.wrapped(concat[T,VectorN](vectors.map(_.s)))
  }

}

object Vectorize {
  /**
    * Creates a VectorN from the given elements.
    * The first element supplied is the least significant (Vector index of 0).
    * The last element supplied is the most significant (Vector index of N - 1).
    **/
  @api def BigEndian[T:Type:Bits](elems: T*): VectorN[T] = {
    implicit val vT = VectorN.typeFromLen[T](elems.length)
    vT.wrapped(Vector.fromseq[T,VectorN](elems.map(_.s)))
  }
  /** A mnemonic for BigEndian (with reference to the zeroth element being specified first in order). **/
  @api def ZeroFirst[T:Type:Bits](elems: T*): VectorN[T] = {
    implicit val vT = VectorN.typeFromLen[T](elems.length)
    vT.wrapped(Vector.fromseq[T,VectorN](elems.map(_.s)))
  }

  /**
    * Creates a VectorN from the given elements.
    * The first element supplied is the most significant (Vector index of N - 1).
    * The last element supplied is the least significant (Vector index of 0).
    **/
  @api def LittleEndian[T:Type:Bits](elems: T*): VectorN[T] = {
    implicit val vT = VectorN.typeFromLen[T](elems.length)
    vT.wrapped(Vector.fromseq[T,VectorN](elems.reverse.map(_.s)))
  }

  /** A mnemonic for LittleEndian (with reference to the zeroth element being specified last in order). **/
  @api def ZeroLast[T:Type:Bits](elems: T*): VectorN[T] = {
    implicit val vT = VectorN.typeFromLen[T](elems.length)
    vT.wrapped(Vector.fromseq[T,VectorN](elems.reverse.map(_.s)))
  }
}

case class Vec[N:INT,T:Type:Bits](s: Exp[Vec[N,T]]) extends MetaAny[Vec[N,T]] with Vector[T] {
  val width = INT[N].v
  /**
    * Returns the `i`'th element of this Vector.
    * Element 0 is always the LSB.
    */
  @api def apply(i: Int): T = wrap(Vector.select(s,i))

  /** Returns true if this Vector and `that` contain the same elements, false otherwise. **/
  @api def ===(that: Vec[N,T]): MBoolean = Math.reduceTree(Seq.tabulate(width){i => this.apply(i) === that.apply(i) }){(a,b) => a && b }

  /** Returns true if this Vector and `that` differ by at least one element, false otherwise. **/
  @api def =!=(that: Vec[N,T]): MBoolean = Math.reduceTree(Seq.tabulate(width){i => this.apply(i) =!= that.apply(i) }){(a,b) => a || b }

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

  /**
    * Returns the `i`'th element of this Vector.
    * Element 0 is always the LSB.
    */
  @api def apply(i: Int): T = wrap(Vector.select(s,i))

  /** Returns true if this Vector and `that` contain the same elements, false otherwise. **/
  @api def ===(that: VectorN[T]): MBoolean = Math.reduceTree(Seq.tabulate(width){i => this.apply(i) === that.apply(i) }){(a,b) => a && b }

  /** Returns true if this Vector and `that` differ by at least one element, false otherwise. **/
  @api def =!=(that: VectorN[T]): MBoolean = Math.reduceTree(Seq.tabulate(width){i => this.apply(i) =!= that.apply(i) }){(a,b) => a || b }

  @api def toText = MString.ify(this)

  /**
    * Casts this VectorN as a VectorX.
    * Values of X from 1 to 128 are currently supported.
    *
    * If the VectorX type has fewer elements than this value's type, the most significant elements will be dropped.
    * If the VectorX type has more elements than this value's type, the resulting elements will be zeros.
    **/
  @generate
  @api def asVectorJJ$JJ$1to128: VectorJJ[T] = {
    if (width != JJ) {
      implicit val bT: Bits[VectorN[T]] = VectorN.bitsFromLen[T](width, myType)
      new DataConversionOps(this).as[VectorJJ[T]]
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
