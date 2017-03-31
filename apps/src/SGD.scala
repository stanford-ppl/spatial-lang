import spatial._
import org.virtualized._


/*





         Minibatch impelementation:
                             _
                            | |
                            |M|
                            | |
                            | |
                            | |
                            | |
                  D         |_|
             _____________   _       _                  _
            |             | |^|     | |                | |
          N |      X      | |Y|  -  |Y|  =>            |Y_err
            |_____________| |_|     |_|                |_|
                                                ____    _        _      _
                                               |    |  | |      | |    | |
                                               |    |  | |      |M|    |M|
                                               |    |  |Δ|  +   | | -> | |
                                               | X_T|  | |      | |    | |
                                               |    |  | |      | |    | |
                                               |    |  | |      | |    | |
                                               |____|  |_|      |_|    |_|


*/



object SGD extends SpatialApp { 
  import IR._

  type T = Int
  val modelSize = 192
  val tileSize = 96
  val innerPar = 2
  val outerPar = 1
  val margin = 1

  @virtualize
  def sgd_onept[T:Type:Num](x_in: Array[T], y_in: Array[T], alpha: T, epochs: Int, nn: Int) = {
    val E = ArgIn[Int]
    val N = ArgIn[Int]
    val A = ArgIn[T]
    val D = modelSize

    val ip = innerPar (1 -> 1)
    val op = outerPar (1 -> 1)

    setArg(E, epochs)
    setArg(N, nn)
    setArg(A, alpha)

    val x = DRAM[T](N,D)
    val y = DRAM[T](N)
    val result = DRAM[T](D)

    setMem(x, x_in)
    setMem(y, y_in)

    Accel {
      val y_tile = SRAM[T](tileSize)
      val sgdmodel = SRAM[T](D)
      Sequential.Foreach(E by 1) { e =>
        Sequential.Foreach (N by tileSize) { b =>
          y_tile load y(b::b+tileSize par op)
          // Sequential.Foreach.fold(tileSize by 1 par op)(sgdmodel) { i =>
          Sequential.Foreach(tileSize by 1) {i => 
            val y_err = Reg[T]
            // val update = SRAM[T](D)
            val x_tile = SRAM[T](D)
            Parallel{
              x_tile load x(b+i, 0 :: D par ip)
            }
            Pipe{ // This Foreach is here to make sure y_hat resets properly
              val y_hat = Reg[T]
              Reduce(y_hat)(D by 1 par ip){ j => x_tile(j) * sgdmodel(j) }{_+_}
              y_err := y_hat.value - y_tile(i)
            }

            Foreach (D by 1 par ip) { j =>
              sgdmodel(j) = sgdmodel(j) + x_tile(j)*y_err.value*A
            }
          }
        }
      }
      result(0::D par ip) store sgdmodel

    }

    getMem(result)

  }

  def printArr(a: Array[T], str: String = "") {
    println(str)
    (0 until a.length) foreach { i => print(a(i) + " ") }
    println("")
  }

  @virtualize
  def main() {
    val E = args(0).to[Int]
    val N = args(1).to[Int]
    val A = args(2).to[T] // Should be somewhere around 0.0001 for point-wise sgd
    val D = modelSize

    val sX = Array.fill(N){ Array.fill(D){ random[T](3.as[T])} }
    val sY = Array.fill(N)( random[T](10.as[T]) )
    val id = Array.tabulate(D){ i => i }
    val ep = Array.tabulate(E){ i => i }

    val result = sgd_onept(sX.flatten, sY, A, E, N)

    val gold = Array.empty[T](D)
    (0 until D) foreach { j => gold(j) = 0.as[T] }

    (0 until E) foreach { i =>
      val y_hat = sX.zip(sY){ case (row, y) => row.zip(gold) {_*_}.reduce{_+_} }
      val y_err = y_hat.zip(sY){case (a,b) => a - b}
      val update = id.map{ j =>
        val col = sX.map{_(j)}
        col.zip(y_err){case (a,b) => a*b}.reduce{_+_}
      }

      (0 until D) foreach { j => gold(j) = update(j)*A + gold(j) }
    }

    val cksum = gold.zip(result){ case (a,b) => a < b + margin && a > b - margin }.reduce{_&&_}
    printArr(result, "result: ")
    printArr(gold, "gold: ")
    println("PASS: " + cksum  + " (SGD) *Note that test was designed for minibatch SGD and not true point-wise SGD")
  }
}
