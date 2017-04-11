// import spatial._
// import org.virtualized._

// object EdgeDetector extends SpatialApp { 
//   import IR._
//   type T = FixPt[TRUE,_16,_16]

//   @virtualize
//   def main() {
//     type T = FixPt[TRUE,_16,_16]
//     val rowtile = 16
//     val coltile = 64
//     val data = loadCSV2D[T]("/home/mattfel/spatial-lang/spatial/core/resources/cppgen/testdata/slacsample2.ssv", " ", "\n")
//     val memrows = ArgIn[Int]
//     val memcols = ArgIn[Int]
//     setArg(memrows, data.rows.to[Int])
//     setArg(memcols, data.cols.to[Int])
//     val srcmem = DRAM[T](memrows, memcols)
//     setMem(srcmem, data)
//     val dstmem = DRAM[T](memrows)

//     val window = 8

//     Accel {
//       val sr = RegFile[T](window)
//       val rawdata = SRAM[T](coltile)
//       val results = SRAM[T](rowtile)
//       // Work on each row
//       Sequential.Foreach(memrows by rowtile) { r => 
//         Sequential.Foreach(rowtile by 1) { rr => 
//         // Work on each tile of a row
//         val globalMax = Reduce(Reg[Tup2[Int,T]](pack(0.to[Int], 10000.to[T])))(memcols by coltile) { c =>
//           // Load tile from row
//           rawdata load srcmem(r + rr, c::c+coltile)
//           // Scan through tile to get deriv
//           val localMax = Reduce(Reg[Tup2[Int,T]](pack(0.to[Int], 10000.to[T])))(coltile by 1) { j => 
//             sr(*) <<= rawdata(j)
//             val mean_left = Reduce(Reg[T](0.to[T]))(window/2 by 1) { k => sr(k) }{_+_} / window.to[T]
//             val mean_right = Reduce(Reg[T](0.to[T]))(window/2 by 1) { k => sr(k+window/2) }{_+_} / window.to[T]
//             val slope = (mean_right - mean_left) / (window/2)
//             val idx = j + c
//             mux(idx < window, pack(idx, 0.to[T]), pack(idx,slope))
//           }{(a,b) => mux(a._2 > b._2, a, b)}
//           localMax
//         }{(a,b) => mux(a._2 > b._2, a, b)}
//         results(r+rr) = rawdata._1
//       }
//       dstmem(r::r+rowtile) store results
//     }


//     // Extract results from accelerator
//     val results = getMem(dstmem)

//     // Create validation checks and debug code
//     printArray(results, "Results:")

//     // val cksum = gold == result
//     // println("PASS: " + cksum + " (FixPtInOutArg)")
//   }
// }
