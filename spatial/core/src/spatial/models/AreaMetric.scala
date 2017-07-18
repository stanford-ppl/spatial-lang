package spatial.models

trait AreaMetric[A] {
  def zero: A
  def plus(a: A, b: A): A
  def times(a: A, x: Int, isInner: Boolean): A
  def lessThan(x: A, y: A): Boolean
}

abstract class AreaSummary {
  def headings: List[String]
  def toArray:  List[Double]
}

trait AreaMetricOps {
  def noArea[A:AreaMetric]: A = implicitly[AreaMetric[A]].zero

  implicit class AreaMetricInfixOps[A:AreaMetric](a: A) {
    def +(b: A): A = implicitly[AreaMetric[A]].plus(a, b)
    def replicate(x: Int, isInner: Boolean): A = implicitly[AreaMetric[A]].times(a, x, isInner)
  }
}
