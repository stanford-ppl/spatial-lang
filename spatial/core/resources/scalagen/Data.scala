import scala.language.implicitConversions
import scala.math.Integral
import scala.collection.immutable.NumericRange
import java.lang.ArithmeticException

object DataImplicits {
  implicit def numberToInt(x: Number): Int = x.toInt
  implicit def intToNumber(x: Int): Number = Number(x)
  implicit def bitToBoolean(x: Bit): Boolean = x.value
  implicit def booleanToBit(x: Boolean): Bit = Bit(x)

  implicit object NumberIsIntegral extends Integral[Number] {
    def quot(x: Number, y: Number): Number = x / y
    def rem(x: Number, y: Number): Number = x % y
    def compare(x: Number, y: Number): Int = if (x < y) -1 else if (x > y) 1 else 0
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
  override def hashCode() = (signed,intBits,fracBits).hashCode()
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
  override def hashCode() = (sigBits, expBits).hashCode()
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
class Number(val value: BigDecimal, val valid: Boolean, val fmt: NumberFormat) extends Data(valid) {
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

  def withValid(valid: Boolean) = Number(value, valid, fmt)

  def valueOrX(value: => Number): Number = try { value } catch {case e: Throwable => X(fmt) }

  def unary_-() = Number(-this.value, valid, fmt)
  def unary_~() = Number(~this.fixValue, valid, fmt)
  def floor() = {
    val adjustment = if (this < 0) -1 else 0
    Number(this.value - (this.value % 1) + adjustment, this.valid, fmt)
  }
  def ceil() = {
    val adjustment = if (this.fixValue == 0) 0 else {if (this < 0) 0 else 1}
    Number(this.value - (this.value % 1) + adjustment , this.valid, fmt)
  }
  def +(that: Number) = Number(this.value + that.value, this.valid && that.valid, fmt)
  def -(that: Number) = Number(this.value - that.value, this.valid && that.valid, fmt)
  def *(that: Number) = Number(this.value * that.value, this.valid && that.valid, fmt)
  def /(that: Number) = valueOrX{ Number(this.value / that.value, this.valid && that.valid, fmt) }
  def %(that: Number) = valueOrX{ Number(((this.value % that.value) + that.value) % that.value, this.valid && that.valid, fmt) }
  def &(that: Number) = Number(this.fixValue & that.fixValue, this.valid && that.valid, fmt)
  def ^(that: Number) = Number(this.fixValue ^ that.fixValue, this.valid && that.valid, fmt)
  def |(that: Number) = Number(this.fixValue | that.fixValue, this.valid && that.valid, fmt)
  def <(that: Number) = Bit(this.value < that.value, this.valid && that.valid)
  def <=(that: Number) = Bit(this.value <= that.value, this.valid && that.valid)
  def >(that: Number) = Bit(this.value > that.value, this.valid && that.valid)
  def >=(that: Number) = Bit(this.value >= that.value, this.valid && that.valid)
  def !==(that: Number) = Bit(this.value != that.value, this.valid && that.valid)
  def ===(that: Number) = Bit(this.value == that.value, this.valid && that.valid)

  def <+>(that: Number) = Number.saturating(this.value + that.value, this.valid && that.valid, fmt)
  def <->(that: Number) = Number.saturating(this.value - that.value, this.valid && that.valid, fmt)
  def <*>(that: Number) = Number.saturating(this.value * that.value, this.valid && that.valid, fmt)
  def </>(that: Number) = Number.saturating(this.value / that.value, this.valid && that.valid, fmt)
  def *&(that: Number)  = Number.unbiased(this.value * that.value, this.valid && that.valid, fmt)
  def /&(that: Number)  = valueOrX { Number.unbiased(this.value / that.value, this.valid && that.valid, fmt) }
  def <*&>(that: Number) = Number.unbiasedSat(this.value * that.value, this.valid && that.valid, fmt)
  def </&>(that: Number) = valueOrX { Number.unbiasedSat(this.value / that.value, this.valid && that.valid, fmt) }

  def <<(that: Number) = Number(this.fixValue << that.intValue, this.valid && that.valid, fmt)
  def >>(that: Number) = Number(this.fixValue >> that.intValue, this.valid && that.valid, fmt)
  def >>>(that: Number) = {
    // Unsigned right shift isn't supported in BigInt because BigInt technically has infinite precision
    // But we're only using BigInt to model arbitrary precision data here
    val zeros = List.fill(that.intValue)(Bit(false))
    val bits = this.bits.drop(that.intValue) // Drop that number of lsbs
    Number(bits ++ zeros, fmt).withValid(this.valid && that.valid)
  }

  /*override def canEqual(that: Any): Boolean = that match {
    case _:Int | _:Long | _:Float | _:Double | _:Number => true
    case _ => false
  }*/

  override def equals(that: Any): Boolean = that match {
    case that: Int => this === Number(that)
    case that: Long => this === Number(that)
    case that: Float => this === Number(that)
    case that: Double => this === Number(that)
    case that: Number => this === Number(that.value, that.valid, this.fmt)
    case _ => false
  }
  override def hashCode() = (value,fmt).hashCode()

  def toDouble: Double = value.toDouble
  def toInt: Int = value.toInt
  def toLong: Long = value.toLong
  def toFloat: Float = value.toFloat

  def until(end: Number) = NumberRange(this, end, Number(1), isInclusive = false)
  def to(end: Number) = NumberRange(this, end, Number(1), isInclusive = true)

  override def toString = if (valid) { value.bigDecimal.toPlainString } else { "X" + value.bigDecimal.toPlainString + "X" }
}

// Almost more trouble than it's worth...
case class NumberRange(override val start: Number, override val end: Number, override val step: Number, override val isInclusive: Boolean)
  extends NumericRange[Number](start,end,step,isInclusive)(DataImplicits.NumberIsIntegral) {

  override def copy(start: Number, end: Number, step: Number) = NumberRange(start, end, step, isInclusive)

}

object Number {
  private def clamp(bits: BigInt, valid: Boolean, fmt: NumberFormat) = {
    val FixedPoint(s,i,f) = fmt
    if (bits < 0) {
      var x = BigInt(-1)
      Range(0, i+f).foreach { i => if (!bits.testBit(i)) x = x.flipBit(i) }
      new Number(BigDecimal(x) / BigDecimal(BigInt(1) << f), valid, fmt)
    }
    else {
      val mask = if (s) (BigInt(1) << (i-1)) - 1 else (BigInt(1) << i) - 1
      var x = BigInt(0)
      Range(0, i+f).foreach { i => if (bits.testBit(i)) x = x.flipBit(i) }
      new Number(BigDecimal(x) / BigDecimal(BigInt(1) << f), valid, fmt)
    }
  }

  def unbiased(value: BigDecimal, valid: Boolean, fmt: NumberFormat): Number = {
    val FixedPoint(s,i,f) = fmt
    val intValue = value * BigDecimal(BigInt(1) << f)
    val rand = scala.util.Random.nextFloat() // uniform between 0 and 1
    val bits = (intValue + rand).toBigInt
    Number.clamp(bits, valid, fmt)
  }
  def saturating(value: BigDecimal, valid: Boolean, fmt: NumberFormat): Number = {
    val FixedPoint(s, i, f) = fmt
    val MAX_INTEGRAL_VALUE = BigDecimal( if (s) (BigInt(1) << (i+f-1)) - 1 else (BigInt(1) << (i+f)) - 1 )/BigDecimal(BigInt(1) << f)
    val MIN_INTEGRAL_VALUE = BigDecimal( if (s) -(BigInt(1) << (i-1)) else BigInt(0) )
    if (value < MIN_INTEGRAL_VALUE) {
      new Number(MIN_INTEGRAL_VALUE, valid, fmt)
    }
    else if (value > MAX_INTEGRAL_VALUE) {
      new Number(MAX_INTEGRAL_VALUE, valid, fmt)
    }
    else Number.apply(value, valid, fmt)
  }
  def unbiasedSat(value: BigDecimal, valid: Boolean, fmt: NumberFormat): Number = {
    val FixedPoint(s,i,f) = fmt
    val intValue = value * BigDecimal(BigInt(1) << f)
    val rand = scala.util.Random.nextFloat() // uniform between 0 and 1
    val bits = intValue + rand
    val MAX_INTEGRAL_VALUE = BigDecimal( if (s) (BigInt(1) << (i+f-1)) - 1 else (BigInt(1) << (i+f)) - 1 )/BigDecimal(BigInt(1) << f)
    val MIN_INTEGRAL_VALUE = BigDecimal(if (s) -(BigInt(1) << (i-1)) else BigInt(0))
    if (bits < MIN_INTEGRAL_VALUE) {
      new Number(MIN_INTEGRAL_VALUE, valid, fmt)
    }
    else if (bits > MAX_INTEGRAL_VALUE) {
      new Number(MAX_INTEGRAL_VALUE, valid, fmt)
    }
    else new Number(bits / BigDecimal(BigInt(1) << f), valid, fmt)
  }
  def apply(value: BigDecimal, valid: Boolean, fmt: NumberFormat): Number = fmt match {
    case FixedPoint(s,i,f) =>
      val bits = value * BigDecimal(BigInt(1) << f)
      Number.clamp(bits.toBigInt, valid, fmt)
    case FloatPoint(_,_) =>
      new Number(value, valid, fmt)
  }

  def apply(value: Int): Number = Number(BigDecimal(value), true, IntFormat)
  def apply(value: Long): Number = Number(BigDecimal(value), true, LongFormat)
  def apply(value: Float): Number = Number(BigDecimal(value.toDouble), true, FloatFormat)
  def apply(value: Double): Number = Number(BigDecimal(value), true, DoubleFormat)
  def apply(value: String, fmt: NumberFormat): Number = Number(BigDecimal(value), true, fmt)

  def apply(value: BigInt, valid: Boolean, fmt: NumberFormat): Number = fmt match {
    case FixedPoint(s,i,f) => Number.clamp(value, valid, fmt)
    case FloatPoint(_,_)   => Number.apply(BigDecimal(value), valid, fmt)
  }
  // Array format is big-endian (first (head) is LSB, last bit in Array is MSB)
  def apply(bits: Array[Bit], fmt: NumberFormat): Number = fmt match {
    case FixedPoint(signed,i,f) =>
      val valid = bits.forall(_.valid) // Should this be forall or exists?

      if (signed && bits.last.value) { // Is negative number
        var x = BigInt(-1) // Start with all 1s
        bits.zipWithIndex.foreach { case (bit, i) => if (!bit.value) x = x.flipBit(i) }
        new Number(BigDecimal(x), valid, fmt)
      }
      else {
        var x = BigInt(0) // Start with all 0s
        bits.zipWithIndex.foreach { case (bit, i) => if (bit.value) x = x.flipBit(i) }
        new Number(BigDecimal(x), valid, fmt)
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

    case FloatPoint(g,e) => throw new Exception("TODO: Random floating point")
  }

  // TODO: Fix these!
  def sqrt(x: Number) = Number(Math.sqrt(x.toDouble), x.valid, x.fmt)
  def exp(x: Number) = Number(Math.exp(x.toDouble), x.valid, x.fmt)
  def log(x: Number) = Number(Math.log(x.toDouble), x.valid, x.fmt)
  def abs(x: Number) = Number(x.value.abs, x.valid, x.fmt)
  def min(x: Number, y: Number) = if (x < y) x else y
  def max(x: Number, y: Number) = if (x > y) x else y
  def pow(x: Number, exp: Number) = Number(Math.pow(x.toDouble, exp.toDouble), x.valid, x.fmt)

  def sin(x: Number) = Number(Math.sin(x.toDouble), x.valid, x.fmt)
  def cos(x: Number) = Number(Math.cos(x.toDouble), x.valid, x.fmt)
  def tan(x: Number) = Number(Math.tan(x.toDouble), x.valid, x.fmt)
  def sinh(x: Number) = Number(Math.sinh(x.toDouble), x.valid, x.fmt)
  def cosh(x: Number) = Number(Math.cosh(x.toDouble), x.valid, x.fmt)
  def tanh(x: Number) = Number(Math.tanh(x.toDouble), x.valid, x.fmt)
  def asin(x: Number) = Number(Math.asin(x.toDouble), x.valid, x.fmt)
  def acos(x: Number) = Number(Math.acos(x.toDouble), x.valid, x.fmt)
  def atan(x: Number) = Number(Math.atan(x.toDouble), x.valid, x.fmt)
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


