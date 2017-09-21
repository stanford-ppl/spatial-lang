package spatial.lang.static

import argon.core._
import forge._
import spatial.lang.BitOps._

trait BitOpsApi { this: SpatialApi =>

  // TODO: Proper way to define conversion ops on vars?
  implicit class VarDataConversionOps[A:Type:Bits](x: Var[A]) {
    @api def reverse: A = readVar(x).reverse
  }

  implicit class DataConversionOps[A:Type:Bits](x: A) {

    /**
      * Returns the given bit in this value.
      * 0 corresponds to the least significant bit (LSB).
      **/
    @api def apply(i: Int): Bit = dataAsBitVector(x).apply(i)

    /**
      * Returns a slice of the bits in this word as a VectorN.
      * The range must be statically determinable with a stride of 1.
      * The range is inclusive for both the start and end.
      * The range can be big endian (e.g. ``3::0``) or little endian (e.g. ``0::3``).
      * In both cases, element 0 is always the least significant element.
      *
      * For example, ``x(3::0)`` returns a Vector of the 4 least significant bits of ``x``.
      */
    @api def apply(range: MRange): BitVector = dataAsBitVector(x).apply(range)

    /**
      * Re-interprets this value's bits as the given type, without conversion.
      * If B has fewer bits than this value's type, the MSBs will be dropped.
      * If B has more bits than this value's type, the resulting MSBs will be zeros.
      */
    @api def as[B:Type:Bits]: B = {
      checkLengthMismatch[A,B]()
      val len = bits[B].length
      implicit val vT = VectorN.typeFromLen[Bit](len)
      val vector = dataAsBitVector(x)
      bitVectorAsData[B](vector, enWarn = true)
    }

    /** Returns a value of the same type with this value's bits in reverse order. **/
    @api def reverse: A = {
      val len = bits[A].length
      implicit val vT = VectorN.typeFromLen[Bit](len)
      val vector = dataAsBitVector(x)
      val reversed_elements = Range(0, len).map{i => Vector.select(vector.s, i) }
      val vector_reversed = wrap(Vector.fromseq[Bit,VectorN](reversed_elements))
      bitVectorAsData[A](vector_reversed, enWarn = true)
    }


    /**
      * Returns a slice of N bits of this value starting at the given `offset` from the LSB.
      * To satisfy Scala's static type analysis, each bit-width has a separate method.
      *
      * For example, ``x.take3(1)`` returns the 3 least significant bits of x after the LSB
      * as a Vector3[Bit].
      */
    @generate
    @api def takeJJ$JJ$1to128(offset: CInt): VectorJJ[Bit] = dataAsBitVector(x).takeJJ(offset)

    /**
      * Returns a slice of N bits of this value, starting at the given `offset` from the MSB.
      * To satisfy Scala's static type analysis, each bit-width has a separate method.
      *
      * For example, ``x.take3MSB(1)`` returns the 3 most significant bits of x after the MSB
      * as a Vector3[Bit].
      */
    @generate
    @api def takeJJMSB$JJ$1to128: VectorJJ[Bit] = {
      val offset = bits[A].length - JJ
      dataAsBitVector(x).takeJJ(offset)
    }

    /**
      * Returns a view of this value's bits as a N-bit Vector.
      * To satisfy Scala's static analysis, each bit-width has a separate method.
      * If N is smaller than this value's total bits, the MSBs will be dropped.
      * If N is larger than this value's total bits, the resulting MSBs will be zeros.
      */
    @generate
    @api def asJJb$JJ$1to128: VectorJJ[Bit] = this.as[VectorJJ[Bit]]
  }


  implicit class BitVectorOps(vector: BitVector) {
    @api def as[B:Type:Bits] = {
      implicit val vT = VectorN.typeFromLen[Bit](vector.width)
      bitVectorAsData[B](vector, enWarn = true)
    }

    @generate
    @api def asJJb$JJ$1to128: VectorJJ[Bit] = this.as[VectorJJ[Bit]]
  }
}