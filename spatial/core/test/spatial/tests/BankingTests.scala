package spatial.tests
import argon.TestBenchFailed
import org.scalatest.{FlatSpec, Matchers}
import org.virtualized._


object TwoDuplicatesSimple extends SpatialTest {
  import spatial.dsl._

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

    printArray(result, "result")
    printArray(gold, "gold")

    gold.foreach{i => assert(result(i) == i) }
  }
}

// Nonsensical app, just to get structure there.
object TwoDuplicatesPachinko extends SpatialTest {
  import spatial.dsl._

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
  import spatial.dsl._

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
  import spatial.dsl._

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

object RegCoalesceTest extends SpatialTest {
  import spatial.dsl._

  @virtualize
  def main() {
    val out1 = ArgOut[Int]
    val out2 = ArgOut[Int]
    Accel {
      Foreach(16 by 1) {i =>
        val reg = Reg[Int]
        Pipe { reg := i }
        Pipe { out1 := reg.value }
        Pipe { out2 := reg.value }
      }
    }
  }
}

object SRAMCoalesceTest extends SpatialTest {
  import spatial.dsl._

  @virtualize
  def main() {
    Accel {
      val sram = SRAM[Int](32)
      val out1 = ArgOut[Int]
      val out2 = ArgOut[Int]
      Foreach(16 by 1){i =>
        Foreach(16 by 1){j => sram(j) = i*j }
        val sum = Reduce(0)(16 par 2){j => sram(j) }{_+_}
        val product = Reduce(0)(16 par 3){j => sram(j) }{_*_}
        out1 := sum
        out2 := product
      }
    }
  }
}

object LinearWriteRandomRead extends SpatialTest {
  import spatial.dsl._

  @virtualize
  def main() {
    Accel {
      val sram = SRAM[Int](16)
      val addr = SRAM[Int](16)
      val out1 = ArgOut[Int]
      val out2 = ArgOut[Int]
      Foreach(16 by 1){i =>
        Foreach(16 by 1 par 2){j =>
          sram(j) = i*j
          addr(j) = 16 - j
        }
        val sum = Reduce(0)(16 par 5){j => sram(addr(j)) }{_+_}
        out1 := sum
      }
    }
  }
}

class BankingTests extends FlatSpec with Matchers {
  "TwoDuplicatesSimple" should "have two duplicates of sram" in { TwoDuplicatesSimple.main(Array.empty) }
  "TwoDuplicatesPachinko" should "have two duplicates of sram" in { TwoDuplicatesPachinko.main(Array.empty) }

  "LegalFIFOParallelization" should "compile" in { LegalFIFOParallelization.main(Array.empty) }

  "RegCoalesceTest" should "be coalesced" in { RegCoalesceTest.main(Array.empty) }
  "SRAMCoalesceTest" should "NOT be coalesced" in { SRAMCoalesceTest.main(Array.empty) }
  "LinearWriteRandomRead" should "compile" in { LinearWriteRandomRead.main(Array.empty) }

  a [TestBenchFailed] should be thrownBy { IllegalFIFOParallelization.main(Array.empty) }
}
