import spatial._
import org.virtualized._

object MatMult_outer extends SpatialApp { // Regression (Dense) // Args: 32 96 96
  import IR._

  type X = Int

  val innerPar = 16
  val midPar = 1
  val outerPar = 5

  val tsm = 16
  val tsn = 48
  val tsp = 48

  @virtualize
  def MatMult_outer[T:Type:Num](A: Array[T], B: Array[T], C_init: Array[T], mm: Int, nn: Int, pp: Int) = {
    val M = ArgIn[Int]
    val N = ArgIn[Int]
    val P = ArgIn[Int]
    setArg(M,mm)
    setArg(N,nn)
    setArg(P,pp)

    val a = DRAM[T](M, P)
    val b = DRAM[T](P, N)
    // val c_init = DRAM[T](M, N)
    val c = DRAM[T](M, N)

    val op = outerPar (1 -> 1)
    val mp = midPar (1 -> 16)
    val ip = innerPar (1 -> 64)
    val px = 1 (1 -> 1) // Cannot parallelize accum across k blocks

    val bm = tsm (48 -> 48 -> 1920)
    val bn = tsn (48 -> 48 -> 1920)
    val bp = tsp (48 -> 48 -> 1920)

    setMem(a, A)
    setMem(b, B)
    setMem(c, C_init)

    Accel {
      Sequential.Foreach(M by bm, N by bn par op) { (i,j) =>
        val tileC = SRAM[T](bm, bn)
        tileC load c(i::i+bm, j::j+bn par 16)
        Foreach(P by bp par px) { k =>
          val tileA = SRAM[T](bm, bp) 
          val tileB = SRAM[T](bp, bn)
          Parallel {
            tileA load a(i::i+bm, k::k+bp par 16)
            tileB load b(k::k+bp, j::j+bn par 16)
          }
          // Requires tileC NOT to be reset until next j
          MemFold(tileC)(bp by 1 par mp){ kk =>
            val tileC_partial = SRAM[T](bm,bn)
            Foreach(bm by 1, bn by 1 par ip){ (ii,jj) =>
              tileC_partial(ii,jj) = tileA(ii,kk) * tileB(kk,jj)
            }
            tileC_partial
          }{_+_}
        }
        c(i::i+bm, j::j+bn par 16) store tileC
      }
    }
    getMem(c)
  }

  @virtualize
  def main() = {
    val M = args(0).to[Int]
    val N = args(1).to[Int]
    val P = args(2).to[Int]

    val a = Array.tabulate(M){ j => Array.tabulate(P){ i => (i + j * P) % 8 } } // Standard array
    val b = Array.tabulate(P){ j => Array.tabulate(N){ i => (i + j * N) % 8 } } // Standard array
    val c_init = Array.fill(M){ Array.fill(N){ 0.to[X] } }
    // val a = Array.fill(M){ Array.fill(P){random[T](100)} }
    // val b = Array.fill(P){ Array.fill(N){random[T](100)} }

    val result = MatMult_outer(a.flatten, b.flatten, c_init.flatten, M, N, P)

    val gold = Array.tabulate(M){i =>
      val aRow = a(i)
      Array.tabulate(N){j =>
        val bCol = b.map{row => row(j)}
        aRow.zip(bCol){_*_}.reduce{_+_}
      }
    }.flatten

    println("expected cksum: " + gold.map(a => a).reduce{_+_})
    println("result cksum: " + result.map(a => a).reduce{_+_})
    printArray(gold, "Gold: ")
    printArray(result, "Result: ")

    val cksum = result.zip(gold){_ == _}.reduce{_&&_}
    println("PASS: " + cksum + " (MatMult_outer)")
  }
}

object MatMult_inner extends SpatialApp { // Regression (Dense) // Args: 32 96 96
  import IR._

  type X = Int //FixPt[Signed,B16,B16]

  val innerPar = 16
  val midPar = 1
  val outerPar = 11

  val tsm = 16
  val tsn = 48
  val tsp = 48

  @virtualize
  def MatMult_inner[T:Type:Num](A: Array[T], B: Array[T], mm: Int, nn: Int, pp: Int) = {
    val M = ArgIn[Int]
    val N = ArgIn[Int]
    val P = ArgIn[Int]
    setArg(M,mm)
    setArg(N,nn)
    setArg(P,pp)

    val a = DRAM[T](M, P)
    val b = DRAM[T](P, N)
    val c = DRAM[T](M, N)

    val bm = tsm (1 -> 1536)
    val bn = tsn (64 -> 64 -> 1536)
    val bp = tsp (64 -> 64 -> 1536)

    val op = outerPar (1 -> 6)
    val mp = midPar   (1 -> 64)
    val ip = innerPar (1 -> 64)
    val px = 1 (1 -> 1) // Cannot parallelize accum across k blocks

    setMem(a, A)
    setMem(b, B)

    Accel {
      Foreach(M by bm, N by bn par op){(i,j) =>
        val tileC = SRAM[T](bm, bn)

        Foreach(P by bp par px){k =>
          val tileA = SRAM[T](bm, bp)
          val tileB = SRAM[T](bp, bn)
          Parallel {
            tileA load a(i::i+bm, k::k+bp par 1) // Reads M*N*P times
            tileB load b(k::k+bp, j::j+bn par 1)
          }
          Foreach(bm by 1, bn by 1 par mp){ (ii,jj) =>    // MetaPipe?
            val prod = Reduce(Reg[T])(bp by 1 par ip){kk => tileA(ii, kk) * tileB(kk, jj) }{_+_}
            val prev = mux(k == 0, 0.to[T], tileC(ii,jj))
            tileC(ii,jj) = prev + prod.value // Is a unit pipe that should be recognized as accum
          }
        }
        c(i::i+bm, j::j+bn) store tileC // Writes M*N times
      }
    }
    getMem(c)
  }

  @virtualize
  def main() = {
    val M = args(0).to[Int]
    val N = args(1).to[Int]
    val P = args(2).to[Int]

    val a = Array.tabulate(M){ i => Array.tabulate(P){ j => (i*P + j)%8 } }
    val b = Array.tabulate(P){ i => Array.tabulate(N){ j => (i*N + j)%8 } }
    // val a = Array.fill(M){ Array.fill(P){random[T](100)} }
    // val b = Array.fill(P){ Array.fill(N){random[T](100)} }

    val result = MatMult_inner(a.flatten, b.flatten, M, N, P)

    val gold = Array.tabulate(M){i =>
      val aRow = a(i)
      Array.tabulate(N){j =>
        val bCol = b.map{row => row(j)}
        aRow.zip(bCol){_*_}.reduce{_+_}
      }
    }.flatten

    val gold_cksum = gold.map(a => a).reduce{_+_}
    val result_cksum = result.map(a => a).reduce{_+_}
    printArray(gold, "Gold: ")
    printArray(result, "Result: ")
    println("expected cksum: " + gold_cksum)
    println("result cksum:   " + result_cksum)

    // (0 until M*N) foreach { i => assert(result(i) == gold(i)) }

    val cksum = result_cksum == gold_cksum
    println("PASS: " + cksum + " (MatMult_inner)")

  }


}
