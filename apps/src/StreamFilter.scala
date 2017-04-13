import spatial._
import org.virtualized._

object StreamFilter extends SpatialApp {
  import IR._

  @virtualize
  def main() {
    val input  = StreamIn[Int32](GPInput)
    val output = StreamOut[Int32](GPOutput)

    Accel(*) {
      val element = input.value
      if (element > 0) output := element
    }

  }
}
