import org.virtualized._
import spatial.dsl._

object GPOutput extends SpatialApp {


  override val target = targets.DE1

  @virtualize 
  def main() { 
    val gpout1: Bus = target.GPOutput1
    val gpout2: Bus = target.GPOutput2
    val out1 = StreamOut[UInt32](gpout1)
    val out2 = StreamOut[UInt32](gpout2)

    Accel(*) {
      out1 := 1023
      out2 := 1023
    }
  }
}
