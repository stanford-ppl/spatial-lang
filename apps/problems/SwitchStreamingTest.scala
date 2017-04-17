import spatial._
import org.virtualized._
import forge._

object SwitchStreamingTest extends SpatialApp {
  import IR._

  override val target = targets.DE1
  type UINT10 = FixPt[FALSE,_10,0]

  @virtualize
  def main() {
    val inputSwitch = target.SliderSwitch
    val input = StreamIn[UINT10](inputSwitch)
    val io1 = HostIO[Int]
    Accel(*) {
      val switchVal = input.value()
      io1 := switchVal
    }
  }
}
