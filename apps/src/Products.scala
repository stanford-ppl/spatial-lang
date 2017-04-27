import spatial._
import org.virtualized._

object OuterProduct extends SpatialApp { //Regression (Dense) // Args: 76800 76800 
  import IR._
  type X = Int

  val tileSize1 = 64
  val tileSize2 = 64
  val op = 2
  val ip = 2

  @virtualize
  def outerproduct[T:Type:Num](a: Array[T], b: Array[T]) = {
    val tileSizeA = tileSize1 (64 -> 64 -> 38400)
    val tileSizeB = tileSize2 (64 -> 64 -> 38400)
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
          b1 load vec1(i::i+tileSizeA par innerPar)
          b2 load vec2(j::j+tileSizeB par innerPar)
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

object DotProduct extends SpatialApp { // Regression (Dense) // Args: 640
  import IR._

  type X = Int

  val tileSize = 320
  val innerPar = 16
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
        //val ts = Reg[Int](0)
        //ts := min(B, N-i)
        val aBlk = SRAM[T](B)
        val bBlk = SRAM[T](B)
        Parallel {
          //aBlk load a(i::i+ts.value par P3)
          //bBlk load b(i::i+ts.value par P3)
          aBlk load a(i::i+B par P3)
          bBlk load b(i::i+B par P3)
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
