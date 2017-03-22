package spatial.tests

import argon.core.Exceptions
import org.scalatest.{FlatSpec, Matchers}
import spatial.SpatialConfig
import org.virtualized._

object BasicFSM extends SpatialTest {
  import IR._

  @virtualize
  def main() {
    val dram = DRAM[Int](32)
    Accel {
      val bram = SRAM[Int](32)

      FSM[Int]{state => state < 32}{state =>
        bram(state) = state
      }{state => state + 1}

      dram store bram
    }
    val result = getMem(dram)
    for(i <- 0 until 32) { assert(result(i) == i, "Incorrect at index " + i) }
    println("PASS")
  }
}

object MatcherFSM extends SpatialTest {
  import IR._

  @virtualize
  def main() {
    val dram = DRAM[Int](32)
    Accel {
      val bram = SRAM[Int](32)

      FSM[Int]{state => state < 32}{state =>
        if (state < 16) {
          bram(31 - state) = state // 16:31 [15, 14, ... 0]
        }
        else {
          bram(state - 16) = state // 0:15 [16, 17, ... 31]
        }
      }{state => state + 1}

      dram store bram
    }
    val result = getMem(dram)
    val gold = Array.tabulate(32){i => if (i < 16) 16 + i else 31 - i }
    printArray(result, "Result")
    printArray(gold, "Gold")
    for (i <- 0 until 32){ assert(result(i) == gold(i)) }
    println("PASS")
  }
}

class FSMTests extends FlatSpec with Matchers with Exceptions {
  SpatialConfig.enableScala = true
  "BasicFSM" should "compile" in { BasicFSM.main(Array.empty) }
  "MatcherFSM" should "compile" in { MatcherFSM.main(Array.empty) }
}