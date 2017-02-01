package spatial.tests

import org.virtualized.SourceContext
import org.virtualized.virtualize
import org.scalatest.{FlatSpec, Matchers}
import argon._
import argon.core.Exceptions
import argon.utils.deleteExts
import spatial.{SpatialApp, SpatialIR}

// Create a testbench IR which runs Scala tests
trait SpatialTestIR extends SpatialIR with RunnerCore { self =>
  override val testbench = true
  override def settings() {
    Config.verbosity = 3
  }
}

trait SpatialTest extends SpatialApp {
  override val IR: SpatialTestIR = new SpatialTestIR { }
}

// --- Tests
object NumericTest extends SpatialTest {
  import IR._
  @virtualize
  def main() {
    Accel {
      val x = random[Int]
      val y = random[Int]
      val z = -x
      val q = z + y
      val m = z + x
      val f = m + y
      println(q)
      println(m)
      println(f)
    }
  }
}

object RegTest extends SpatialTest {
  import IR._
  @virtualize
  def main() {
    val in = ArgIn[Int]
    setArg(in, 0)

    Accel {
      val reg = Reg[Int](0)
      val x = reg + in.value
      println(x)
    }
  }
}

object SRAMTest extends SpatialTest {
  import IR._
  @virtualize
  def main() {
    Accel {
      val sram = SRAM[Int](1, 16)
      sram(0, 0) = 10
      println(sram(0, 0))
    }
  }
}

object MuxTests extends SpatialTest {
  import IR._
  @virtualize
  def main() {
    Accel {
      val x = random[Int]
      val y = random[Int]
      val z = min(x, y)
      val q = max(x, y)
      val m = min(x, lift(0))
      val n = max(lift(0), y)
      val p = max(lift(0), lift(5))

      println("" + z + ", " + q + ", " + m + ", " + n + ", " + p)
    }
  }
}


object ReduceTest extends SpatialTest {
  import IR._
  @virtualize
  def main() {
    Accel {
      val sram = SRAM[Int](1, 16)
      val sum = Reduce(0)(16 by 1) { i => sram(0, i) } { (a, b) => a + b }
      println(sum.value)
    }
  }
}

object FoldAccumTest extends SpatialTest {
  import IR._
  @virtualize
  def main() {
    Accel {
      val product = Reg[Int](1)
      Reduce(product)(16 by 1) { i => i } {_ * _}
      val sum2 = Reduce(0)(0 :: 1 :: 16 par 2) { i => i } {_ + _}
      println(product.value)
      println(sum2.value)
    }
  }
}

object MemReduceTest extends SpatialTest {
  import IR._
  @virtualize
  def main() {
    Accel {
      val accum = SRAM[Int](32, 32)
      MemReduce(accum)(0 until 32) { i =>
        val inner = SRAM[Int](32, 32)
        Foreach(0 until 32, 0 until 32) { (j, k) => inner(j, k) = j + k }
        inner
      } { (a, b) => a + b }

      println(accum(0, 0))
    }
  }
}

// A[B] = C
object NDScatterTest extends SpatialTest {
  import IR._

  @virtualize
  def main() {
    val A = DRAM[Int](64)
    val B = DRAM[Int](64)
    val C = DRAM[Int](64)

    //b := B(0::32)
    //c := C(0::32)
    Accel {
      val b = SRAM[Int](32)
      val c = SRAM[Int](32,32)

      A(b) scatter c
      A(b) scatter c
    }
  }
}


class SpatialTests extends FlatSpec with Matchers with Exceptions {
  val noargs = Array[String]()
  "NumericTest" should "compile" in { NumericTest.main(noargs) }
  "RegTest" should "compile" in { RegTest.main(noargs) }
  "SRAMTest" should "compile" in { SRAMTest.main(noargs) }
  "MuxTests" should "compile" in { MuxTests.main(noargs) }
  "ReduceTest" should "compile" in { ReduceTest.main(noargs) }
  "FoldAccumTest" should "compile" in { FoldAccumTest.main(noargs) }
  "MemReduceTest" should "compile" in { MemReduceTest.main(noargs) }
  a [TestBenchFailed] should be thrownBy { NDScatterTest.main(noargs) }
}
