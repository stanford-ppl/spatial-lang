package spatial.targets

import spatial.models._
import spatial.models.altera._

object Plasticine extends AlteraDevice { // TODO: Fix
  import AlteraDevice._
  def name = "Default"
  val burstSize = 512 // in bits

  //TODO: No model for plasticine yet
  protected def makeAreaModel: AreaModel = new StratixVAreaModel
  protected def makeLatencyModel: LatencyModel = new StratixVLatencyModel
  def capacity: Area = AreaMap(ALMs->262400, Regs->524800, DSPs->1963, BRAM->2567, Channels->13)
}

