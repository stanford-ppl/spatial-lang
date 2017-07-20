package spatial.models.xilinx

import forge._
import spatial.models.AreaSummary

class VirtexUSPAreaModel extends XilinxAreaModel {
  override val MAX_PORT_WIDTH: Int = 40

  // TODO: This needs to be updated for Xilinx BRAMs
  override def bramWordDepth(width: Int): Int = {
    if      (width == 1)  16384
    else if (width == 2)  8192
    else if (width <= 5)  4096
    else if (width <= 10) 2048
    else if (width <= 20) 1024
    else                  512
  }

  override def init(): Unit = {

  }

  // TODO
  @stateful override def summarize(area: XilinxArea): XilinxAreaSummary = {
    XilinxAreaSummary(0, 0, 0, 0, 0)
  }

}
