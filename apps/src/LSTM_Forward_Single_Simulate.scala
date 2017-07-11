import spatial.dsl._
import org.virtualized._

object LSTM_Forward_Single_Simulate extends SpatialApp {
  import IR._

  // TODO: need to conform the result with Spatial sim result
  // TODO: need to load in more accurate data from tensorflow weights
  type X = FixPt[TRUE,_32,_32]
  // type X = Float

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

//    def sigmoid(x: X) : X= {
//      return 1 / (1 + exp(-x))
//    }

    val W_i =     loadCSV2D[X]("/home/tianzhao/data/64_by_64_basic_eles.csv", ",", "\n")
    val U_i =     loadCSV2D[X]("/home/tianzhao/data/64_by_64_basic_eles.csv", ",", "\n")
    val W_f =     loadCSV2D[X]("/home/tianzhao/data/64_by_64_basic_eles.csv", ",", "\n")
    val U_f =     loadCSV2D[X]("/home/tianzhao/data/64_by_64_basic_eles.csv", ",", "\n")
    val W_o =     loadCSV2D[X]("/home/tianzhao/data/64_by_64_basic_eles.csv", ",", "\n")
    val U_o =     loadCSV2D[X]("/home/tianzhao/data/64_by_64_basic_eles.csv", ",", "\n")
    val W_c =     loadCSV2D[X]("/home/tianzhao/data/64_by_64_basic_eles.csv", ",", "\n")
    val U_c =     loadCSV2D[X]("/home/tianzhao/data/64_by_64_basic_eles.csv", ",", "\n")
    val x_t =     loadCSV2D[X]("/home/tianzhao/data/64_by_32_basic_eles.csv", ",", "\n")
    val h_t_1 =   loadCSV2D[X]("/home/tianzhao/data/64_by_32_basic_eles.csv", ",", "\n")
    val W_c_t_1 = loadCSV2D[X]("/home/tianzhao/data/64_by_32_basic_eles.csv", ",", "\n")

    val gold_newMemGate = (0::D_h, 0::N){(i,j) =>
      Array.tabulate(d){ k => W_f(i,k) * x_t(k,j) + U_f(i,k) * h_t_1(k,j) }.reduce{_+_} * W_c_t_1(i,j) +
        Array.tabulate(d){ k => W_i(i,k) * x_t(k,j) + U_i(i,k) * h_t_1(k,j) }.reduce{_+_} *
          Array.tabulate(d) { k => W_c(i,k) * x_t(k,j) + U_c(i,k) * h_t_1(k,j) }.reduce{_+_}
    }

    val gold_newHiddenState = (0::D_h, 0::N){(i,j) =>
      val memState = Array.tabulate(d){ k => W_f(i,k) * x_t(k,j) + U_f(i,k) * h_t_1(k,j) }.reduce{_+_} * W_c_t_1(i,j) +
                      Array.tabulate(d){ k => W_i(i,k) * x_t(k,j) + U_i(i,k) * h_t_1(k,j) }.reduce{_+_} *
                        Array.tabulate(d) { k => W_c(i,k) * x_t(k,j) + U_c(i,k) * h_t_1(k,j) }.reduce{_+_}
      memState * Array.tabulate(d) { k => W_o(i,k) * x_t(k,j) + U_o(i,k) * h_t_1(k,j) }.reduce{_+_}
    }

    printMatrix(gold_newHiddenState, "Gold hiddenState:")
    writeCSV2D[X](gold_newHiddenState, "/home/tianzhao/spatial-LSTM/spatial-lang/apps/results/LSTM_Forward_Single/gold_newHiddenState.csv", ",", "\n")
    printMatrix(gold_newMemGate, "Gold memGate:")
    writeCSV2D[X](gold_newMemGate, "/home/tianzhao/spatial-LSTM/spatial-lang/apps/results/LSTM_Forward_Single/gold_newMemGate.csv", ",", "\n")
  }
}
