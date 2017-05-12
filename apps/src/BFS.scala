import org.virtualized._
import spatial._

object BFS extends SpatialApp { // DISABLED Regression (Sparse) // Args: 6 10
  import IR._

  val tileSize = 8000
  val edges_per_node = 6 // Will make this random later

  @virtualize
  def bfs(nodesIn: Array[Int], edgesIn: Array[Int], countsIn: Array[Int], idsIn: Array[Int], n: Int, e: Int, average_nodes_per_edge: Int) = {
    val edges = DRAM[Int](e)
    val counts = DRAM[Int](n)
    val ids = DRAM[Int](n)
    val result = DRAM[Int](n)

    setMem(edges, edgesIn)
    setMem(counts, countsIn)
    setMem(ids, idsIn)

    val depth = ArgIn[Int]
    val d = args(1).to[Int]
    setArg(depth, d)
    val anpe = ArgIn[Int]
    setArg(anpe, average_nodes_per_edge)

    Accel {
      val frontierNodes = SRAM[Int](tileSize)
      val frontierCounts = SRAM[Int](tileSize)
      val frontierIds = SRAM[Int](tileSize)
      val frontierLevels = SRAM[Int](tileSize)
      val currentNodes = SRAM[Int](tileSize)
      val pieceMem = SRAM[Int](tileSize)

      val concatReg = Reg[Int](0)
      val numEdges = Reg[Int](1)

      // Flush first few for scatter safety
      Foreach(anpe by 1){i =>  //dummy read of anpe
        Parallel{
          frontierNodes(i) = 0.to[Int]
          // frontierCounts(i) = 0.to[Int]
          frontierLevels(i) = 0.to[Int]
          currentNodes(i) = 0.to[Int]
          pieceMem(i) = 0.to[Int]
        }
      }
      Parallel {
        frontierIds load ids(0 :: tileSize)
        frontierCounts load counts(0 :: tileSize)
      }

      Sequential.Foreach(depth.value by 1) { i => /* Loop 1 */
        val nextLen = Reg[Int](1)
        val nextId = Reg[Int](1)
        Sequential.Foreach(numEdges by 1) { k => /* Loop 2 */
          // val lastLen = Reg[Int](1)

          val fetch = currentNodes(k)
          // val lastFetch = currentNodes(k - 1)
          nextId := frontierIds(fetch)
          nextLen := frontierCounts(fetch)
          // lastLen := frontierCounts(lastFetch)

          // pieceMem load edges(nextId :: nextId + nextLen)            
          pieceMem load edges(nextId :: nextId + nextLen)       

          // val frontierAddr = SRAM[Int](tileSize)
          // Foreach(nextLen by 1) { kk =>
          Foreach(nextLen by 1) { kk =>
            /* Since Loop 2 is a metapipe and we read concatReg before
               we write to it, this means iter0 and iter1 both read
               0 in concatReg.  I.e. we always see the previous iter's
               value of concatReg, so we should add nextLen to it here
               if we are not on the first iter (since concatReg is and
               should be 0)
            */
            // val plus = mux(k == 0, 0.to[Int], anpe.value) // Really adding in lastLen
            val frontierAddr = kk + concatReg.value
            frontierNodes(frontierAddr) = pieceMem(kk)
          }
          concatReg := min(tileSize.to[Int], concatReg + nextLen)
        }

        Foreach(concatReg by 1) { kk => currentNodes(kk) = frontierNodes(kk) }
        Foreach(concatReg by 1) { kk => frontierLevels(kk) = i + 1 }
        result(currentNodes, concatReg) scatter frontierLevels
        numEdges := concatReg
        concatReg := 0.to[Int]
      }
    }

    getMem(result)
  }

  @virtualize
  def main() {
    /* NEW VERSION FOR PERFORMANCE MEASUREMENTS */
    val E = 9600000
    val N = tileSize
    val average_nodes_per_edge = args(0).to[Int]
    val spacing = 3 
    val ed = E //args(1).to[SInt] // Set to roughly max_edges_per_node * N 

    val OCnodes = Array.tabulate(N) {i => 0.to[Int]}
    val OCedges = Array.tabulate(ed){ i => i*2 % N}
    val OCids = Array.tabulate(N)( i => average_nodes_per_edge*average_nodes_per_edge*i+1 % E)
    val OCcounts = Array.tabulate(N){ i => random[Int](average_nodes_per_edge-1)*2+1}

    val result = bfs(OCnodes, OCedges, OCcounts, OCids, N, E, average_nodes_per_edge)
    val gold = (6*1) + (16*2) + (22*3) + (5*4)
    // println("Cksum: " + gold + " == " + result.reduce{_+_})

    val cksum = gold == result.reduce{_+_}
    printArray(result, "result: ")
    println("Cksum = " + result.reduce{_+_})
    // println("PASS: " + cksum + " (BFS)")



  }

}


object BFS_FSM extends SpatialApp { // DISABLED Regression (Sparse) // Args: 6 10
  import IR._

  val tileSize = 8000

  @virtualize
  def bfs(nodesIn: Array[Int], edgesIn: Array[Int], lensIn: Array[Int], idsIn: Array[Int], n: Int, e: Int, average_nodes_per_edge: Int) = {
    val edges = DRAM[Int](e)
    val lens = DRAM[Int](n)
    val ids = DRAM[Int](n)
    val result = DRAM[Int](n)

    setMem(edges, edgesIn)
    setMem(lens, lensIn)
    setMem(ids, idsIn)

    val depth = ArgIn[Int]
    val d = args(1).to[Int]
    setArg(depth, d)
    val anpe = ArgIn[Int]
    setArg(anpe, average_nodes_per_edge)

    Accel {
      val init = 0
      val gatherEdgeInfo = 1
      val denseEdgeLoads = 2
      val scatterDepths = 3
      val done = 4

      val layer = Reg[Int](0)
      val depths = SRAM[Int](tileSize)
      val frontierStack = FILO[Int](tileSize) // TODO: Eventually allow scatters on stacks
      val idList = SRAM[Int](tileSize)
      val lenList = SRAM[Int](tileSize)
      val frontier = SRAM[Int](tileSize)
      val size = Reg[Int](1)
      FSM[Int]{state => state < done}{state =>
        if (state == init.to[Int]) {
          frontier(0) = 0.to[Int]
        } else if (state == gatherEdgeInfo.to[Int]) {
          // Collect edge ids and lens
          idList gather ids(frontier par 1, size.value)
          lenList gather lens(frontier par 1, size.value)
        } else if (state == denseEdgeLoads.to[Int]) {
          // Accumulate frontier
          Foreach(size.value by 1) {i =>
            val start = idList(i)
            val end = lenList(i) + start
            frontierStack load edges(start::end par 1)
          }
        } else if (state == scatterDepths.to[Int]) {
          // Grab size of this scatter
          size := frontierStack.numel
          // Drain stack and set up scatter addrs + depth srams
          Foreach(frontierStack.numel by 1) { i => 
            depths(i) = layer.value
            frontier(i) = frontierStack.pop()
          }
          result(frontier, size) scatter depths
        }
      }{state => mux(state == init.to[Int], gatherEdgeInfo, 
                  mux(state == gatherEdgeInfo, denseEdgeLoads,
                    mux(state == denseEdgeLoads, scatterDepths, 
                      mux(state == scatterDepths && layer.value < depth, gatherEdgeInfo, done))))
        }

    }

    getMem(result)
  }

  @virtualize
  def main() {
    /* NEW VERSION FOR PERFORMANCE MEASUREMENTS */
    val E = 9600000
    val N = 96000
    val average_nodes_per_edge = args(0).to[Int]
    val spacing = 3 
    val ed = E //args(1).to[SInt] // Set to roughly max_edges_per_node * N 

    val OCnodes = Array.tabulate(N) {i => 0.to[Int]}
    val OCedges = Array.tabulate(ed){ i => i*2 % N}
    val OCids = Array.tabulate(N)( i => average_nodes_per_edge*average_nodes_per_edge*i+1 % E)
    val OCcounts = Array.tabulate(N){ i => random[Int](average_nodes_per_edge-1)*2+1}

    val result = bfs(OCnodes, OCedges, OCcounts, OCids, N, E, average_nodes_per_edge)
    val gold = 0
    // println("Cksum: " + gold + " == " + result.reduce{_+_})

    val cksum = gold == result.reduce{_+_}
    printArray(result, "result: ")
    println("Cksum = " + result.reduce{_+_})
    // println("PASS: " + cksum + " (BFS)")



  }

}

