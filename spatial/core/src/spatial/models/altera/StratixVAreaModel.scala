package spatial.models
package altera

import argon.core._
import forge._

class StratixVAreaModel extends AlteraAreaModel {
  val FILE_NAME = "StratixV.csv"
  private val MAX_PORT_WIDTH: Int = 40
  private val areaFile = "StratixV-Routing.csv"
  private val keys = Seq("LUT3", "LUT4", "LUT5", "LUT6", "LUT7", "MEM16", "MEM32", "MEM64",	"Regs", "DSPs",	"BRAM")


  private def bramWordDepth(width: Int): Int = {
    if      (width == 1) 16384
    else if (width == 2) 8192
    else if (width <= 5) 4096
    else if (width <= 10) 2048
    else if (width <= 20) 1024
    else 512
  }

  @stateful override def SRAMArea(width: Int, depth: Int): Area = {
    val nCols     = if (width > MAX_PORT_WIDTH) Math.ceil(width / MAX_PORT_WIDTH ).toInt else 1
    val wordDepth = bramWordDepth(width)
    val nRows     = Math.ceil(depth.toDouble/wordDepth).toInt
    val total = nCols * nRows

    dbg(s"# of columns:     $nCols")
    dbg(s"# of rows:        $nRows")
    dbg(s"Elements / Mem:   $wordDepth")
    dbg(s"Memories / Bank:  $total")
    AreaMap("BRAM"->total)
  }

  import AreaNeuralModel._
  class LUTRoutingModel extends AreaNeuralModel(name="RoutingLUTs", filename=areaFile, OUT=RLUTS, LAYER2=6)
  class RegFanoutModel extends AreaNeuralModel(name="FanoutRegisters", filename=areaFile, OUT=FREGS, LAYER2=6)
  class UnavailALMsModel extends AreaNeuralModel(name="UnavailableALMs", filename=areaFile, OUT=UNVAIL, LAYER2=6)

  lazy val lutModel = new LUTRoutingModel
  lazy val regModel = new RegFanoutModel
  lazy val almModel = new UnavailALMsModel

  def calculateRoutingLUTs(design: Area): Double = lutModel.evaluate(design.seq(keys:_*))
  def calculateRoutingRegs(design: Area): Double = regModel.evaluate(design.seq(keys:_*))
  def calculateUnavailALMs(design: Area): Double = almModel.evaluate(design.seq(keys:_*))

  @stateful override def init(): Unit = {
    super.init()
    lutModel.init()
    regModel.init()
    almModel.init()
  }

}


