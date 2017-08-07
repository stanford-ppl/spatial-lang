package spatial.targets

import spatial.models._
import spatial.models.xilinx._

object Zynq extends FPGATarget {
  val name = "Zynq"
  def burstSize = 512

  override type Area = XilinxArea
  override type Sum = XilinxAreaSummary
  override def areaMetric: AreaMetric[XilinxArea] = XilinxAreaMetric
  def areaModel: AreaModel[Area,Sum] = new VirtexUSPAreaModel
  def latencyModel: LatencyModel = new VirtexUSPLatencyModel

  // TODO
  override def capacity: XilinxAreaSummary = XilinxAreaSummary(clbs=100000,regs=200000,dsps=2000,bram=2000,channels=15)
}
