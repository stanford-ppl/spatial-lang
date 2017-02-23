package spatial.tests
import argon.core.Exceptions
import org.scalatest.{FlatSpec, Matchers}
import org.virtualized._
import spatial.SpatialConfig

object TwoDuplicatesSimple extends SpatialTest {
  import IR._

  @virtualize
  def main() {
    val dram = DRAM[Int](32)
    val dram2 = DRAM[Int](32)

    Accel {
      val sram = SRAM[Int](32)

      Foreach(32 by 16){i =>
        Foreach(16 par 16){ii =>
          sram(ii) = i + ii
        }

        dram(i::i+16 par 16) store sram
        dram2(i::i+16 par 16) store sram
      }
    }
    val result = getMem(dram)
    val gold = Array.tabulate(32){i => i}
    gold.foreach{i => assert(result(i) == i) }
  }
}

// Nonsensical app, just to get structure there.
object TwoDuplicatesPachinko extends SpatialTest {
  import IR._

  @virtualize
  def main() {
    val dram = DRAM[Int](512)
    val out = ArgOut[Int]

    Accel {
      Foreach(16 by 1){i =>

        val sram = SRAM[Int](16)

        sram load dram(i::i+16)

        Foreach(32 by 16 par 2){j =>
          val accum = Reg[Int]
          Reduce(accum)(16 par 16){k => sram(k) }{_+_}
          out := accum.value
        }
      }
    }
  }
}



object LegalFIFOParallelization extends SpatialTest {
  import IR._

  @virtualize
  def main() {

    val out = ArgOut[Int]

    Accel {
      val fifo = FIFO[Int](32)

      Foreach(16 by 1) {i =>
        Foreach(8 par 8){j => fifo.enq(j) }
        Foreach(8 par 1){j => out := fifo.deq() }
      }
    }

  }
}

object IllegalFIFOParallelization extends SpatialTest {
  import IR._

  @virtualize
  def main() {

    val out = ArgOut[Int]

    Accel {
      val fifo = FIFO[Int](32)

      Foreach(16 par 2) {i =>
        Foreach(8 by 1){j => fifo.enq(j) }
        Foreach(8 by 1){j => out := fifo.deq() }
      }
    }

  }
}

class BankingTests extends FlatSpec with Matchers with Exceptions {
  SpatialConfig.enableScala = true
  "TwoDuplicatesSimple" should "have two duplicates of sram" in { TwoDuplicatesSimple.main(Array.empty) }
  "TwoDuplicatesPachinko" should "have two duplicates of sram" in { TwoDuplicatesPachinko.main(Array.empty) }

  "LegalFIFOParallelization" should "compile" in { LegalFIFOParallelization.main(Array.empty) }
  a [TestBenchFailed] should be thrownBy { IllegalFIFOParallelization.main(Array.empty) }
}
