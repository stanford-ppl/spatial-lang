package spatial.targets

import spatial.models.altera._
import spatial.models._

object DE1 extends AlteraDevice {
  import AlteraDevice._
  val name = "DE1"
  def burstSize = 96

  case object DE1VideoCamera extends Bus {def length = 16}
  case object VGA extends Bus {def length = 16}
  case object SliderSwitch extends Bus {def length = 10}
  case object LEDR extends Bus {
    def length = 10
    val LEDR0 = 1
    val LEDR1 = 2
    val LEDR2 = 4
    val LEDR3 = 8
    val LEDR4 = 16
    val LEDR5 = 32
    val LEDR6 = 64
    val LEDR7 = 128
    val LEDR8 = 256
    val LEDR9 = 512
  }

  case object GPInput1 extends Bus { def length = 32 }
  case object GPOutput1 extends Bus { def length = 32 }
  case object GPInput2 extends Bus { def length = 32 }
  case object GPOutput2 extends Bus { def length = 32 }

  // FIXME: No models for DE1 yet
  protected def makeAreaModel: AreaModel       = new StratixVAreaModel
  protected def makeLatencyModel: LatencyModel = new StratixVLatencyModel
  def capacity: Area = AreaMap(ALMs->262400, Regs->524800, DSPs->1963, BRAM->2567, Channels->13)
}
