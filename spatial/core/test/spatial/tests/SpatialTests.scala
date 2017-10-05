package spatial.tests

import org.virtualized._
import org.scalatest.{FlatSpec, Matchers}
import argon.core._
import argon.ArgonAppRunner
import argon.TestBenchFailed
import spatial.SpatialApp
import spatial.aliases.spatialConfig

// Create a testbench which runs Scala tests
trait SpatialTest extends SpatialApp with ArgonAppRunner {
  override val testbench = true
  override def settings() {
    config.verbosity = 1
    spatialConfig.enableSim = true
  }
  def runTest(): Unit = this.main(Array.empty)
}


class SpatialTests extends FlatSpec with Matchers {
  val noargs = Array[String]()

  // --- Tests
  object NumericTest extends SpatialTest {
    import spatial.dsl._
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
    import spatial.dsl._
    @virtualize
    def main() {
      val in = ArgIn[Int]
      setArg(in, 0)

      Accel {
        val reg = Reg[Int](0)
        val x = reg + in.value
        val y = in.value - reg
        println(x)
        println(y)
      }
    }
  }

  object SRAMTest extends SpatialTest {
    import spatial.dsl._
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
    import spatial.dsl._
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
    import spatial.dsl._
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
    import spatial.dsl._
    @virtualize
    def main() {
      Accel {
        val product = Reg[Int](1)
        Reduce(product)(16 by 1){i => i } {_ * _}
        val sum2 = Reduce(0)(0 :: 1 :: 16 par 2) { i => i } {_ + _}
        println(product.value)
        println(sum2.value)
      }
    }
  }

  object MemReduceTest extends SpatialTest {
    import spatial.dsl._
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

  object UtilTest extends SpatialTest {
    import spatial.dsl._

    @virtualize
    def main() {
      val array = Array.tabulate(32){i => random[Int](10) }
      val matrix = (0::4,0::10){(i,j) => random[Int](10) }

      Accel { }

      printArray(array)
      printMatrix(matrix)
    }
  }

  // A[B] = C
  /*object NDScatterTest extends SpatialTest {
    import spatial.dsl._

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
  }*/

  object UntransferredValueTest extends SpatialTest {
    import spatial.dsl._

    @virtualize
    def main() {
      val x = random[Int]
      val y = ArgIn[Int]
      Accel {
        y := x
      }
      println(getArg(y))
    }
  }

  object DRAMSizeTest extends SpatialTest {
    import spatial.dsl._

    @virtualize
    def main() {
      val arr = args.map{ a => a.to[Int] }
      val x = DRAM[Int](arr.length)
      val N = ArgIn[Int]
      setArg(N, args.length)
      setMem(x, arr)
      val out = ArgOut[Int]
      Accel {
        out := Reduce(0)(N by 5) { i =>
          val sram = SRAM[Int](12)
          sram load x(i :: i + 5)
          Reduce(0)(5 by 1) { j => sram(j) } {_ + _}
        }{_+_}
      }
      println(getArg(out))
    }
  }

  object IllegalVarAssignTest extends SpatialTest {
    import spatial.dsl._

    @virtualize
    def main(): Unit = {
      var x = 0
      Accel {
        x = 32
      }
      println(x)
    }
  }

  object IllegalVarReadTest extends SpatialTest {
    import spatial.dsl._
    @virtualize def main(): Unit = {
      var x = 32
      Accel {
        val m = x + 32
        println(m)
      }
    }
  }

  object IllegalVarNewTest extends SpatialTest {
    import spatial.dsl._
    @virtualize def main(): Unit = {
      Accel {
        var x = 32
        println(x + 1)
      }
    }
  }

  "NumericTest" should "compile" in { NumericTest.main(noargs) }
  "RegTest" should "compile" in { RegTest.main(noargs) }
  "SRAMTest" should "compile" in { SRAMTest.main(noargs) }
  "MuxTests" should "compile" in { MuxTests.main(noargs) }
  "ReduceTest" should "compile" in { ReduceTest.main(noargs) }
  "FoldAccumTest" should "compile" in { FoldAccumTest.main(noargs) }
  "MemReduceTest" should "compile" in { MemReduceTest.main(noargs) }
  "UtilTest" should "compile" in { UtilTest.main(noargs) }
  // a [TestBenchFailed] should be thrownBy { NDScatterTest.main(noargs) }
  a [TestBenchFailed] should be thrownBy { UntransferredValueTest.main(noargs) }
  a [TestBenchFailed] should be thrownBy { DRAMSizeTest.main(noargs) }

  a [TestBenchFailed] should be thrownBy { IllegalVarAssignTest.main(noargs) }
  a [TestBenchFailed] should be thrownBy { IllegalVarNewTest.main(noargs) }
  a [TestBenchFailed] should be thrownBy { IllegalVarReadTest.main(noargs) }
}
