import spatial.dsl._
import org.virtualized._

object MathLUT extends SpatialApp {
  import IR._

  type X = FixPt[TRUE,_32,_32]

  @virtualize
  def LUTOut[T:Type:Num] (
    /* Data loaded from csv */
    data: Array[T]
  ) = {
    val totalSize = 128
    val tileSize = 32

    val dataMem = DRAM[T](totalSize)
    val sigmoidResult = DRAM[T](totalSize)
    val tanhResult = DRAM[T](totalSize)

    setMem(dataMem, data)

    Accel {
      /*
       * LUT table tests:
       * Input lower bound: -32.0
       * Input upper bound: 32.0
       * Output lower bound: 0
       * Output upper bound: 1
       * Number of samples: 128
       */

      val sigmoidLUT = LUT.fromFile[T](totalSize)("/home/tianzhao/spatial-LSTM/spatial-lang/apps/src/__128_sigmoidLUT.csv")
      val tanhLUT = LUT.fromFile[T](totalSize)("/home/tianzhao/spatial-LSTM/spatial-lang/apps/src/__128_tanhLUT.csv")

      Foreach(totalSize by tileSize) { k =>
        val tile_dat = SRAM[T](tileSize)
        val tile_sigmoidRe = SRAM[T](tileSize)
        val tile_tanhRe = SRAM[T](tileSize)
        tile_dat load dataMem(k::k+tileSize)
        Foreach(tileSize by 1) { kk =>
          Parallel {
            tile_sigmoidRe(kk) = sigmoidLUT(((tile_dat(kk) + 32.to[T]) * 2.to[T]).to[Int])
            tile_tanhRe(kk) = tanhLUT(((tile_dat(kk) + 32.to[T]) * 2.to[T]).to[Int])
          }
        }

        sigmoidResult(k::k+tileSize) store tile_sigmoidRe
        tanhResult(k::k+tileSize) store tile_tanhRe
      }
    }

    (getMem(sigmoidResult), getMem(tanhResult))
  }

  @virtualize
  def main() = {
    val dataCSV = loadCSV1D[X]("/home/tianzhao/data/8_by_1_eles.csv", "\n")
    val (sigmoidResult, tanhResult) = LUTOut(dataCSV)
    printArray(sigmoidResult, "Sigmoid table yields: ")
    printArray(tanhResult, "Tanh table yields: ")
  }
}
