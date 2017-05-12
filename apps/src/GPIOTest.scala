import org.virtualized._
import spatial._

object GPIOTest extends SpatialApp {
  import IR._

  override val target = targets.DE1

  @virtualize 
  def main() { 
    val gpio: Bus = target.VGA
    val gpOut = StreamOut[Int32](gpio)

    Accel(*) {
      gpOut := 1023
    }
  }
}
