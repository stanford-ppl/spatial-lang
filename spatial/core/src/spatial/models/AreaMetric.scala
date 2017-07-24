package spatial.models

trait AreaMetric[A] {
  def zero: A
  def plus(a: A, b: A): A
  def times(a: A, x: Int, isInner: Boolean): A
  def lessThan(x: A, y: A): Boolean

  def sramArea(n: Int): A
  def regArea(n: Int, bits: Int): A
  def muxArea(n: Int, bits: Int): A
}

abstract class AreaSummary[T<:AreaSummary[T]] {
  def headings: List[String]
  def toList:   List[Double]
  def toFile:   List[Double]
  def fromList(items: List[Double]): T

  def <=(that: AreaSummary[T]): Boolean = this.toList.zip(that.toList).forall{case (a,b) => a <= b}
  def /(that: AreaSummary[T]): T = fromList(this.toList.zip(that.toList).map{case (a,b) => a / b })
}

trait AreaMetricOps {
  def noArea[A:AreaMetric]: A = implicitly[AreaMetric[A]].zero

  implicit class AreaMetricInfixOps[A:AreaMetric](a: A) {
    def +(b: A): A = implicitly[AreaMetric[A]].plus(a, b)
    def replicate(x: Int, isInner: Boolean): A = implicitly[AreaMetric[A]].times(a, x, isInner)
  }
}
