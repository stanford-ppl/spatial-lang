package spatial.models

trait AreaMetric[A] {
  def zero: A
  def lessThan(x: A, y: A): Boolean
}

trait AreaMetricOps {
  def noArea[A:AreaMetric]: A = implicitly[AreaMetric[A]].zero
}
