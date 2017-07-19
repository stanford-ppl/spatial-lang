package spatial.targets

import spatial.models._
import spatial.models.xilinx._

object AWS_F1 extends FPGATarget {
  val name = "AWS_F1"
  def burstSize = 512

  override type Area = XilinxArea
  override type Sum = XilinxAreaSummary
  override def areaMetric: AreaMetric[XilinxArea] = XilinxAreaMetric
  override lazy val areaModel: AreaModel[Area,Sum] = new VirtexUSPAreaModel
  override lazy val latencyModel: LatencyModel = new VirtexUSPLatencyModel

  // TODO
  override def capacity: XilinxAreaSummary = XilinxAreaSummary(clbs=100000,regs=200000,dsps=2000,bram=2000,channels=15)
}
