import spatial._
import org.virtualized._

object CSV1D extends SpatialApp {

  import IR._

  @virtualize
  def main() {
    type T = FixPt[TRUE, _16, _16]
    val tilesize = 16
    val data = loadCSV1D[T]("/home/mattfel/spatial-lang/spatial/core/resources/cppgen/testdata/1d.csv", ",")
    val memsize = ArgIn[Int]
    setArg(memsize, data.length.to[Int])
    val srcmem = DRAM[T](memsize)
    setMem(srcmem, data)
    val result = ArgOut[T]

    Accel {
      val fpgamem = SRAM[T](tilesize)
      result := Reduce(Reg[T](0.to[T]))(memsize.value by tilesize) { r =>
        fpgamem load srcmem(r :: r + tilesize)
        Reduce(Reg[T](0.to[T]))(tilesize by 1) { i =>
          fpgamem(i)
        } {
          _ + _
        }
      } {
        _ + _
      }
    }


    val r = getArg(result)

    val gold = data.reduce {
      _ + _
    }

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
    type T = FixPt[TRUE, _16, _16]
    val tilesize = 16
    val data = loadCSV1D[T]("/home/mattfel/spatial-lang/spatial/core/resources/cppgen/testdata/1d.ssv", " ")
    val memsize = ArgIn[Int]
    setArg(memsize, data.length.to[Int])
    val srcmem = DRAM[T](memsize)
    setMem(srcmem, data)
    val result = ArgOut[T]

    Accel {
      val fpgamem = SRAM[T](tilesize)
      result := Reduce(Reg[T](0.to[T]))(memsize.value by tilesize) { r =>
        fpgamem load srcmem(r :: r + tilesize)
        Reduce(Reg[T](0.to[T]))(tilesize by 1) { i =>
          fpgamem(i)
        } {
          _ + _
        }
      } {
        _ + _
      }
    }


    val r = getArg(result)

    val gold = data.reduce {
      _ + _
    }

    printArray(data)
    println("Gold sum is " + gold)
    println("Accel sum is " + r)
    val cksum = gold === r
    println("PASS: " + cksum + " (CSV1D)")
  }
}

object SSV2D extends SpatialApp {

  import IR._

  @virtualize
  def main() {
    type T = FixPt[TRUE, _16, _16]
    val rowtile = 2
    val coltile = 16
    val data = loadCSV2D[T]("/home/mattfel/spatial-lang/spatial/core/resources/cppgen/testdata/2d.ssv", " ", "\n")
    val memrows = ArgIn[Int]
    val memcols = ArgIn[Int]
    setArg(memrows, data.rows.to[Int])
    setArg(memcols, data.cols.to[Int])
    val srcmem = DRAM[T](memrows, memcols)
    setMem(srcmem, data)
    val result = ArgOut[T]

    println(data.rows + " x " + data.cols + " matrix:")
    printMatrix(data)

    Accel {
      val fpgamem = SRAM[T](rowtile, coltile)

      result := Reduce(Reg[T](0.to[T]))(memrows.value by rowtile, memcols.value by coltile) { (r, c) =>
        fpgamem load srcmem(r :: r + rowtile, c :: c + coltile)
        Reduce(Reg[T](0.to[T]))(rowtile by 1, coltile by 1) { (i, j) =>
          fpgamem(i, j)
        } {
          _ + _
        }
      } {
        _ + _
      }

    }


    val r = getArg(result)

    val gold = data.reduce {
      _ + _
    }

    println("Gold sum is " + gold)
    println("Accel sum is " + r)
    val cksum = gold === r && gold > 0.to[T]
    println("PASS: " + cksum + " (SSV2D)")
  }
}
