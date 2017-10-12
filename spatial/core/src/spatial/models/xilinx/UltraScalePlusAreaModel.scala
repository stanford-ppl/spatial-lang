package spatial.models.xilinx

import argon.core._
import forge._
import spatial.models._

class UltraScalePlusAreaModel extends XilinxAreaModel {
  import spatial.targets.XilinxDevice._
  override val FILE_NAME: String = "UltraScalePlus.csv"

  // TODO: When do we use URAMs? Ever?
  private def bramWordDepth(width: Int): Int = {
    if      (width == 1) 16384
    else if (width == 2) 8192
    else if (width <= 4) 4096
    else if (width <= 9) 2048
    else if (width <= 18) 1024 // Assume uses RAM18 TDP
    else 512                   // Assume uses RAM36 TDP
  }

  @stateful override def SRAMArea(width: Int, depth: Int): Area = {
    val cols = Math.ceil(width / 18.0)
    val nRAM36Cols = Math.floor(cols / 2).toInt
    val nRAM18Cols = if (cols % 2 != 0) 1 else 0
    val wordDepth  = bramWordDepth(width)
    val nRows      = Math.ceil(depth.toDouble/wordDepth).toInt
    val totalRAM18 = nRAM18Cols * nRows
    val totalRAM36 = nRAM36Cols * nRows

    dbg(s"# of RAM18 Cols:  $nRAM18Cols")
    dbg(s"# of RAM36 Cols:  $nRAM36Cols")
    dbg(s"# of rows:        $nRows")
    dbg(s"Elements / Mem:   $wordDepth")
    dbg(s"Memories / Bank:  $totalRAM18, $totalRAM36")
    AreaMap(RAM18->totalRAM18, RAM36->totalRAM36)
  }
}
