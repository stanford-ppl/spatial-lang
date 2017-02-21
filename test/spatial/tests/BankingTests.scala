package spatial.tests
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

object TwoDuplicatesPachinko extends SpatialTest {
  import IR._

  @virtualize
  def main() {
    val dram = DRAM[Int](32)
    val out = ArgOut[Int]

    Accel {
      val sram = SRAM[Int](16)
      val accum = Reg[Int]

      Reduce(accum)(32 by 16 par 2){i =>
        sram load dram(i::i+16)
        Reduce(16 par 16){ii => sram(ii) }{_+_}
      }{_+_}

      out := accum
    }
  }
}

class BankingTests extends FlatSpec with Matchers {
  SpatialConfig.enableScala = true
  "TwoDuplicatesSimple" should "have two duplicates of sram" in { TwoDuplicatesSimple.main(Array.empty) }
}
