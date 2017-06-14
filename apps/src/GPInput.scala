import org.virtualized._
import spatial.dsl._

object GPInput extends SpatialApp {


  override val target = targets.DE1

  @virtualize 
  def main() { 
    // pin def
    val gpin1: Bus = target.GPInput1
    val gpin2: Bus = target.GPInput2
    val leds = target.LEDR

    // realization
    val in1 = StreamIn[Int](gpin1)
    val in2 = StreamIn[Int](gpin2)
    val ledOutput = StreamOut[Int](leds)

    Accel(*) {
      ledOutput := in1.value()
    }
  }
}
