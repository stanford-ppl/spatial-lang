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
    @api def apply(i: Int): Bit = dataAsBitVector(x).apply(i)
    @api def apply(range: MRange): BitVector = dataAsBitVector(x).apply(range)

    @api def as[B:Type:Bits]: B = {
      checkLengthMismatch[A,B]()
      val len = bits[B].length
      implicit val vT = VectorN.typeFromLen[Bit](len)
      val vector = dataAsBitVector(x)
      bitVectorAsData[B](vector, enWarn = true)
    }

    @api def reverse: A = {
      val len = bits[A].length
      implicit val vT = VectorN.typeFromLen[Bit](len)
      val vector = dataAsBitVector(x)
      val reversed_elements = Range(0, len).map{i => Vector.select(vector.s, i) }
      val vector_reversed = wrap(Vector.fromseq[Bit,VectorN](reversed_elements))
      bitVectorAsData[A](vector_reversed, enWarn = true)
    }


    // takeN(offset) - creates a VectorN slice starting at given little-endian offset
    @generate
    @api def takeJJ$JJ$1to128(offset: CInt): VectorJJ[Bit] = dataAsBitVector(x).takeJJ(offset)

    @generate
    @api def takeJJMSB$JJ$1to128: VectorJJ[Bit] = {
      val offset = bits[A].length - JJ
      dataAsBitVector(x).takeJJ(offset)
    }


    // asNb - converts this to VectorN bits (includes bit length mismatch checks)
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