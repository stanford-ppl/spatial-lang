package spatial.lang

import argon.core._
import argon.nodes._
import spatial.nodes._
import forge._

object BitOps {
  /** Constructors **/
  @internal def data_as_bits[A:Type:Bits](a: Exp[A])(implicit mV: Type[BitVector]): Exp[BitVector] = stage(DataAsBits[A](a))(ctx)
  @internal def bits_as_data[A:Type:Bits](a: Exp[BitVector]): Exp[A] = stage(BitsAsData[A](a,typ[A]))(ctx)

  @internal def dataAsBitVector[A:Type:Bits](x: A)(implicit ctx: SrcCtx): BitVector = typ[A] match {
    case tp: StructType[_] =>
      val fieldTypes = tp.fields.map{case (name,mT) => mT }
      val fields = tp.ev(x).fields.map(_._2)
      val fieldBits = fields.zip(fieldTypes).map{case (field,mT@Bits(bT)) =>
        dataAsBitVector(field)(mtyp(mT),mbits(bT),ctx,state).s
      }
      val width = fieldTypes.map{case Bits(bT) => bT.length }.sum
      implicit val vT = VectorN.typeFromLen[Bit](width)
      wrap(Vector.concat[Bit,VectorN](fieldBits)) // big endian (List)

    case tp: VectorType[_] =>
      val width = bits[A].length
      implicit val vT = VectorN.typeFromLen[Bit](width)

      // HACK: Specialize for converting from other types of vectors of bits
      if (tp.child == BooleanType) {
        val sym = x.s.asInstanceOf[Exp[VectorN[Bit]]]
        vT.wrapped(sym)
      }
      else {
        val vector = x.asInstanceOf[Vector[_]]
        val mT = tp.child
        val Bits(bT) = mT
        val elems = List.tabulate(tp.width) { i => dataAsBitVector(vector.apply(i))(mtyp(mT), mbits(bT), ctx, state).s }
        wrap(Vector.concat[Bit,VectorN](elems))   // big endian (List)
      }

    case _ =>
      val len = bits[A].length
      implicit val vT = VectorN.typeFromLen[Bit](len)
      wrap(data_as_bits(x.s))
  }

  @internal def bitVectorSliceOrElseZero[A:Type:Bits](x: BitVector, offset: Int)(implicit ctx: SrcCtx): Exp[A] = {
    val mT = typ[A]
    val bT = bits[A]
    val vecSize = x.width
    val length = bT.length
    implicit val fvT: Type[VectorN[Bit]] = VectorN.typeFromLen[Bit](length)

    if (offset < vecSize && length+offset-1 < vecSize) {
      val fieldBits = fvT.wrapped(Vector.slice(x.s, length + offset - 1, offset))
      val field = bitVectorAsData(fieldBits, enWarn = false)(mT, bT, fvT, ctx, state)
      mT.unwrapped(field)
    }
    else if (offset >= vecSize) {
      mT.unwrapped(bT.zero)
    }
    else {
      val max = vecSize - 1
      val remain = length + offset - vecSize    // e.g. asked for 34::5 (30 bits) of 32b number, should have 3 bits left
      val fvT2 = VectorN.typeFromLen[Bit](remain)
      val fvB2 = VectorN.bitsFromLen[Bit](remain,fvT2)

      val trueBits = Vector.slice(x.s, max, offset)
      val zeroBits = fvB2.zero.s
      // Note that this is big-endian concatenation (everything internally is big-endian)
      val fieldBits = fvT.wrapped(Vector.concat[Bit,VectorN](Seq(trueBits,zeroBits))(typ[Bit],bits[Bit],fvT,ctx,state))

      val field = bitVectorAsData(fieldBits, enWarn = false)(mT, bT, fvT, ctx, state)
      mT.unwrapped(field)
    }
  }

  @internal def bitVectorAsData[B:Type:Bits](x: BitVector, enWarn: Boolean)(implicit vT: Type[BitVector]): B = {
    val Bits(bT) = vT
    if (enWarn) checkLengthMismatch()(vT,bT,typ[B],bits[B],ctx,state)
    val vecSize = x.width

    typ[B] match {
      case tp: StructType[_] =>
        val fieldNames = tp.fields.map{case (name,_) => name }
        val fieldTypes = tp.fields.map{case (_, mT) => mT }
        val sizes = tp.fields.map{case (_, mT@Bits(bT)) => bT.length }
        val offsets = List.tabulate(sizes.length){i => sizes.drop(i+1).sum }

        val fields = (fieldTypes,offsets).zipped.map{case (mT@Bits(bT),offset) =>
          bitVectorSliceOrElseZero(x,offset)(mtyp(mT),mbits(bT),ctx,state)
        }
        val namedFields = fieldNames.zip(fields)

        implicit val sT: StructType[B] = tp.asInstanceOf[StructType[B]]
        Struct[B](namedFields:_*)

      case tp: VectorType[_] =>
        // HACK: Specialize for converting to other vectors of bits
        // Assumes that Vector* has the same underlying representation as VectorN
        if (tp.child == BooleanType) {
          val sym = x.s.asInstanceOf[Exp[B]]
          tp.wrapped(sym)
        }
        else {
          val width = tp.width
          val mT = tp.child
          val Bits(bT) = mT
          val elems = List.tabulate(width) { i =>
            val offset = i * bT.length
            val length = bT.length
            bitVectorSliceOrElseZero(x, offset)(mtyp(mT), mbits(bT), ctx, state)
          }
          tp.wrapped(Vector.fromseq(elems)(mtyp(mT), mbits(bT), mtyp(tp), ctx, state).asInstanceOf[Exp[B]])
        }

      case _ => wrap(bits_as_data[B](x.s))
    }
  }


  @internal def checkLengthMismatch[A:Type:Bits,B:Type:Bits]()(implicit ctx: SrcCtx): Unit = {
    val lenA = bits[A].length
    val lenB = bits[B].length
    if (lenA != lenB)
      warn(ctx, u"Bit length mismatch in conversion between ${typ[A]} and ${typ[B]}.")

    if (lenA < lenB) {
      warn(s"Bits ($lenB::$lenA) will be set to zero in result.")
      warn(ctx)
    }
    else if (lenA > lenB) {
      warn(s"Bits ($lenA::$lenB) will be dropped.")
      warn(ctx)
    }
  }
}

