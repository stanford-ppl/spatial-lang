package spatial.targets

import spatial.models._
import spatial.models.xilinx._

object Zynq extends XilinxDevice {
  import XilinxDevice._
  val name = "Zynq"
  def burstSize = 512

  protected def makeAreaModel: AreaModel = new ZynqAreaModel
  protected def makeLatencyModel: LatencyModel = new ZynqLatencyModel

  def capacity: Area = AreaMap(
    SLICEL -> 54650,  // Can use any LUT
    SLICEM -> 17600,  // Can only use specialized LUTs
    Slices -> 54650,  // SLICEL + SLICEM
    Regs   -> 437200,
    BRAM   -> 545,    // 1 RAM36 or 2 RAM18s
    DSPs   -> 900
  )
}
