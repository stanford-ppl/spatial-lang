package spatial.tests
import argon.core.Exceptions
import spatial.SpatialConfig
import org.scalatest.{FlatSpec, Matchers}
import org.virtualized._

object BitSelects extends SpatialTest {
  import IR._
  @virtualize
  def main() {
    Accel {
      val x = 9.to[Int]
      val v = x.as32b
      println(v)
      println("bit 0: " + x(0))
      println("bit 1: " + x(1))
      println("bit 2: " + x(2))
      println("bit 3: " + x(3))

      assert(x(0), "First bit should be 1")
      assert(!x(1), "Second bit should be 0")
      assert(!x(2), "Third bit should be 0")
      assert(x(3), "Fourth bit should be 1")
    }
  }
}

object UserVectors extends SpatialTest {
  import IR._

  type Q4 = FixPt[TRUE,_4,_0]

  @virtualize
  def main() {
    Accel {
      val A = 10.to[Q4]
      val B = 11.to[Q4]
      val C = 12.to[Q4]
      val v = Vector(A, B, C)
      println(v.as12b) // Should be 0b1010,1011,1100

      val x = v(7::0)
      println(x.as8b) // Should be 0b1011,1100
    }
  }
}

class BitTwiddling extends FlatSpec with Matchers with Exceptions {
  SpatialConfig.enableScala = true

  "Bit selection" should "compile" in { BitSelects.main(Array.empty) }

}
