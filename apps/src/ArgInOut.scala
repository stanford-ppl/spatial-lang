import org.virtualized._
import spatial._

object ArgInOut extends SpatialApp {
  import IR._

  @virtualize
  def main() {
    val x = ArgIn[Int]
    val y = ArgOut[Int]
    val N = args(0).to[Int]

    setArg(x, N)

    Accel {
      y := x + 4
    }

    val result = getArg(y)
    val gold = N + 4
    println("expected: " + gold)
    println("result: " + result)
  }
}
