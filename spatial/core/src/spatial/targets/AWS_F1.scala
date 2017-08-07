package spatial.targets

import spatial.models._
import spatial.models.xilinx._

object AWS_F1 extends XilinxDevice {
  val name = "AWS_F1"
  def burstSize = 512

  def areaModel: AreaModel = new UltraScalePlusAreaModel
  def latencyModel: LatencyModel = new UltraScalePlusLatencyModel

  // TODO
  override def capacity: Area = AreaMap() //: XilinxAreaSummary = XilinxAreaSummary(clbs=100000,regs=200000,dsps=2000,bram=2000,channels=15)
}
