import spatial._
import org.virtualized._

object BFS extends SpatialApp {
  import IR._

  val tileSize = 8000
  val edges_per_node = 6 // Will make this random later

  @virtualize
  def bfs(nodesIn: Array[Int], edgesIn: Array[Int], countsIn: Array[Int], idsIn: Array[Int], n: Int, e: Int) = {
    val nodes  = DRAM[Int](n)
    val edges  = DRAM[Int](e)
    val counts = DRAM[Int](n)
    val ids    = DRAM[Int](n)
    val result = DRAM[Int](n)

    setMem(nodes, nodesIn)
    setMem(edges, edgesIn)
    setMem(counts, countsIn)
    setMem(ids, idsIn)

    Accel {
      val frontierNodes  = SRAM[Int](tileSize)
      val frontierCounts = SRAM[Int](tileSize)
      val frontierIds    = SRAM[Int](tileSize)
      val frontierLevels = SRAM[Int](tileSize)
      val currentNodes   = SRAM[Int](tileSize)
      val pieceMem       = SRAM[Int](tileSize)

      val concatReg = Reg[Int](0)
      val numEdges  = Reg[Int](1)

      Parallel {
        frontierIds    load ids(0::tileSize)
        frontierCounts load counts(0::tileSize)
      }

      Sequential.Foreach(4 by 1){i =>         /* Loop 1 */
        Reduce(concatReg)(numEdges by 1){k => /* Loop 2 */
          val nextLen   = Reg[Int]
          val nextId    = Reg[Int]
          val lastLen   = Reg[Int]

          val fetch = currentNodes(k)
          val lastFetch = currentNodes(k - 1)
          nextId  := frontierIds(fetch)
          nextLen := frontierCounts(fetch)
          lastLen := frontierCounts(lastFetch)

          pieceMem load edges(nextId :: nextId + nextLen)

          val frontierAddr = SRAM[Int](tileSize)
          Foreach(nextLen by 1){ kk =>
            /* Since Loop 2 is a metapipe and we read concatReg before
               we write to it, this means iter0 and iter1 both read
               0 in concatReg.  I.e. we always see the previous iter's
               value of concatReg, so we should add nextLen to it here
               if we are not on the first iter (since concatReg is and
               should be 0)
            */
            val plus = mux(k == 0, lift(0), lastLen.value)
            frontierAddr(kk) = kk + concatReg.value + plus
          }
          Foreach(nextLen by 1){kk =>
            frontierNodes(frontierAddr(kk)) = pieceMem(kk)
          }
          nextLen
        }{_+_}

        Foreach(concatReg by 1){ kk => currentNodes(kk) = frontierNodes(kk) }
        Foreach(concatReg by 1){ kk => frontierLevels(kk) = i+1 }
        result(currentNodes, concatReg) scatter frontierLevels
        numEdges := concatReg
      }
    }

    getMem(result)
  }

  @virtualize
  def main() {
    /** TODO **/
  }

}
