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

    def loadMatrix(fileName: String) : Array[Array[X]]  = {
      val csvArray = loadCSV1D[X](fileName, "\n")
      val csvMat = Array.tabulate(D_h) { h =>
        Array.tabulate(N) (w => csvArray(h * N + w).to[X])
      }

      return csvMat
    }

    // TODO: Get a pretrained model and fetch out weights from one of the gates
    val W_i = loadMatrix("/home/tianzhao/data/64_by_64_eles.csv", "\n")
    val U_i = loadMatrix("/home/tianzhao/data/64_by_64_eles.csv", "\n")
    val W_f = loadMatrix("/home/tianzhao/data/64_by_64_eles.csv", "\n")
    val U_f = loadMatrix("/home/tianzhao/data/64_by_64_eles.csv", "\n")
    val W_o = loadMatrix("/home/tianzhao/data/64_by_64_eles.csv", "\n")
    val U_o = loadMatrix("/home/tianzhao/data/64_by_64_eles.csv", "\n")
    val W_c = loadMatrix("/home/tianzhao/data/64_by_64_eles.csv", "\n")
    val U_c = loadMatrix("/home/tianzhao/data/64_by_64_eles.csv", "\n")
    val x_t = loadMatrix("/home/tianzhao/data/64_by_32_eles.csv", "\n")
    val h_t_1 = loadMatrix("/home/tianzhao/data/64_by_32_eles.csv", "\n")
    val W_c_t_1 = loadMatrix("/home/tianzhao/data/64_by_32_eles.csv", "\n")

    val gold = Array.tabluate(D_h) { i =>
      val W_i_Row = W_i(i)
      val U_i_Row = U_i(i)
      val W_f_Row = W_f(i)
      val U_f_Row = W_f(i)
      val W_o_Row = W_o(i)
      val U_o_Row = W_o(i)
      val W_c_Row = W_c(i)
      val U_c_Row = W_c(i)
      Array.tabulate(N) { j =>
         
      }
    }
    printArray(gold , "gold = ")
  }
}
