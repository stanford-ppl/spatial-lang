import spatial._
import org.virtualized._

object LedStreamingTest extends SpatialApp {
  import IR._
  override val target = targets.DE1

  type bit = FixPt[FALSE,_1,_0]
  @struct class LEDs(LED0: bit = 0, LED1: bit = 0, LED2: bit = 0,
                     LED3: bit = 0, LED4: bit = 0, LED5: bit = 0,
                     LED6: bit = 0, LED7: bit = 0, LED8: bit = 0,
                     LED9: bit = 0)
  @virtualize
  def main() {
    val outputLEDR: Bus = target.LEDR
    val output = StreamOut[LEDRArray](outputLEDR)

    Accel(*) {
      val leds := LEDs(LED5 = 1, LED6 = 1)
      output := leds
    }
  }
}

