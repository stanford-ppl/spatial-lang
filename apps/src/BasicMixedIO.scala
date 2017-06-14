import org.virtualized._
import spatial.dsl._

object BasicMixedIO extends SpatialApp {

  @virtualize 
  def main() { 
    val cst1 = 10
    val io1 = HostIO[Int]
    setArg(io1, cst1)
    Accel {
      Pipe { io1 := io1.value + 2}
    }

    val r1 = getArg(io1)
    val g1 = 12
    println("expected: " + g1)
    println("received: " + r1)
  }
}
