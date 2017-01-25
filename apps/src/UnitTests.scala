import spatial._
import org.virtualized._

object SimpleSequential extends SpatialApp {
  import IR._

  def simpleSeq(xIn: Int, yIn: Int): Int = {
    val innerPar = 1 (1 -> 1 -> 1)
    val tileSize = 96 (96 -> 96 -> 96)

    val x = ArgIn[Int]
    val y = ArgIn[Int]
    val out = ArgIn[Int]
    setArg(x, xIn)
    setArg(y, yIn)

    Accel {
      val b1 = SRAM[Int](tileSize)
      Sequential.Foreach(tileSize by 1 par innerPar){ii =>
        b1(ii) = x.value * ii
      }
      out := b1(y)
    }

    getArg(out)
  }

  def main() {
    val x = args(0).to[Int]
    val y = args(1).to[Int]
    val result = simpleSeq(x, y)

  }
}
