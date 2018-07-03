package spatial.targets

import spatial.models._
import spatial.models.xilinx._

object AWS_F1 extends XilinxDevice {
  val name = "AWS_F1"
  def burstSize = 512

  def newAreaModel(): AreaModel = new UltraScalePlusAreaModel
  def newLatencyModel(): LatencyModel = new UltraScalePlusLatencyModel

  // TODO
  override def capacity: Area = AreaMap(

  )
}
