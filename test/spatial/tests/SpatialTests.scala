package spatial.tests

import org.virtualized.SourceContext
import org.virtualized.virtualize
import org.scalatest.{FlatSpec, Matchers}
import spatial.spec._
import argon._
import argon.ops._
import argon.core.Exceptions
import argon.utils.deleteExts
import argon.traversal.IRPrinter

trait SpatialTest extends SpatialCompiler with RunnerCore { self =>
  override val testbench = true
  override def settings() {
    Config.verbosity = 3
  }
}

//trait SpatialLib extends argonLib with SpatialTest

/*object SpatialREPL extends SpatialLib with SpatialApp {
  import org.scala_lang.org.virtualized.SourceContext

  def main() ()
  context = Nil
  Config.lib = true
  def summarize() = {
    val result = lastStm // Most recent
    val deps = context
    val effects = summarizeScope(deps)
    Block(result.lhs, effects, deps)(mtyp(result.tp))
  }

  var traversalNum = 0
  var blk: Option[Block[_<:Sym]] = None

  def show() = {
    if (blk.isEmpty) blk = Some(summarize())
    printer.pass(blk.get)(mtyp(blk.get.tp))
  }

  def run() = {
    if (blk.isEmpty) blk = Some(summarize())
    State.inLib { Eval.pass(blk.get)(mtyp(blk.get.tp)) }
  }

  def step() = {
    if (blk.isEmpty) blk = Some(summarize())
    blk = Some(passes(traversalNum).pass(blk.get)(mtyp(blk.get.tp)))
    traversalNum += 1
  }
  def compile() = {
    if (blk.isEmpty) blk = Some(summarize())
    for (t <- passes.drop(traversalNum)) { blk = Some(t.pass(blk.get)(mtyp(blk.get.tp))) }
  }

  override def reset() = {
    super.reset()
    blk = None
    traversalNum = 0
    //context = Nil
  }
  def all() {
    allStms.foreach{stm: Stm => Console.println(stm.lhs + " = " + stm.rhs)}
  }
}*/

// --- Tests

object NumericTest extends SpatialTest with NumericTestApp
trait NumericTestApp extends SpatialApp {
  @virtualize
  def main() {
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

object RegTest extends SpatialTest with RegTestApp
trait RegTestApp extends SpatialApp {
  @virtualize
  def main() {
    val reg = Reg[Int](0)
    val x = reg.value
    println(x)
  }
}

object SRAMTest extends SpatialTest with SRAMTestApp
trait SRAMTestApp extends SpatialApp {
  @virtualize
  def main() {
    val sram = SRAM[Int](1,16)
    sram(0,0) = 10

    println(sram(0,0))
  }
}

object MuxTests extends SpatialTest with MuxTestsApp
trait MuxTestsApp extends SpatialApp {
  @virtualize
  def main() {
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


object ReduceTest extends SpatialTest with ReduceTestApp
trait ReduceTestApp extends SpatialApp {
  @virtualize
  def main() {
    val sram = SRAM[Int](1,16)
    val sum = Reduce(0)(16 by 1){i => sram(0,i) }{(a,b) => a + b}
    println(sum.value)
  }
}

object FoldAccumTest extends SpatialTest with FoldAccumTestApp
trait FoldAccumTestApp extends SpatialApp {
  @virtualize
  def main() {
    val product = Reg[Int](1)
    Reduce(product)(16 by 1){i => i }{_*_}

    val sum2 = Reduce(0)(0::1::16 par 2){i => i }{_+_}

    println(product.value)
    println(sum2)
  }
}

object MemReduceTest extends SpatialTest with MemReduceTestApp
trait MemReduceTestApp extends SpatialApp {
  @virtualize
  def main() {
    val accum = SRAM[Int](32,32)
    MemReduce(accum)(0 until 32){i =>
      val inner = SRAM[Int](32, 32)
      Foreach(0 until 32, 0 until 32){(j,k) => inner(j,k) = j + k }
      inner
    }{(a,b) => a + b}

    println(accum(0,0))
  }
}

// A[B] = C
object NDScatterTest extends SpatialTest with NDScatterTestApp
trait NDScatterTestApp extends SpatialApp {
  @virtualize
  def main() {
    val A = DRAM[Int](64)
    val B = DRAM[Int](64)
    val C = DRAM[Int](64)

    val b = SRAM[Int](32)
    val c = SRAM[Int](32,32)

    //b := B(0::32)
    //c := C(0::32)
    A(b) scatter c
    A(b) scatter c
  }
}

class SpatialTests extends FlatSpec with Matchers with Exceptions {
  val noargs = Array[String]()
  deleteExts(Config.logDir, ".log")

  "NumericTest" should "compile" in { NumericTest.main(noargs) }
  "RegTest" should "compile" in { RegTest.main(noargs) }
  "SRAMTest" should "compile" in { SRAMTest.main(noargs) }
  "MuxTests" should "compile" in { MuxTests.main(noargs) }
  "ReduceTest" should "compile" in { ReduceTest.main(noargs) }
  "FoldAccumTest" should "compile" in { FoldAccumTest.main(noargs) }
  "MemReduceTest" should "compile" in { MemReduceTest.main(noargs) }
  a [TestBenchFailed] should be thrownBy { NDScatterTest.main(noargs) }

}
