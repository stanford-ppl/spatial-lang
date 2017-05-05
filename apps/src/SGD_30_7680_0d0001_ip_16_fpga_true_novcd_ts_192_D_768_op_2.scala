import org.virtualized._
import spatial._


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


object SGD_30_7680_0d0001_ip_16_fpga_true_novcd_ts_192_D_768_op_2 extends SpatialApp { // Regression (Dense) // Args: 40 32 0.0001
  import IR._

  type T = FixPt[TRUE, _16, _16]
val D = 768
  val margin = 1

val ip = 16
val op = 2

val ts = 192

  @virtualize
  def sgd_onept[T: Type : Num](x_in: Array[T], y_in: Array[T], alpha: T, epochs: Int, nn: Int) = {
    val E = ArgIn[Int]
    val N = ArgIn[Int]
    val A = ArgIn[T]
val D = 768

val ip = 16
val op = 2

    setArg(E, epochs)
    setArg(N, nn)
    setArg(A, alpha)

    val x = DRAM[T](N, D)
    val y = DRAM[T](N)
    val result = DRAM[T](D)

    setMem(x, x_in)
    setMem(y, y_in)

    Accel {
      val y_tile = SRAM[T](ts)
      val sgdmodel = SRAM[T](D)
      Pipe(D by 1) { i => sgdmodel(i) = 0.to[T] }
      Sequential.Foreach(E by 1) { e =>
        Sequential.Foreach(N by ts) { b =>
          y_tile load y(b :: b + ts par ip)
          Sequential.Foreach(ts by 1) { i =>
            val y_err = Reg[T]
            val x_tile = SRAM[T](D)
            Parallel {
              x_tile load x(b + i, 0 :: D par ip)
            }
            Pipe {
              val y_hat = Reg[T]
              Reduce(y_hat)(D by 1 par ip) { j => x_tile(j) * sgdmodel(j) } {
                _ + _
              }
              y_err := y_tile(i) - y_hat.value
            }

            Foreach(D by 1 par ip) { j =>
              sgdmodel(j) = sgdmodel(j) + x_tile(j) * y_err.value * A
            }
          }
        }
      }
      result(0 :: D par ip) store sgdmodel

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
val D = 768

    val sX = Array.fill(N) {
      Array.fill(D) {
        random[T](3.to[T]) + 1.to[T]
      }
    }
    val ideal_model = Array.tabulate(D) { i => 3.to[T] }
    val sY = Array.tabulate(N) { i => ideal_model.zip(sX.apply(i)){_*_}.reduce{_+_} }
    val id = Array.tabulate(D) { i => i }
    val ep = Array.tabulate(E) { i => i }

    val result = sgd_onept(sX.flatten, sY, A, E, N)


    // (0 until E) foreach { i =>
    //   (0 until N) foreach { j =>
    //     val y_hat = sX.apply(j).zip(gold) {_*_}.reduce{_+_}
    //     val y_err = sY.apply(j) - y_hat
    //     val update = sX.apply(j).zip(gold){case (x,g) => g + x*A*y_err}
    //     (0 until D) foreach { q => gold(q) = update(q) }
    //   }
    // }

    val cksum = ideal_model.zip(result) { case (a, b) => abs(a - b) < margin }.reduce{_&&_}
    printArr(result, "result: ")
    printArr(ideal_model, "gold: ")
    println("PASS: " + cksum + " (SGD)")
  }
}

