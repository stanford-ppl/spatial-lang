import spatial._
import org.virtualized._

object CSV1D extends SpatialApp { 
  import IR._

  @virtualize
  def main() {
    val len = 64
    type T = FixPt[TRUE,_16,_16]
    val data = loadCSV1D[T]("/home/mattfel/1d.csv", ",")
    // val len = data.length
    val srcmem = DRAM[T](len)
    setMem(srcmem, data)
    val result = ArgOut[T]

    Accel{
      val fpgamem = SRAM[T](len)
      fpgamem load srcmem
      val accum = Reduce(Reg[T](0.to[T]))(len by 1) { i => 
        fpgamem(i)
      } { _ + _ }
      result := accum
    }


    val r = getArg(result)

    val gold = data.reduce{_+_}

    printArray(data)
    println("Gold sum is " + gold)
    println("Accel sum is " + r)
    val cksum = gold === r
    println("PASS: " + cksum + " (CSV1D)")
  }
}

object SSV1D extends SpatialApp { 
  import IR._

  @virtualize
  def main() {
    val len = 64
    type T = FixPt[TRUE,_16,_16]
    val data = loadCSV1D[T]("/home/mattfel/1d.ssv", " ")
    // val len = data.length
    val srcmem = DRAM[T](len)
    setMem(srcmem, data)
    val result = ArgOut[T]

    Accel{
      val fpgamem = SRAM[T](len)
      fpgamem load srcmem
      val accum = Reduce(Reg[T](0.to[T]))(len by 1) { i => 
        fpgamem(i)
      } { _ + _ }
      result := accum
    }


    val r = getArg(result)

    val gold = data.reduce{_+_}

    printArray(data)
    println("Gold sum is " + gold)
    println("Accel sum is " + r)
    val cksum = gold === r
    println("PASS: " + cksum + " (SSV1D)")
  }
}

object SSV2D extends SpatialApp { 
  import IR._

  @virtualize
  def main() {
    val cols = 32
    val rows = 4
    type T = FixPt[TRUE,_16,_16]
    val data = loadCSV2D[T]("/home/mattfel/2d.ssv", " ", "\n")
    // val len = data.length
    val srcmem = DRAM[T](rows,cols)
    setMem(srcmem, data)
    val result = ArgOut[T]
    printMatrix(data)

    Accel{
      val fpgamem = SRAM[T](rows, cols)
      fpgamem load srcmem
      val accum = Reduce(Reg[T](0.to[T]))(rows by 1, cols by 1) { (i,j) => 
        fpgamem(i,j)
      } { _ + _ }
      result := accum
    }


    val r = getArg(result)

    val gold = data.reduce{_+_}

    println("Gold sum is " + gold)
    println("Accel sum is " + r)
    val cksum = gold === r
    println("PASS: " + cksum + " (SSV2D)")
  }
}
