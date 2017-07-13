package spatial.models

trait AreaMetric[A] {
  def zero: A
}

trait AreaMetricOps {
  def noArea[A:AreaMetric]: A = implicitly[AreaMetric[A]].zero
}
