import org.virtualized._
import spatial._

object MemReduceTest extends SpatialApp {
  import IR._

  @virtualize
  def main() {
    val y = ArgOut[Int]

    Accel {
      val accum = SRAM[Int](32, 32)
      // TODO: Use MemReduce to generate entries for 
      // matrix A as defined in README.md. 
      y := accum(0,0)
    }
    
    val result = getArg(y)
    println("expected: " + 0)
    println("result: " + result)
  }
}
