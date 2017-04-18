import scala.language.implicitConversions
import scala.math.Integral
import scala.collection.immutable.NumericRange

object DataImplicits {
  implicit def numberToInt(x: Number): Int = x.toInt
  implicit def intToNumber(x: Int): Number = Number(x)
  implicit def bitToBoolean(x: Bit): Boolean = x.value
  implicit def booleanToBit(x: Boolean): Bit = Bit(x)

  implicit object NumberIsIntegral extends Integral[Number] {
    def quot(x: Number, y: Number): Number = x / y
    def rem(x: Number, y: Number): Number = x % y
    def compare(x: Number, y: Number): Int = if (x < y) - 1 else if (x > y) 1 else 0
    def plus(x : Number, y : Number) : Number = x + y
    def minus(x : Number, y : Number) : Number = x - y
    def times(x : Number, y : Number) : Number = x * y
    def negate(x : Number) : Number = -x
    def fromInt(x : scala.Int) : Number = Number(x)
    def toInt(x : Number) : scala.Int = x.toInt
    def toLong(x : Number) : scala.Long = x.toLong
    def toFloat(x : Number) : scala.Float = x.toFloat
    def toDouble(x : Number) : scala.Double = x.toDouble
  }
}

import DataImplicits._

abstract class Data(valid: Boolean)

abstract class NumberFormat
class FixedPoint(val signed: Boolean, val intBits: Int, val fracBits: Int) extends NumberFormat {
  override def equals(obj: Any) = obj match {
    case that: FixedPoint => this.signed == that.signed && this.intBits == that.intBits && this.fracBits == that.fracBits
    case _ => false
  }
}
object FixedPoint {
  def unapply(x: NumberFormat): Option[(Boolean, Int, Int)] = x match {
    case x: FixedPoint => Some((x.signed, x.intBits, x.fracBits))
    case _ => None
  }
  def apply(signed: Boolean, intBits: Int, fracBits: Int) = new FixedPoint(signed, intBits, fracBits)
}

class FloatPoint(val sigBits: Int, val expBits: Int) extends NumberFormat {
  override def equals(obj: Any) = obj match {
    case that: FloatPoint => this.sigBits == that.sigBits && this.expBits == that.expBits
    case _ => false
  }
}
object FloatPoint {
  def unapply(x: NumberFormat): Option[(Int, Int)] = x match {
    case x: FloatPoint => Some((x.sigBits, x.expBits))
    case _ => None
  }
  def apply(sigBits: Int, expBits: Int) = new FloatPoint(sigBits, expBits)
}

case object IntFormat extends FixedPoint(true,32,0)
case object LongFormat extends FixedPoint(true,64,0)
case object FloatFormat extends FloatPoint(24,8)
case object DoubleFormat extends FloatPoint(53,11)

// TODO: Need to model effects of limited precision
// TODO: Bitwise operations on floating point values?
case class Number(value: BigDecimal, valid: Boolean, fmt: NumberFormat) extends Data(valid) {
  def intValue: Int = fmt match {
    case FixedPoint(s,i,0) => value.toIntExact
    case _ => throw new Exception("Cannot get fixed point representation of a floating point number")
  }

  def fixValue: BigInt = fmt match {
    case FixedPoint(s,i,f) => (value * math.pow(2,f)).toBigInt
    case _ => throw new Exception("Cannot get fixed point representation of a floating point number")
  }

  def bits: Array[Bit] = fmt match {
    case FixedPoint(s,i,f) =>
      Array.tabulate(i+f){i => Bit(this.fixValue.testBit(i), this.valid) }

    case FloatPoint(g,e) => throw new Exception("TODO: Bitwise operations on floating point valus not yet supported")
  }

  def withValid(valid: Boolean) = this.copy(valid = valid)

  def unary_-() = Number(-this.value, valid, fmt)
  def unary_~() = Number(~this.fixValue, valid, fmt)
  def +(that: Number) = Number(this.value + that.value, this.valid && that.valid, fmt)
  def -(that: Number) = Number(this.value - that.value, this.valid && that.valid, fmt)
  def *(that: Number) = Number(this.value * that.value, this.valid && that.valid, fmt)
  def /(that: Number) = Number(this.value / that.value, this.valid && that.valid, fmt)
  def %(that: Number) = Number(this.value % that.value, this.valid && that.valid, fmt)
  def &(that: Number) = Number(this.fixValue & that.fixValue, this.valid && that.valid, fmt)
  def |(that: Number) = Number(this.fixValue | that.fixValue, this.valid && that.valid, fmt)
  def <(that: Number) = Bit(this.value < that.value, this.valid && that.valid)
  def <=(that: Number) = Bit(this.value <= that.value, this.valid && that.valid)
  def >(that: Number) = Bit(this.value > that.value, this.valid && that.valid)
  def >=(that: Number) = Bit(this.value >= that.value, this.valid && that.valid)
  def !==(that: Number) = Bit(this.value != that.value, this.valid && that.valid)
  def ===(that: Number) = Bit(this.value == that.value, this.valid && that.valid)

  def <<(that: Number) = Number(this.fixValue << that.intValue, this.valid && that.valid, fmt)
  def >>(that: Number) = Number(this.fixValue >> that.intValue, this.valid && that.valid, fmt)
  def >>>(that: Number) = {
    // Unsigned right shift isn't supported in BigInt because BigInt technically has infinite precision
    // But we're only using BigInt to model arbitrary precision data here
    val zeros = List.fill(that.intValue)(Bit(false))
    val bits = this.bits.drop(that.intValue) // Drop that number of lsbs
    Number(bits ++ zeros, fmt).withValid(this.valid && that.valid)
  }

  def toDouble: Double = value.toDouble
  def toInt: Int = value.toInt
  def toLong: Long = value.toLong
  def toFloat: Float = value.toFloat

  def until(end: Number) = NumberRange(this, end, Number(1), isInclusive = false)
  def to(end: Number) = NumberRange(this, end, Number(1), isInclusive = true)

  override def toString = if (valid) { value.toString } else { "X" + value.toString + "X" }
}

// Almost more trouble than it's worth...
case class NumberRange(override val start: Number, override val end: Number, override val step: Number, override val isInclusive: Boolean)
  extends NumericRange[Number](start,end,step,isInclusive)(DataImplicits.NumberIsIntegral) {

  override def copy(start: Number, end: Number, step: Number) = NumberRange(start, end, step, isInclusive)

}

object Number {
  def apply(value: Int): Number = Number(BigDecimal(value), true, IntFormat)
  def apply(value: Long): Number = Number(BigDecimal(value), true, LongFormat)
  def apply(value: Float): Number = Number(BigDecimal(value.toDouble), true, FloatFormat)
  def apply(value: Double): Number = Number(BigDecimal(value), true, DoubleFormat)
  def apply(value: String, fmt: NumberFormat): Number = Number(BigDecimal(value), true, fmt)

  def apply(value: BigInt, valid: Boolean, fmt: NumberFormat): Number = fmt match {
    case FixedPoint(s,i,f) => Number(BigDecimal(value) / math.pow(2,f), valid, fmt)
    case FloatPoint(_,_)   => Number(BigDecimal(value), valid, fmt)
  }
  // Array format is big-endian (first (head) is LSB, last bit in Array is MSB)
  def apply(bits: Array[Bit], fmt: NumberFormat): Number = fmt match {
    case FixedPoint(signed,i,f) =>
      val valid = bits.forall(_.valid) // Should this be forall or exists?

      if (signed && bits.last.value) { // Is negative number
        var x = BigInt(-1) // Start with all 1s
        bits.zipWithIndex.foreach { case (bit, i) => if (!bit.value) x = x.flipBit(i) }
        Number(x, valid, fmt)
      }
      else {
        var x = BigInt(0) // Start with all 0s
        bits.zipWithIndex.foreach { case (bit, i) => if (bit.value) x = x.flipBit(i) }
        Number(x, valid, fmt)
      }

    case FloatPoint(_,_) => throw new Exception("TODO: Bitwise operators not yet defined for floating point")
  }

  def random(max: Number, fmt: NumberFormat): Number = fmt match {
    case FixedPoint(s,i,f) =>
      val bits = Array.tabulate(i + f){i => Bit(scala.util.Random.nextBoolean()) }
      val num = Number(bits, fmt)
      num % max

    case FloatPoint(g,e) => throw new Exception("TODO: Random floating point")
  }

  def random(fmt: NumberFormat): Number = fmt match {
    case FixedPoint(s,i,f) =>
      val bits = Array.tabulate(i + f){i => Bit(scala.util.Random.nextBoolean()) }
      Number(bits, fmt)

    case FloatPoint(g,e) => throw new Exception("TODO: Random fixed point")
  }

  def sqrt(x: Number) = Number(Math.sqrt(x.toDouble), x.valid, x.fmt)
  def exp(x: Number) = Number(Math.exp(x.toDouble), x.valid, x.fmt)
  def log(x: Number) = Number(Math.log(x.toDouble), x.valid, x.fmt)
  def abs(x: Number) = Number(x.value.abs, x.valid, x.fmt)
  def min(x: Number, y: Number) = if (x < y) x else y
  def max(x: Number, y: Number) = if (x > y) x else y
}

object X {
  def apply(fmt: NumberFormat) = Number(BigDecimal(-1), false, fmt)
}


case class Bit(value: Boolean, valid: Boolean = true) extends Data(valid) {
  def &&(that: Bit)  = Bit(this.value && that.value, this.valid && that.valid)
  def ||(that: Bit)  = Bit(this.value || that.value, this.valid && that.valid)
  def ^(that: Bit)   = Bit(this.value ^ that.value, this.valid && that.valid)
  def !==(that: Bit) = Bit(this.value != that.value, this.valid && that.valid)
  def ===(that: Bit) = Bit(this.value == that.value, this.valid && that.valid)

  override def toString = if (valid) { value.toString } else "X"
}

object FALSE extends Bit(false, true) { override def toString = "false" }
object TRUE  extends Bit(true, true) { override def toString = "true" }


