import spatial._
import org.virtualized._

object Kmeans extends SpatialApp { // Regression (Dense) // Args: 5 384
  import IR._

  type X = Int

  val num_cents = 16
  val dim = 32
  val tileSize = 32
  val innerPar = 1
  val outerPar = 1
  val element_max = 10
  val margin = (element_max * 0.2).as[X]

  val MAXK = num_cents
  val MAXD = dim

  @virtualize
  def kmeans[T:Staged:Num](points_in: Array[T], numPoints: Int, numCents: Int, numDims: Int, it: Int) = {
    bound(numPoints) = 960000
    bound(numCents) = MAXK
    bound(numDims) = MAXD

    val BN = tileSize (96 -> 96 -> 9600)
    val BD = MAXD
    val PX = 1 (1 -> 1)
    val P0 = innerPar (32 -> 96 -> 192) // Dimensions loaded in parallel
    val P1 = outerPar (1 -> 12)         // Sets of points calculated in parallel
    val P2 = innerPar (1 -> 4 -> 96)    // Dimensions accumulated in parallel (outer)
    val P3 = innerPar (1 -> 4 -> 16)    // Points calculated in parallel
    val PR = innerPar (1 -> 4 -> 96)
    val P4 = innerPar (1 -> 4 -> 96)

    val iters = ArgIn[Int]
    val N     = ArgIn[Int]
    val K     = numCents //ArgIn[Int]
    val D     = numDims //ArgIn[Int]

    setArg(iters, it)
    setArg(N, numPoints)
    // setArg(K, numCents)
    // setArg(D, numDims)

    val points = DRAM[T](N, D)    // Input points
    val centroids = DRAM[T](num_cents*dim) // Output centroids
    setMem(points, points_in)

    Accel {
      val cts = SRAM[T](MAXK, MAXD)

      // Load initial centroids (from points)
      cts load points(0::K, 0::D par P0)

      val DM1 = D - 1

      Sequential.Foreach(iters by 1){epoch =>

        val newCents = SRAM[T](MAXK,MAXD)
        // For each set of points
        Foreach(N by BN par PX){i =>
          val pts = SRAM[T](BN, BD)
          pts load points(i::i+BN, 0::BD par P0)

          // For each point in this set
          MemReduce(newCents)(BN par PX){pt =>
            // Find the index of the closest centroid
            val accum = Reg[Tup2[Int,T]]( pack(0.as[Int], 100000.as[T]) )
            val minCent = Reduce(accum)(K par PX){ct =>
              val dist = Reduce(Reg[T])(D par P2){d => (pts(pt,d) - cts(ct,d)) ** 2 }{_+_}
              pack(ct, dist.value)
            }{(a,b) =>
              mux(a._2 < b._2, a, b)
            }

            // Store this point to the set of accumulators
            val localCent = SRAM[T](MAXK,MAXD)
            Foreach(K by 1, D par P2){(ct,d) =>
              val elem = mux(d == DM1, 1.as[T], pts(pt, d))
              localCent(ct, d) = mux(ct == minCent.value._1, elem, 0.as[T])
            }
            localCent
          }{_+_} // Add the current point to the accumulators for this centroid
        }

        val centCount = SRAM[T](MAXK)
        Foreach(K by 1 par PX){ct => centCount(ct) = newCents(ct,DM1) } // Until diagonal banking is allowed

        // Average each new centroid
        // val centsOut = SRAM[T](MAXK, MAXD)
        Foreach(K by 1, D par P0){(ct,d) =>
          cts(ct, d) = newCents(ct,d) / centCount(ct)
        }
        // Flush centroid accumulator
        Foreach(K by 1, D par P0){(ct,d) =>
          newCents(ct,d) = 0.as[T]
        }
      }

      val flatCts = SRAM[T](MAXK * MAXD)
      Foreach(K by 1, D by 1) {(i,j) =>
        flatCts(i*D+j) = cts(i,j)
      }
      // Store the centroids out
      centroids(0::K*D par P2) store flatCts
    }

    getMem(centroids)
  }

  @virtualize
  def main() {
    val iters = args(0).to[Int]
    val N = args(1).to[Int]
    val K = num_cents //args(2).to[SInt];
    val D = dim //args(3).to[SInt];

    val pts = Array.tabulate(N){i => Array.tabulate(D){d => if (d == D-1) 1.as[X] else random[X](element_max) }}

    // println("points: ")
    // for (i <- 0 until N) { println(i.mkString + ": " + pts(i).mkString(", ")) }

    val result = kmeans(pts.flatten, N, K, D, iters)

    val cts = Array.empty[Array[X]](K)
    for (k <- 0 until K) {
      cts(k) = Array.tabulate(D){i => pts(k).apply(i) }
    }
    val ii = Array.tabulate(K){i => i}

    for(epoch <- 0 until iters) {
      def dist[T:Staged:Num](p1: Array[T], p2: Array[T]) = p1.zip(p2){(a,b) => (a - b)**2 }.reduce(_+_)

      // Make weighted points
      val map = pts.groupByReduce{pt =>
        val dists = cts.map{ct => dist(ct, pt) }
        dists.zip(ii){(a,b) => pack(a,b) }.reduce{(a,b) => if (a._1 < b._1) a else b}._2  // minIndex
      }{pt => pt}{(x,y) => x.zip(y){_+_} }

      // Average
      for (k <- 0 until K) {
        // Assumes at least one point per centroid
        val wp = map(k)
        val n  = wp(D - 1)
        cts(k) = Array.tabulate(D){d => if (d == D-1) 1.as[X] else wp(d)/n }
      }
    }

    val gold = cts.flatten

    printArray(gold, "gold: ")
    printArray(result, "result: ")

    for (i <- 0 until result.length) {
      val diff = result(i) - gold(i)
      if (abs(diff) > margin)
        println("[" + i + "] gold: " + gold(i) + ", result: " + result(i) + ", diff: " + diff)
    }

    val cksum = result.zip(gold){ case (o, g) => (g < (o + margin)) && g > (o - margin)}.reduce{_&&_}

    println("PASS: " + cksum + " (Kmeans)")
  }

}
