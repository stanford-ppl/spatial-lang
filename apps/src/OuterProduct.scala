import spatial._
import org.virtualized._

object OuterProduct extends SpatialApp { // Regression (Dense) // Args: 384 384
  import IR._
  type X = Int

  val tileSize1 = 192
  val tileSize2 = 192
  val op = 1
  //val tileSize1 = 96
  //val tileSize2 = 96
  //val op = 8
  val ip = 1

  @virtualize
  def outerproduct[T:Staged:Num](a: Array[T], b: Array[T]) = {
    val tileSizeA = tileSize1 (96 -> 96 -> 38400)
    val tileSizeB = tileSize2 (96 -> 96 -> 38400)
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
<<<<<<< HEAD
        Foreach(tileSizeA by 1, tileSizeB par innerPar){ (ii,jj) => outTile(ii, jj) = b1(ii) * b2(jj) }

        out(i::i+tileSizeA, j::j+tileSizeB) store outTile
        // val blkA = Reg[Int]
        // val blkB = Reg[Int]
        // Parallel {
        //   b1 load vec1(i::i+tileSizeA)
        //   b2 load vec2(j::j+tileSizeB)
        //   Pipe{ blkA := min(sizeA - i, tileSizeA) }
        //   Pipe{ blkB := min(sizeB - j, tileSizeB) }
        // }
        // Foreach(blkA by 1, blkB par innerPar){ (ii,jj) => outTile(ii, jj) = b1(ii) * b2(jj) } // 2

        // out(i::i+blkA, j::j+blkB) store outTile
=======
        //val blkA = Reg[Int]
        //val blkB = Reg[Int]
        Parallel {
          b1 load vec1(i::i+tileSizeA)
          b2 load vec2(j::j+tileSizeB)
          //Pipe{ blkA := min(sizeA - i, tileSizeA) }
          //Pipe{ blkB := min(sizeB - j, tileSizeB) }
        }
        Foreach(tileSizeA by 1, tileSizeB par innerPar){ (ii,jj) => outTile(ii, jj) = b1(ii) * b2(jj) } // 2

        out(i::i+tileSizeA, j::j+tileSizeB) store outTile
>>>>>>> origin/master
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
    val a = Array.tabulate(M) { i => i.to[X] }
    val b = Array.fill(N){ 1.as[X] }

    val result = outerproduct(a, b)

    val gold = Array.tabulate(M){i => Array.tabulate(N){j => a(i) * b(j) }}.flatten
    println("expected cksum: " + gold.map(a => a).reduce{_+_})
    println("result cksum:   " + result.map(a => a).reduce{_+_})
    (0 until M*N) foreach { i => assert(result(i) == gold(i)) }

    val cksum = result.zip(gold){_ == _}.reduce{_&&_}
    println("PASS: " + cksum + " (OuterProduct)")


  }
}
