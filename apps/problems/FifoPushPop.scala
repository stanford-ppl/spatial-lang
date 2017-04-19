import org.virtualized._
import spatial._

object FifoPushPop extends SpatialApp {
  import IR._

  def fifopushpop(N: Int) = {
    val tileSize = 16

    val size = ArgIn[Int]
    setArg(size, N)
    val acc = ArgOut[Int]

    Accel {
      //TODO: Create a FIFO
      //TODO: Create a register used for accumulating sums at each step

      // We want to use two reduces to get the sum. Here let's use tileSize = 16
      // and N = 16 as an example. The outer reduce loop would reduce N by tileSize, 
      // and the inner one reduces tileSize by 1. At each iteration, the inner reduce 
      // generates a temporary sum, which is accumulated by the outer reduce 
      // to generate the final sum. In this example, the sum will be 1+2+...+15 = 120 in
      // the inner reduce. The outer reduce will run for 1 loop and return 120.
      // TODO: Implement 2 reduce loops to calculate the overall sum. You can use 
      // a FIFO to buffer the intermediate result.

      // Assign accum to ArgOut
      acc := accum
    }
    getArg(acc)
  }

  @virtualize
  def main() {
    val arraySize = args(0).to[Int]

    val gold = Array.tabulate(arraySize){ i => i }.reduce{_+_}
    val dst = fifopushpop(arraySize)

    println("gold: " + gold)
    println("dst: " + dst)

    val cksum = dst == gold
    println("PASS: " + cksum + " (FifoPushPop)")
  }
}

