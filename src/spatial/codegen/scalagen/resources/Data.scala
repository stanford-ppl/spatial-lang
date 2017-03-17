import scala.language.implicitConversions

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
case class Number(value: BigDecimal, valid: Boolean, fmt: NumberFormat) extends Data(valid) {
  def fixValue: BigInt = fmt match {
    case FixedPoint(s,i,f) => (value * math.pow(2,f)).toBigInt
    case _ => throw new Exception("Cannot get fixed point representation of a floating point number")
  }

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

  def toDouble: Double = value.toDouble
  def toInt: Int = value.toInt

  def until(end: Number) = NumberRange(this, end, Number(1))

  override def toString = if (valid) { value.toString } else { "X" + value.toString + "X" }
}

object Number {
  import DataImplicits._

  def apply(value: Int): Number = Number(BigDecimal(value), true, IntFormat)
  def apply(value: Long): Number = Number(BigDecimal(value), true, LongFormat)
  def apply(value: Float): Number = Number(BigDecimal(value.toDouble), true, FloatFormat)
  def apply(value: Double): Number = Number(BigDecimal(value), true, DoubleFormat)

  def apply(value: BigInt, valid: Boolean, fmt: NumberFormat): Number = fmt match {
    case FixedPoint(s,i,f) => Number(BigDecimal(value) / math.pow(2,f), valid, fmt)
    case FloatPoint(_,_)   => Number(BigDecimal(value), valid, fmt)
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


object DataImplicits {
  implicit def numberToInt(x: Number): Int = x.toInt
  implicit def intToNumber(x: Int): Number = Number(x)
  implicit def bitToBoolean(x: Bit): Boolean = x.value
  implicit def booleanToBit(x: Boolean): Bit = Bit(x)
}


case class NumberRange(start: Number, end: Number, step: Number) {
  import DataImplicits._

  def foreach(func: Number => Unit): Unit = {
    var i = start
    while (i < end) {
      func(i)
      i += step
    }
  }
  def by(step: Number) = NumberRange(start, end, step)
}