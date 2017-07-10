package spatial.tests
import org.scalatest.{FlatSpec, Matchers}
import spatial.dsl._
import org.virtualized._

object OuterSwitchTest extends SpatialTest {
  @virtualize def main(): Unit = {

    val in = ArgIn[Int]
    setArg(in, 20)

    val dram = DRAM[Int](32)

    Accel {
      val data = SRAM[Int](32)
      if (in.value <= 28) {
        Sequential.Foreach((in.value+4) by 1){ i => data(i) = i }
      }
      dram(0::32) store data
    }

    printArray(getMem(dram), "dram")
  }
}

class SwitchTests extends FlatSpec with Matchers {
  "OuterSwitchTest" should "have one controller in the SwitchCase" in { OuterSwitchTest.runTest() }
}
