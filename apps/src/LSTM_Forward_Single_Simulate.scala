import spatial.dsl._
import org.virtualized._

object LSTM_Forward_Single_Simulate extends SpatialApp {
  import IR._

  // TODO: need to rethink of precision
  type X = FixPt[TRUE,_32,_32]

  @virtualize
  def main() = {
    val D_h = 64
    val d = 64
    val N = 32

    val x = ArgIn[Int]
    val y = ArgOut[Int]
    setArg(x, N)

    Accel {
      y := x + 4
    }

    val result = getArg(y)

    println("Fake test Arg = " + y)

    def loadMatrix(fileName: String, nRow: Int, nCol: Int) : Array[Array[Float]]  = {
      val csvArray = loadCSV1D[Float](fileName, "\n")
      val csvMat = Array.tabulate(nRow) { h =>
        Array.tabulate(nCol) (w => csvArray(h * N + w))
      }

      return csvMat
    }

    def sigmoid(x: Float) : Float  = {
      return 1 / (1 + exp(-x))
    }

    // TODO: Get a pretrained model and fetch out weights from one of the gates
    val W_i = loadMatrix("/home/tianzhao/data/64_by_64_eles.csv", D_h, d)
    val U_i = loadMatrix("/home/tianzhao/data/64_by_64_eles.csv", D_h, d)
    val W_f = loadMatrix("/home/tianzhao/data/64_by_64_eles.csv", D_h, d)
    val U_f = loadMatrix("/home/tianzhao/data/64_by_64_eles.csv", D_h, d)
    val W_o = loadMatrix("/home/tianzhao/data/64_by_64_eles.csv", D_h, d)
    val U_o = loadMatrix("/home/tianzhao/data/64_by_64_eles.csv", D_h, d)
    val W_c = loadMatrix("/home/tianzhao/data/64_by_64_eles.csv", D_h, d)
    val U_c = loadMatrix("/home/tianzhao/data/64_by_64_eles.csv", D_h, d)
    val x_t = loadMatrix("/home/tianzhao/data/64_by_32_eles.csv", D_h, N)
    val h_t_1 = loadMatrix("/home/tianzhao/data/64_by_32_eles.csv", D_h, N)
    val W_c_t_1 = loadMatrix("/home/tianzhao/data/64_by_32_eles.csv", D_h, N)

    //val W_i = loadCSV1D[X]("/home/tianzhao/data/64_by_64_eles.csv", "\n")
    //println("first double " + W_i(1))


    val goldHidden = Array.tabulate(D_h) { i =>
      val W_i_row = W_i(i)
      val U_i_row = U_i(i)
      val W_f_row = W_f(i)
      val U_f_row = W_f(i)
      val W_o_row = W_o(i)
      val U_o_row = W_o(i)
      val W_c_row = W_c(i)
      val U_c_row = W_c(i)
      Array.tabulate(N) { j =>
        val x_t_col = x_t.map{row => row(j)}
        val h_t_1_col = h_t_1.map{row => row(j)}
        val W_c_t_1_col = W_c_t_1.map{row => row(j)}

        val newMemVal = (sigmoid(W_i_row.zip(x_t_col){_*_}.reduce{_+_} + U_i_row.zip(h_t_1_col){_*_}.reduce{_+_}) *
            tanh(W_c_row.zip(x_t_col){_*_}.reduce{_+_} + U_c_row.zip(h_t_1_col){_*_}.reduce{_+_})) +
          (W_c_t_1_col(i) * sigmoid(W_f_row.zip(x_t_col){_*_}.reduce{_+_} + U_f_row.zip(h_t_1_col){_*_}.reduce{_+_}))

        tanh(newMemVal) * sigmoid(W_o_row.zip(x_t_col){_*_}.reduce{_+_} + U_o_row.zip(h_t_1_col){_*_}.reduce{_+_})
      }
    }.flatten
    printArray(goldHidden, "gold = ")
  }
}
