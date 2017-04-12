import spatial._
import org.virtualized._

object StreamInOut extends SpatialApp {
  import IR._

  def main() {
    case object Input extends Bus { val length = 32 }
    case object Output extends Bus { val length = 32 }

    val input  = StreamIn[Int32](Input)
    val output = StreamOut[Int32](Output)

    Accel(*) {
      output := input
    }

  }
}
