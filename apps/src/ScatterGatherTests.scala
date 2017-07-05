import spatial.dsl._
import org.virtualized._

object FIFOGatherTest extends SpatialApp {
  val n = 32
  val T = 8

  @virtualize def test(dat: Array[Int], adr: Array[Int]): Array[Int] = {
    val N = ArgIn[Int]
    setArg(N, n)
    val data = DRAM[Int](N)
    val addr = DRAM[Int](N)
    val out  = DRAM[Int](N)
    setMem(data, dat)
    setMem(addr, adr)

    Accel {
      val localAddr = FIFO[Int](T)
      val localData = FIFO[Int](T)
      localAddr load addr(0::T)
      localData gather data(localAddr)
      out(0::T) store localData
    }
    getMem(out)
  }
  @virtualize def main(): Unit = {
    val addrs = Array.tabulate(n){i => random[Int](n) }
    val datas = Array.tabulate(n){i => random[Int](10) }
    printArray(addrs, "addrs")
    printArray(datas, "datas")
    val out = test(datas, addrs)

    val gold = Array.tabulate(n){i => if (i < T) datas(addrs(i)) else 0 }

    val isCorrect = gold.zip(out){(a,b) => a == b }.reduce{_&&_}
    printArray(out, "out")
    printArray(gold, "gold")
    println("PASS: " + isCorrect)
  }
}
