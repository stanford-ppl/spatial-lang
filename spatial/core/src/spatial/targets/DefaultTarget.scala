package spatial.targets

import spatial.models._
import spatial.models.altera._

// This is actually the old StratixV from MaxJ..
object DefaultTarget extends AlteraDevice {
  import AlteraDevice._
  def name = "Default"
  val burstSize = 512 // in bits. TODO: This should actually be selectable

  protected def makeAreaModel: AreaModel = new StratixVAreaModel
  protected def makeLatencyModel: LatencyModel = new StratixVLatencyModel
  def capacity: Area = AreaMap(ALMs->262400, Regs->524800, DSPs->1963, BRAM->2567, Channels->13)
}

