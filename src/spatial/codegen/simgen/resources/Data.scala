
abstract class Data(valid: Boolean)

abstract class NumberFormat
case class FixedPoint(signed: Boolean, intBits: Int, fmtBits: Int) extends NumberFormat
case class FloatPoint(sigBits: Int, expBits: Int) extends NumberFormat

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

  override def toString = if (valid) { value.toString } else "X"
}

object Number {
  def apply(value: BigInt, valid: Boolean, fmt: NumberFormat) = fmt match {
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
  def apply(fmt: NumberFormat) = Number(BigDecimal(0), false, fmt)
}


case class Bit(value: Boolean, valid: Boolean) extends Data(valid) {
  def &&(that: Bit)  = Bit(this.value && that.value, this.valid && that.valid)
  def ||(that: Bit)  = Bit(this.value || that.value, this.valid && that.valid)
  def ^(that: Bit)   = Bit(this.value ^ that.value, this.valid && that.valid)
  def !==(that: Bit) = Bit(this.value != that.value, this.valid && that.valid)
  def ===(that: Bit) = Bit(this.value == that.value, this.valid && that.valid)

  override def toString = if (valid) { value.toString } else "X"
}

object FALSE extends Bit(false, true)
object TRUE  extends Bit(true, true)