package spatial.targets

import spatial.models.altera._
import spatial.models._

object Ethernet extends AlteraDevice {
  import AlteraDevice._
  val name = "Ethernet"
  
  // FIXME: Not sure what size this should be
  def burstSize = 96 


  case object EthInput extends Bus { def length = 32 }
  case object EthOutput extends Bus { def length = 32 }

  
  // FIXME: No models for Ethernet yet. This code is taken straight from the DE1 targets...
  def areaModel: AreaModel       = new StratixVAreaModel
  def latencyModel: LatencyModel = new StratixVLatencyModel
  def capacity: Area = AreaMap(ALMs->262400, Regs->524800, DSPs->1963, BRAM->2567, Channels->13)
}
