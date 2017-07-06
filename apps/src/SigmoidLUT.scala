import spatial._
import org.virtualized._

object SigmoidLUT extends SpatialApp {
  import IR._
  type X = FixPt[TRUE,_32,_32]

  @virtualize
  def SigmoidOut[T:Type:Num] (
    /* Data loaded from csv */   
    data: Array[T]
  ) = {
    val totalSize = 8
    val tileSize = 2

    val dataMem = DRAM[T](totalSize) 
    val result = DRAM[T](totalSize)

    setMem(dataMem, data)

    Accel {
      /*
       * LUT table for sigmoid function:
       * Input lower bound: -32.0
       * Input upper bound: 32.0
       * Output lower bound: 0
       * Output upper bound: 1
       * Number of samples: 128
       */
      val sigmoidLUT = LUT[T](totalSize)(
        // 1.0000000000.to[T], 1.0000000000.to[T], 1.0000000000.to[T], 1.0000000000.to[T], 1.0000000000.to[T], 1.0000000000.to[T], 1.0000000000.to[T], 1.0000000000.to[T],
        // 1.0000000000.to[T], 1.0000000000.to[T], 1.0000000000.to[T], 1.0000000000.to[T], 1.0000000000.to[T], 1.0000000000.to[T], 1.0000000000.to[T], 1.0000000000.to[T],
        // 1.0000000000.to[T], 0.9999999999.to[T], 0.9999999999.to[T], 0.9999999998.to[T], 0.9999999997.to[T], 0.9999999995.to[T], 0.9999999992.to[T], 0.9999999987.to[T],
        // 0.9999999979.to[T], 0.9999999966.to[T], 0.9999999944.to[T], 0.9999999908.to[T], 0.9999999848.to[T], 0.9999999749.to[T], 0.9999999586.to[T], 0.9999999317.to[T],
        0.9999998875.to[T], 0.9999998145.to[T], 0.9999996941.to[T], 0.9999994957.to[T], 0.9999991685.to[T], 0.9999986290.to[T], 0.9999977397.to[T], 0.9999962734.to[T])
        // 0.9999938558.to[T], 0.9999898700.to[T], 0.9999832986.to[T], 0.9999724643.to[T], 0.9999546021.to[T], 0.9999251538.to[T], 0.9998766054.to[T], 0.9997965730.to[T],
        // 0.9996646499.to[T], 0.9994472214.to[T], 0.9990889488.to[T], 0.9984988177.to[T], 0.9975273768.to[T], 0.9959298623.to[T], 0.9933071491.to[T], 0.9890130574.to[T],
        // 0.9820137900.to[T], 0.9706877692.to[T], 0.9525741268.to[T], 0.9241418200.to[T], 0.8807970780.to[T], 0.8175744762.to[T], 0.7310585786.to[T], 0.6224593312.to[T],
        // 0.5000000000.to[T], 0.3775406688.to[T], 0.2689414214.to[T], 0.1824255238.to[T], 0.1192029220.to[T], 0.0758581800.to[T], 0.0474258732.to[T], 0.0293122308.to[T],
        // 0.0179862100.to[T], 0.0109869426.to[T], 0.0066928509.to[T], 0.0040701377.to[T], 0.0024726232.to[T], 0.0015011823.to[T], 0.0009110512.to[T], 0.0005527786.to[T],
        // 0.0003353501.to[T], 0.0002034270.to[T], 0.0001233946.to[T], 0.0000748462.to[T], 0.0000453979.to[T], 0.0000275357.to[T], 0.0000167014.to[T], 0.0000101300.to[T],
        // 0.0000061442.to[T], 0.0000037266.to[T], 0.0000022603.to[T], 0.0000013710.to[T], 0.0000008315.to[T], 0.0000005043.to[T], 0.0000003059.to[T], 0.0000001855.to[T],
        // 0.0000001125.to[T], 0.0000000683.to[T], 0.0000000414.to[T], 0.0000000251.to[T], 0.0000000152.to[T], 0.0000000092.to[T], 0.0000000056.to[T], 0.0000000034.to[T],
        // 0.0000000021.to[T], 0.0000000013.to[T], 0.0000000008.to[T], 0.0000000005.to[T], 0.0000000003.to[T], 0.0000000002.to[T], 0.0000000001.to[T], 0.0000000001.to[T],
        // 0.0000000000.to[T], 0.0000000000.to[T], 0.0000000000.to[T], 0.0000000000.to[T], 0.0000000000.to[T], 0.0000000000.to[T], 0.0000000000.to[T], 0.0000000000.to[T],
        // 0.0000000000.to[T], 0.0000000000.to[T], 0.0000000000.to[T], 0.0000000000.to[T], 0.0000000000.to[T], 0.0000000000.to[T], 0.0000000000.to[T], 0.0000000000.to[T])

      Foreach(totalSize by tileSize) { k =>
        val tile_dat = SRAM[T](32)         
        val tile_re = SRAM[T](32)
        tile_dat load dataMem(k::k+tileSize) 
        Foreach(tileSize by 1) { kk =>
          // TODO: shifting? 
          tile_re(kk) = sigmoidLUT(((tile_dat(kk) + 32.to[T]) * 2.to[T]).to[Int])
        }

        result(k::tileSize) store tile_re
      }
    }

    getMem(result)
  }

  @virtualize
  def main() = {
    val dataCSV = loadCSV1D[X]("/home/tianzhao/data/8_by_1_eles.csv", "\n") 
    val sigmoidResult = SigmoidOut(dataCSV)
    printArray(sigmoidResult, "Sigmoid table yields: ")
  }  
}
