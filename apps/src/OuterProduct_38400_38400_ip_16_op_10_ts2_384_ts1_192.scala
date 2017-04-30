import spatial._
import org.virtualized._

object OuterProduct_38400_38400_ip_16_op_10_ts2_384_ts1_192 extends SpatialApp { // Regression (Dense) // Args: 192 192
  import IR._
  type X = Int

val ts1 = 192
val ts2 = 384
val op = 10
val ip = 16

  @virtualize
  def outerproduct[T:Type:Num](a: Array[T], b: Array[T]) = {
    val tileSizeA = ts1 (64 -> 64 -> 38400)
    val tileSizeB = ts2 (64 -> 64 -> 38400)
    val outerPar  = op (1 -> 4)
    val innerPar  = ip (1 -> 256)

    val M = a.length;  bound(M) = 38400
    val N = b.length;  bound(N) = 38400

    val sizeA = ArgIn[Int]
    val sizeB = ArgIn[Int]
    setArg(sizeA, M)
    setArg(sizeB, N)

    val vec1 = DRAM[T](sizeA)
    val vec2 = DRAM[T](sizeB)
    val out = DRAM[T](sizeA, sizeB)

    setMem(vec1, a)
    setMem(vec2, b)

    Accel {
      Foreach(sizeA by tileSizeA, sizeB by tileSizeB par outerPar){ (i,j) =>
        val b1 = SRAM[T](tileSizeA)
        val b2 = SRAM[T](tileSizeB)
        val outTile = SRAM[T](tileSizeA, tileSizeB)
        //val blkA = Reg[Int]
        //val blkB = Reg[Int]
        Parallel {
          b1 load vec1(i::i+tileSizeA par 16)
          b2 load vec2(j::j+tileSizeB par 16)
          //Pipe{ blkA := min(sizeA - i, tileSizeA) }
          //Pipe{ blkB := min(sizeB - j, tileSizeB) }
        }
        Foreach(tileSizeA by 1, tileSizeB par innerPar){ (ii,jj) => outTile(ii, jj) = b1(ii) * b2(jj) } // 2

        out(i::i+tileSizeA, j::j+tileSizeB par 16) store outTile
      }
    }
    getMem(out)
  }

  @virtualize
  def main() = {
    val M = args(0).to[Int]
    val N = args(1).to[Int]
    // val a = Array.fill(M)(random[T](100))
    // val b = Array.fill(N)(random[T](100))
    val a = Array.tabulate(M) { i => (i % 64).to[X] }
    val b = Array.tabulate(N){ i => (i % 64).to[X] }

    val result = outerproduct(a, b)

    val gold = Array.tabulate(M){i => Array.tabulate(N){j => a(i) * b(j) }}.flatten
    val gold_cksum = gold.map(a => a).reduce{_+_}
    val result_cksum = result.map(a => a).reduce{_+_}
    println("expected cksum: " + gold_cksum)
    println("result cksum:   " + result_cksum)
    // (0 until M*N) foreach { i => assert(result(i) == gold(i)) }

    val cksum = result_cksum == gold_cksum
    println("PASS: " + cksum + " (OuterProduct)")


  }
}

