package spatial.targets

import spatial.models._
import spatial.models.xilinx._

object AWS_F1 extends XilinxDevice {
  val name = "AWS_F1"
  def burstSize = 512

  protected def makeAreaModel: AreaModel = new UltraScalePlusAreaModel
  protected def makeLatencyModel: LatencyModel = new UltraScalePlusLatencyModel

  // TODO
  override def capacity: Area = AreaMap(

  )
}
