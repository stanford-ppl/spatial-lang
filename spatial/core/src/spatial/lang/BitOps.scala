package spatial.lang

import spatial._
import forge._

trait BitsOpsApi extends BitOpsExp { this: SpatialApi => }

trait BitOpsExp { this: SpatialExp =>

  type BitVector = VectorN[Bool]

  implicit class VarDataConversionOps[A:Meta:Bits](x: Var[A]) { // TODO: Proper way to define conversion ops on vars?
    @api def reverse: A = {
      readVar(x).reverse
    }

  }

  implicit class DataConversionOps[A:Meta:Bits](x: A) {
    @api def apply(i: Int): Bool = dataAsBitVector(x).apply(i)
    @api def apply(range: Range): BitVector = dataAsBitVector(x).apply(range)

    @api def as[B:Meta:Bits]: B = {
      checkLengthMismatch[A,B]()
      val len = bits[B].length
      implicit val vT = vectorNType[Bool](len)
      val vector = dataAsBitVector(x)
      bitVectorAsData[B](vector, enWarn = true)
    }

    @api def reverse: A = {
      val len = bits[A].length
      implicit val vT = vectorNType[Bool](len)
      val vector = dataAsBitVector(x)
      val reversed_elements = (0 until len).map{i =>
        vector_apply(vector.s, i)
      }
      val vector_reversed = wrap(vector_new[Bool,VectorN](reversed_elements))
      bitVectorAsData[A](vector_reversed, enWarn = true)
    }


    // takeN(offset) - creates a VectorN slice starting at given little-endian offset

    @generate
    @api def takeJJ$JJ$1to128(offset: Int): VectorJJ[Bool] = dataAsBitVector(x).takeJJ(offset)

    @generate
    @api def takeJJMSB$JJ$1to128: VectorJJ[Bool] = {
      val offset = bits[A].length - JJ
      dataAsBitVector(x).takeJJ(offset)
    }


    // asNb - converts this to VectorN bits (includes bit length mismatch checks)

    @generate
    @api def asJJb$JJ$1to128: VectorJJ[Bool] = this.as[VectorJJ[Bool]]
  }

  implicit class BitVectorOps(vector: BitVector) {

    @api def as[B:Meta:Bits] = {
      implicit val vT = vectorNType[Bool](vector.width)
      bitVectorAsData[B](vector, enWarn = true)
    }

    @generate
    @api def asJJb$JJ$1to128: VectorJJ[Bool] = this.as[VectorJJ[Bool]]
  }

  @internal def dataAsBitVector[A:Meta:Bits](x: A)(implicit ctx: SrcCtx): BitVector = typ[A] match {
    case tp: StructType[_] =>
      val fieldTypes = tp.fields.map{case (name,mT) => mT }
      val fields = tp.ev(x).fields.map(_._2)
      val fieldBits = fields.zip(fieldTypes).map{case (field,mT@Bits(bT)) =>
        dataAsBitVector(field)(mmeta(mT),mbits(bT),ctx).s
      }
      val width = fieldTypes.map{case Bits(bT) => bT.length }.sum
      implicit val vT = vectorNType[Bool](width)
      wrap(vector_concat[Bool,VectorN](fieldBits)) // big endian (List)

    case tp: VectorType[_] =>
      val width = bits[A].length
      implicit val vT = vectorNType[Bool](width)

      // HACK: Specialize for converting from other types of vectors of bits
      if (tp.child == BoolType) {
        val sym = x.s.asInstanceOf[Exp[VectorN[Bool]]]
        vT.wrapped(sym)
      }
      else {
        val vector = x.asInstanceOf[Vector[_]]
        val mT = tp.child
        val Bits(bT) = mT
        val elems = List.tabulate(tp.width) { i => dataAsBitVector(vector.apply(i))(mtyp(mT), mbits(bT), ctx).s }
        wrap(vector_concat[Bool, VectorN](elems))   // big endian (List)
      }

    case _ =>
      val len = bits[A].length
      implicit val vT = vectorNType[Bool](len)
      wrap(data_as_bits(x.s))
  }

  @internal def bitVectorSliceOrElseZero[A:Meta:Bits](x: BitVector, offset: Int)(implicit ctx: SrcCtx): Exp[A] = {
    val mT = meta[A]
    val bT = bits[A]
    val vecSize = x.width
    val length = bT.length
    val fvT = vectorNType[Bool](length)

    if (offset < vecSize && length+offset-1 < vecSize) {
      val fieldBits = fvT.wrapped(vector_slice(x.s, length + offset - 1, offset)(typ[Bool], bits[Bool], ctx, fvT))
      val field = bitVectorAsData(fieldBits, enWarn = false)(mT, bT, fvT, ctx)
      mT.unwrapped(field)
    }
    else if (offset >= vecSize) {
      mT.unwrapped(bT.zero)
    }
    else {
      val max = vecSize - 1
      val remain = length + offset - vecSize    // e.g. asked for 34::5 (30 bits) of 32b number, should have 3 bits left
      val fvT2 = vectorNType[Bool](remain)
      val fvB2 = vectorNBits[Bool](remain,fvT2)

      val trueBits = vector_slice(x.s, max, offset)(typ[Bool], bits[Bool], ctx, fvT)
      val zeroBits = fvB2.zero.s
      // Note that this is big-endian concatenation (everything internally is big-endian)
      val fieldBits = fvT.wrapped(vector_concat[Bool,VectorN](Seq(trueBits,zeroBits))(typ[Bool],bits[Bool],ctx,fvT))

      val field = bitVectorAsData(fieldBits, enWarn = false)(mT, bT, fvT, ctx)
      mT.unwrapped(field)
    }
  }

  @internal def bitVectorAsData[B:Meta:Bits](x: BitVector, enWarn: Boolean)(implicit vT: Meta[BitVector], ctx: SrcCtx): B = {
    val Bits(bT) = vT
    if (enWarn) checkLengthMismatch()(vT,bT,meta[B],bits[B],ctx)
    val vecSize = x.width

    typ[B] match {
      case tp: StructType[_] =>
        val fieldNames = tp.fields.map{case (name,_) => name }
        val fieldTypes = tp.fields.map{case (_, mT) => mT }
        val sizes = tp.fields.map{case (_, mT@Bits(bT)) => bT.length }
        val offsets = List.tabulate(sizes.length){i => sizes.drop(i+1).sum }

        val fields = (fieldTypes,offsets).zipped.map{case (mT@Bits(bT),offset) =>
          bitVectorSliceOrElseZero(x,offset)(mtyp(mT),mbits(bT),ctx)
        }
        val namedFields = fieldNames.zip(fields)

        tp.wrapped(struct_new(namedFields)(tp,ctx).asInstanceOf[Exp[B]])

      case tp: VectorType[_] =>
        // HACK: Specialize for converting to other vectors of bits
        // Assumes that Vector* has the same underlying representation as VectorN
        if (tp.child == BoolType) {
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
            bitVectorSliceOrElseZero(x, offset)(mtyp(mT), mbits(bT), ctx)
          }
          tp.wrapped(vector_new(elems)(mtyp(mT), mbits(bT), ctx, mtyp(tp)).asInstanceOf[Exp[B]])
        }

      case _ => wrap(bits_as_data[B](x.s))
    }
  }


  @internal def checkLengthMismatch[A:Meta:Bits,B:Meta:Bits]()(implicit ctx: SrcCtx): Unit = {
    val lenA = bits[A].length
    val lenB = bits[B].length
    if (lenA != lenB)
      warn(ctx, u"Bit length mismatch in conversion between ${typ[A]} and ${typ[B]}.")

    if (lenA < lenB) {
      warn(s"Bits (${lenB}::${lenA}) will be set to zero in result.")
      warn(ctx)
    }
    else if (lenA > lenB) {
      warn(s"Bits (${lenA}::${lenB}) will be dropped.")
      warn(ctx)
    }
  }


  /** IR Nodes **/
  // Type matters for CSE here!
  case class BitsAsData[T:Type:Bits](a: Exp[BitVector], mT: Type[T]) extends Op[T] {
    def mirror(f:Tx) = bits_as_data[T](f(a))
  }

  case class DataAsBits[T:Type:Bits](a: Exp[T])(implicit mV: Type[BitVector]) extends Op[BitVector] {
    def mirror(f:Tx) = data_as_bits[T](f(a))
    val mT = typ[T]
  }


  /** Constructors **/
  @internal def data_as_bits[A:Type:Bits](a: Exp[A])(implicit mV: Type[BitVector]): Exp[BitVector] = stage(DataAsBits[A](a))(ctx)
  @internal def bits_as_data[A:Type:Bits](a: Exp[BitVector]): Exp[A] = stage(BitsAsData[A](a,typ[A]))(ctx)

}
