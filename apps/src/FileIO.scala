import spatial.dsl._
import org.virtualized._

object CSV1D extends SpatialApp { 



  @virtualize
  def main() {
    type T = FixPt[TRUE, _16, _16]
    val tilesize = 16
    val data = loadCSV1D[T]("/remote/regression/data/1d.csv", ",")
    val memsize = ArgIn[Int]
    setArg(memsize, data.length.to[Int])
    val srcmem = DRAM[T](memsize)
    setMem(srcmem, data)
    val result = ArgOut[T]

    Accel {
      val fpgamem = SRAM[T](tilesize)
      result := Reduce(Reg[T](0.to[T]))(memsize.value by tilesize){ r =>
        fpgamem load srcmem(r :: r + tilesize)
        Reduce(Reg[T](0.to[T]))(tilesize by 1) { i =>
          fpgamem(i)
        }{_+_}
      }{_+_}
    }


    val r = getArg(result)

    val gold = data.reduce {_+_}

    writeCSV1D[T](data, "/remote/regression/data/1d_store.csv", ",")
    printArray(data)
    println("Gold sum is " + gold)
    println("Accel sum is " + r)
    val cksum = gold === r
    println("PASS: " + cksum + " (CSV1D)")
  }
}

object CSV2D extends SpatialApp { 



  @virtualize
  def main() {
    type T = FixPt[TRUE, _16, _16]
    val rowtile = 2
    val coltile = 16
    val data = loadCSV2D[T]("/remote/regression/data/2d.csv", ",", "\n")
    writeCSV2D[T](data, "/remote/regression/data/2d_store.csv", ",", "\n")
    val memrows = ArgIn[Int]
    val memcols = ArgIn[Int]
    setArg(memrows, data.rows.to[Int])
    setArg(memcols, data.cols.to[Int])
    val srcmem = DRAM[T](memrows, memcols)
    setMem(srcmem, data)
    val result = ArgOut[T]

    println(data.rows + " x " + data.cols + " matrix:")
    printMatrix(data)
    val slice0 = Array.tabulate(memcols){ i => data.apply(0,i)}
    val slice1 = Array.tabulate(memcols){ i => data.apply(1,i)}
    printArray(slice0, "Slice 0")
    printArray(slice1, "Slice 1")

    Accel {
      val fpgamem = SRAM[T](rowtile, coltile)

      result := Reduce(Reg[T](0.to[T]))(memrows.value by rowtile, memcols.value by coltile) { (r, c) =>
        fpgamem load srcmem(r :: r + rowtile, c :: c + coltile)
        Reduce(Reg[T](0.to[T]))(rowtile by 1, coltile by 1) { (i, j) =>
          fpgamem(i, j)
        }{_+_}
      }{_+_}
    }


    val r = getArg(result)

    val gold = data.reduce {_+_}

    println("Gold sum is " + gold)
    println("Accel sum is " + r)
    val cksum = gold === r && gold > 0.to[T]
    println("PASS: " + cksum + " (CSV2D)")
  }
}

object SSV1D extends SpatialApp { // Regression (Unit) // Args: none



  @virtualize
  def main() {
    type T = FixPt[TRUE, _16, _16]
    val tilesize = 16
    val data = loadCSV1D[T]("/remote/regression/data/1d.ssv", " ")
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
        }{_+_}
      }{_+_}
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

object SSV2D extends SpatialApp { // Regression (Unit) // Args: none



  @virtualize
  def main() {
    type T = FixPt[TRUE, _16, _16]
    val rowtile = 2
    val coltile = 16
    val data = loadCSV2D[T]("/remote/regression/data/2d.ssv", " ", "\n")
    val memrows = ArgIn[Int]
    val memcols = ArgIn[Int]
    setArg(memrows, data.rows.to[Int])
    setArg(memcols, data.cols.to[Int])
    val srcmem = DRAM[T](memrows, memcols)
    setMem(srcmem, data)
    val result = ArgOut[T]

    println(data.rows + " x " + data.cols + " matrix:")
    printMatrix(data)
    val slice0 = Array.tabulate(memcols){ i => data.apply(0,i)}
    val slice1 = Array.tabulate(memcols){ i => data.apply(1,i)}
    printArray(slice0, "Slice 0")
    printArray(slice1, "Slice 1")

    Accel {
      val fpgamem = SRAM[T](rowtile, coltile)

      result := Reduce(Reg[T](0.to[T]))(memrows.value by rowtile, memcols.value by coltile) { (r, c) =>
        fpgamem load srcmem(r :: r + rowtile, c :: c + coltile)
        Reduce(Reg[T](0.to[T]))(rowtile by 1, coltile by 1) { (i, j) =>
          fpgamem(i, j)
        }{_+_}
      }{_+_}
    }


    val r = getArg(result)

    val gold = data.reduce {_+_}

    println("Gold sum is " + gold)
    println("Accel sum is " + r)
    val cksum = gold === r && gold > 0.to[T]
    println("PASS: " + cksum + " (SSV2D)")
  }
}
