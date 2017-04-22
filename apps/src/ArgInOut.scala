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
      // TODO: Set y = x + 4 here
      y := x + 4
    }

    val result = getArg(y)
    val gold = N + 4
    println("expected: " + gold)
    println("result: " + result)
  }
}


object StructTest extends SpatialApp {
  import IR._

  @struct case class MyStruct(x: FixPt[TRUE,_32,_0], y: FixPt[TRUE,_32,_0])

  @virtualize def main(): Unit = {
    val x = ArgOut[MyStruct]
    Accel {
      x := MyStruct(32, 32)
    }
  }
}
