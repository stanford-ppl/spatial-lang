package spatial.tests

import argon.core.Exceptions
import argon.Config
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

object BasicCondFSM extends SpatialTest {
  import IR._

  @virtualize
  def main() {
    val dram = DRAM[Int](32)
    Accel {
      val bram = SRAM[Int](32)

      FSM[Int]{state => state < 32} { state =>
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

object DotProductFSM extends SpatialTest {
  import IR._

  @virtualize
  def main() {
    val vectorA = Array.fill(128){ random[Int](10) }
    val vectorB = Array.fill(128){ random[Int](10) }

    val vecA = DRAM[Int](128)
    val vecB = DRAM[Int](128)
    val out  = ArgOut[Int]

    setMem(vecA, vectorA)
    setMem(vecB, vectorB)

    Accel {
      FSM[Int](i => i < 128){i =>
        val a = SRAM[Int](16)
        val b = SRAM[Int](16)
        Parallel {
          a load vecA(i::i+16)
          b load vecB(i::i+16)
        }
        out := out + Reduce(0)(0 until 16){i => a(i) * b(i) }{_+_}
      }{i => i + 16 }
    }

    val result = getArg(out)
    val gold = vectorA.zip(vectorB){_*_}.reduce{_+_}

    assert(result == gold, "Result (" + result + ") did not equal expected (" + gold + ")")
    println("PASS")
  }
}


class FSMTests extends FlatSpec with Matchers with Exceptions {
  SpatialConfig.enableScala = true
  "BasicFSM" should "compile" in { BasicFSM.main(Array.empty) }
  "BasicCondFSM" should "compile" in { BasicCondFSM.main(Array.empty) }
  "DotProductFSM" should "compile" in { DotProductFSM.main(Array.empty) }
}