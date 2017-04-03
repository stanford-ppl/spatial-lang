import spatial._
import org.virtualized._

object DotProduct extends SpatialApp { // Regression (Dense) // Args: 1920
  import IR._

  type X = Int

  val tileSize = 640
  val innerPar = 1
  lazy val outerPar = 1

  @virtualize
  def dotproduct[T:Type:Num](aIn: Array[T], bIn: Array[T]): T = {
    val B  = tileSize (64 -> 64 -> 19200)
    val P1 = outerPar (1 -> 6)
    val P2 = innerPar (1 -> 192)
    val P3 = innerPar (1 -> 192)

    val size = aIn.length; bound(size) = 1920000

    val N = ArgIn[Int]
    setArg(N, size)

    val a = DRAM[T](N)
    val b = DRAM[T](N)
    val out = ArgOut[T]
    setMem(a, aIn)
    setMem(b, bIn)

    Accel {
      out := Reduce(Reg[T](0.to[T]))(N by B par P1){i =>
        val aBlk = SRAM[T](B)
        val bBlk = SRAM[T](B)
        Parallel {
          aBlk load a(i::i+B par 16)
          bBlk load b(i::i+B par 16)
        }
        Reduce(Reg[T](0.to[T]))(B par P2){ii => aBlk(ii) * bBlk(ii) }{_+_}
      }{_+_}
    }
    getArg(out)
  }

  @virtualize
  def main() {
    val N = args(0).to[Int]
    val a = Array.fill(N){ random[X] }
    val b = Array.fill(N){ random[X] }

    val result = dotproduct(a, b)
    val gold = a.zip(b){_*_}.reduce{_+_}

    println("expected: " + gold)
    println("result: " + result)

    val cksum = gold == result
    println("PASS: " + cksum + " (DotProduct)")
  }
}
