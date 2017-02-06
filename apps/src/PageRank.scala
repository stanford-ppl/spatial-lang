import spatial._
import org.virtualized._

object PageRank extends SpatialApp {
  import IR._
  type Elem = Float //FixPt[Signed, B16, B16]
  type X = Float

  /*
                                          0
         _________________________________|__________________________________________________________
        |                   |                 |                  |                 |                 |
        1                   3                 5                  7                 9                 11
     ___|______        _____|____         ____|__          ______|______           |        _________|____________
    |     |    |      |     |    |       |       |        |      |      |          |       |       |        |     |
    2     50   55     4     92   49      150     6        8      10     12        42      110     210      310   311
   _|_    _|_   |    _|_    |   _|_      |     __|_       |      |      |         |        |       |      _|_     |
  |   |  |   |  |   |   |   |  |   |     |    |    |      |      |      |         |        |       |     |   |    |
  57 100 58 101 140 60 102  99 120 115   13  103  104    105    106    108        43      111     211   300  301  290
                    |
              ______|________
             |   |   |   |   |
             80 131 132 181 235



  */
  val edges_per_page = 6 // Will make this random later
  val margin = 1

  @virtualize
  def pagerank[T:Num](
    pagesIN:  Array[T],
    edgesIN:  Array[Int],
    countsIN: Array[T],
    edgeIdIN: Array[Int],
    edgeLenIN: Array[Int],
    itersIN: Int,
    dampIN: T,
    np: Int
  ) = {

    val NE = 9216
    val tileSize = 768 // For now
    val iters = ArgIn[Int]
    val NP    = ArgIn[Int]
    val damp  = ArgIn[T]
    setArg(iters, itersIN)
    setArg(NP, np)
    setArg(damp, dampIN)

    val OCpages    = DRAM[T](NP)
    val OCedges    = DRAM[Int](NE)    // srcs of edges
    val OCcounts   = DRAM[T](NE)    // counts for each edge
    val OCedgeId   = DRAM[Int](NP) // Start index of edges
    val OCedgeLen  = DRAM[Int](NP) // Number of edges for each page
    // val OCresult   = DRAM[T](np)

    setMem(OCpages, pagesIN)
    setMem(OCedges, edgesIN)
    setMem(OCcounts, countsIN)
    setMem(OCedgeId, edgeIdIN)
    setMem(OCedgeLen, edgeLenIN)

    Accel {
      Sequential.Foreach(iters by 1){ iter =>
        // val oldPrIdx = iter % 2.as[SInt]
        // val newPrIdx = mux(oldPrIdx == 1, 0.as[SInt], 1.as[SInt])
        Sequential.Foreach(NP by tileSize) { tid =>
          val currentPR = SRAM[T](tileSize)
          val initPR = SRAM[T](tileSize)

          val edgesId = SRAM[Int](tileSize)
          val edgesLen = SRAM[Int](tileSize)
          Parallel {
            initPR load OCpages(tid::tid+tileSize)
            edgesId load OCedgeId(tid :: tid+tileSize)
            edgesLen load OCedgeLen(tid :: tid+tileSize)
          }

          Sequential.Foreach(tileSize by 1) { pid =>
            val startId = edgesId(pid)
            val numEdges = Reg[Int](0)
            Pipe{ numEdges := edgesLen(pid) }

            def pageRank(id: Int) = mux(id <= pid, initPR(pid), currentPR(pid))

            // Gather edges indices and counts
            val edges = SRAM[Int](tileSize)
            val counts = SRAM[T](tileSize)
            Parallel {
              edges load OCedges(startId :: startId + numEdges.value)
              counts load OCcounts(startId :: startId + numEdges.value)
            }

            // Triage edges based on if they are in current tile or offchip
            val offLoc = SRAM[Int](tileSize)
            val offAddr = Reg[Int](-1)
            Sequential.Foreach(numEdges.value by 1){ i =>
              val addr = edges(i) // Write addr to both tiles, but only inc one addr
              val onchip = addr >= tid && addr < tid+tileSize
              offAddr := offAddr.value + mux(onchip, 0, 1)
              offLoc(i) = mux(onchip, offAddr.value, -1.as[Int])
            }

            // Set up gather addresses
            val frontierOff = SRAM[Int](tileSize)
            Sequential.Foreach(numEdges.value by 1){i =>
              frontierOff(offLoc(i)) = edges(i)
            }

            // Gather offchip ranks
            val gatheredPR = SRAM[T](tileSize)
            gatheredPR gather OCpages(frontierOff, offAddr.value)

            // Compute new PR
            val pr = Reduce(Reg[T])(numEdges.value by 1){ i =>
              val addr = edges(i)
              val off  = offLoc(i)
              val onchipRank = pageRank(addr - tid)

              val offchipRank = gatheredPR(off)

              val rank = mux(off == -1.as[Int], onchipRank, offchipRank)

              rank / counts(i)
            }{_+_}
            //val pr = Reduce(numEdges.value by 1)(0.as[T]){ i => frontier(i) / counts(i).to[T] }{_+_}

            // Update PR
            currentPR(pid) = pr.value * damp + (1.as[T] - damp)

            // Reset counts (Plasticine: assume this is done by CUs)
            /*Parallel{
              Pipe{onAddr := 0}
              Pipe{offAddr := 0}
            }*/

          }
          OCpages(tid::tid+tileSize) store currentPR
        }
      }
    }
    getMem(OCpages)
  }

  @virtualize
  def main() {
    val iters = args(0).to[Int]
    val NP = args(1).to[Int]
    val damp = args(2).to[X]
    val NE = 18432

    val pages = Array.tabulate(NP){i => random[X](3)}
    val edges = Array.tabulate(NP){i => Array.tabulate(edges_per_page) {j =>
      if (i < edges_per_page) j else i - j}}.flatten
    val counts = Array.tabulate(NP){i => Array.tabulate(edges_per_page) { j => edges_per_page.as[X] }}.flatten
    val edgeId = Array.tabulate(NP){i => i*edges_per_page }
    val edgeLen = Array.tabulate(NP){i => edges_per_page.as[Int] }

    val result = pagerank(pages, edges, counts, edgeId, edgeLen, iters, damp, NP)


    val gold = Array.empty[X](NP)
    // Init
    for (i <- 0 until NP) {
      gold(i) = pages(i)
    }

    // Really bad imperative version
    for (ep <- 0 until iters) {
      for (i <- 0 until NP) {
        val numEdges = edgeLen(i)
        val startId = edgeId(i)
        val iterator = Array.tabulate(numEdges){kk => startId + kk}
        val these_edges = iterator.map{j => edges(j)}
        val these_pages = these_edges.map{j => gold(j)}
        val these_counts = these_edges.map{j => counts(j)}
        val pr = these_pages.zip(these_counts){ (p,c) =>
          // println("page " + i + " doing " + p + " / " + c)
          p/c
        }.reduce{_+_}
        // println("new pr for " + i + " is " + pr)
        gold(i) = pr*damp + (1.as[X]-damp)
      }
    }

    printArray(gold, "gold: ")
    printArray(result, "result: ")
    val cksum = result.zip(gold){ case (o, g) => (g < (o + margin)) && g > (o - margin)}.reduce{_&&_}
    println("PASS: " + cksum + " (PageRank)")

  }
}
