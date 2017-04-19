package spatial.tests

import argon.core.Exceptions
import org.scalatest.{FlatSpec, Matchers}
import org.virtualized._

object SimpleRetimePipe extends SpatialTest {
  import IR._

  @virtualize def main(): Unit = {
    val a = ArgIn[Int]
    val b = ArgIn[Int]
    val c = ArgIn[Int]
    val d = ArgOut[Int]
    Accel {
      d := a * b + c
    }
    println("d: " + getArg(d))
  }
}

class RetimingTests extends FlatSpec with Matchers with Exceptions {
  "SimpleRetimePipe" should "create one delay line" in {
    SimpleRetimePipe.main(Array.empty)
    val delays = SimpleRetimePipe.IR.NodeData.value.collect{case dly:SimpleRetimePipe.IR.ShiftRegNew[_] => dly }
    delays.length shouldBe 1
    delays.head.size shouldBe 2 // Latency of integer multiplier is assumed to be 2 here
  }
}