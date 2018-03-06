package spatial.targets

import spatial.models.altera._
import spatial.models._

object Arria10 extends AlteraDevice {
  import AlteraDevice._
  val name = "Arria10"
  def burstSize = 512

  case object VideoCamera extends Bus {def length = 32}
  case object DP extends Bus {def length = 32}


  // FIXME: No models for Arria10 yet
  protected def makeAreaModel: AreaModel       = new StratixVAreaModel
  protected def makeLatencyModel: LatencyModel = new StratixVLatencyModel
  def capacity: Area = AreaMap(ALMs->262400, Regs->524800, DSPs->1963, BRAM->2567, Channels->13)
}
