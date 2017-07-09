package spatial.tests
import spatial.dsl._
import org.virtualized._
import org.scalatest.{Matchers, FlatSpec}

object IntPrinting extends SpatialTest {
  @virtualize def main(): Unit = {
    Accel { }
    val x = -391880660.to[Int]
    println("x: " + x)
  }
}

class MiscTests extends FlatSpec with Matchers {
  "IntPrinting" should "print correctly" in { IntPrinting.runTest() }
}
