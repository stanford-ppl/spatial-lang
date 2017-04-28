import spatial._
import org.virtualized._

object EdgeDetector extends SpatialApp { 
  import IR._
  type T = FixPt[TRUE,_16,_16]

  @virtualize
  def main() {
    type T = FixPt[TRUE,_16,_16]
    val rowtile = 16
    val coltile = 64
    val data = loadCSV2D[T]("/remote/regression/data/slacsample2d.csv", ",", "\n")
    val memrows = ArgIn[Int]
    val memcols = ArgIn[Int]
    setArg(memrows, data.rows.to[Int])
    setArg(memcols, data.cols.to[Int])
    val srcmem = DRAM[T](memrows, memcols)
    setMem(srcmem, data)
    val dstmem = DRAM[Int](memrows)

    val window = 16

    Accel {
      val sr = RegFile[T](1,window)
      val rawdata = SRAM[T](coltile)
      val results = SRAM[Int](rowtile)
      // Work on each row
      Sequential.Foreach(memrows by rowtile) { r => 
        Sequential.Foreach(rowtile by 1) { rr => 
          // Work on each tile of a row
          val globalMax = Reduce(Reg[Tup2[Int,T]](pack(0.to[Int], -1000.to[T])))(memcols by coltile) { c =>
            // Load tile from row
            rawdata load srcmem(r + rr, c::c+coltile)
            // Scan through tile to get deriv
            val localMax = Reduce(Reg[Tup2[Int,T]](pack(0.to[Int], -1000.to[T])))(coltile by 1) { j => 
              sr(1,*) <<= rawdata(j)
              val mean_right = Reduce(Reg[T](0.to[T]))(window/2 by 1) { k => sr(0,k) }{_+_} / window.to[T]
              val mean_left = Reduce(Reg[T](0.to[T]))(window/2 by 1) { k => sr(0,k+window/2) }{_+_} / window.to[T]
              val slope = (mean_right - mean_left) / (window/2).to[T]
              val idx = j + c
              mux(idx < window, pack(idx, 0.to[T]), pack(idx,slope))
            }{(a,b) => mux(a._2 > b._2, a, b)}
            localMax
          }{(a,b) => mux(a._2 > b._2, a, b)}
          results(rr) = globalMax._1
        }
        dstmem(r::r+rowtile) store results
      }
    }


    // Extract results from accelerator
    val results = getMem(dstmem)
    val gold = loadCSV1D[Int]("/remote/regression/data/edge_gold.csv", ",")
    val margin = 2.to[Int]

    // Create validation checks and debug code
    printArray(results, "Results:")

    val cksum = results.zip(gold) {case (a,b) => a < b + margin && a > b - margin}.reduce{_&&_}
    println("PASS: " + cksum + " (EdgeDetector)")
  }
}

object Differentiator extends SpatialApp {
  import IR._
  type T = FixPt[TRUE,_16,_16]

  @virtualize
  def main() {
    type T = FixPt[TRUE,_16,_16]
    val coltile = 64
    val data = loadCSV1D[T]("/remote/regression/data/slacsample1d.csv", ",")
    val memcols = ArgIn[Int]
    setArg(memcols, data.length.to[Int])
    val srcmem = DRAM[T](memcols)
    setMem(srcmem, data)
    val dstmem = DRAM[T](memcols)

    val window = 16

    Accel {
      val sr = RegFile[T](1,window)
      val rawdata = SRAM[T](coltile)
      val results = SRAM[T](coltile)
      // Work on each tile of a row
      Foreach(memcols by coltile) { c =>
        rawdata load srcmem(c::c+coltile)
        // Scan through tile to get deriv
        Foreach(coltile by 1) { j => 
          sr(1,*) <<= rawdata(j)
          val mean_right = Reduce(Reg[T](0.to[T]))(window/2 by 1) { k => sr(0,k) }{_+_} / window.to[T]
          val mean_left = Reduce(Reg[T](0.to[T]))(window/2 by 1) { k => sr(0,k+window/2) }{_+_} / window.to[T]
          val slope = (mean_right - mean_left) / (window/2).to[T]
          val idx = j + c
          results(j) = mux(idx < window, 0.to[T], slope)
        }
        dstmem(c::c+coltile) store results
      }
    }


    // Extract results from accelerator
    val results = getMem(dstmem)

    // // Write answer for first time
    // writeCSV1D(results, "/remote/regression/data/deriv_gold.csv", ",")
    // Read answer
    val gold = loadCSV1D[T]("/remote/regression/data/deriv_gold.csv", ",")

    // Create validation checks and debug code
    printArray(results, "Results:")
    val margin = 0.5.to[T]

    val cksum = gold.zip(results){case (a,b) => abs(a-b) < margin}.reduce{_&&_}
    println("PASS: " + cksum + " (Differentiator)")
  }
}
