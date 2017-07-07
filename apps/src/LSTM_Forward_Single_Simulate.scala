import spatial.dsl._
import org.virtualized._

object LSTM_Gate_Simulate extends SpatialApp {
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

    // TODO: Get a pretrained model and fetch out weights from one of the gates
    val W_i = loadCSV1D[X]("/home/tianzhao/data/64_by_64_eles.csv", "\n")
    val U_i = loadCSV1D[X]("/home/tianzhao/data/64_by_64_eles.csv", "\n")
    val W_f = loadCSV1D[X]("/home/tianzhao/data/64_by_64_eles.csv", "\n")
    val U_f = loadCSV1D[X]("/home/tianzhao/data/64_by_64_eles.csv", "\n")
    val W_o = loadCSV1D[X]("/home/tianzhao/data/64_by_64_eles.csv", "\n")
    val U_o = loadCSV1D[X]("/home/tianzhao/data/64_by_64_eles.csv", "\n")
    val W_c = loadCSV1D[X]("/home/tianzhao/data/64_by_64_eles.csv", "\n")
    val U_c = loadCSV1D[X]("/home/tianzhao/data/64_by_64_eles.csv", "\n")
    val x_t = loadCSV1D[X]("/home/tianzhao/data/64_by_32_eles.csv", "\n")
    val h_t_1 = loadCSV1D[X]("/home/tianzhao/data/64_by_32_eles.csv", "\n")
    val W_c_t_1 = loadCSV1D[X]("/home/tianzhao/data/64_by_32_eles.csv", "\n")


    //Array.tabulate(height) { h =>
    //  Array.tabulate(width)(w => W_i(h * width + w))
    //}

    printArray(W_i , "W_i = ")
  }
}
