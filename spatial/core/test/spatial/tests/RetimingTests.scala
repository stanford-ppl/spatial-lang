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

object RetimeLoop extends SpatialTest {
  import IR._

  @virtualize def main(): Unit = {
    Accel {
      val x = Reg[Int]
      val sram = SRAM[Int](16, 16)
      x := Reduce(0)(0 until 16){i => i}{_+_}

      Foreach(0 until 16, 0 until 16){(i,j) =>
        sram(i,j) = ((i*j + 3) + x + 4) * 3
      }
    }
  }
}

object NestedPipeTest extends SpatialTest { // Regression (Unit) // Args: 6
  import IR._
  IR.testArgs = List("6")

  @virtualize
  def main() {
    // Declare SW-HW interface vals
    val x = ArgIn[Int]
    val y = ArgOut[Int]
    val N = args(0).to[Int]

    // Connect SW vals to HW vals
    setArg(x, N)

    // Create HW accelerator
    Accel {
      Pipe(5 by 1) { i =>
        Pipe(10 by 1) { j =>
          Pipe {y := 3*(j + 4 + x + i)+x/4}
        }
      }
    }

    // Extract results from accelerator
    val result = getArg(y)

    // Create validation checks and debug code
    val gold = 3*(N + 4 + 4 + 9) + N / 4
    println("expected: " + gold)
    println("result: " + result)
  }
}




class RetimingTests extends FlatSpec with Matchers with Exceptions {
  "SimpleRetimePipe" should "create one delay line" in {
    SimpleRetimePipe.main(Array.empty)
    val delays = SimpleRetimePipe.IR.NodeData.value.collect{case dly:SimpleRetimePipe.IR.ShiftRegNew[_] => dly }
    delays.length shouldBe 3
  }

  "RetimeLoop" should "be retimed" in { RetimeLoop.main(Array.empty) }

  "NestedPipeTest" should "be retimed" in { NestedPipeTest.main(Array.empty) }
}