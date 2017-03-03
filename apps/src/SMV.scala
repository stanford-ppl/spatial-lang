// import spatial.compiler._
// import spatial.library._
// import spatial.shared._

// // Sparse Matrix Vector multiply
// object SMV extends SpatialAppCompiler with SMVApp // Regression (Sparse) // Args: 768
// trait SMVApp extends SpatialApp {
//   type T = SInt //FixPt[Signed,B16,B16]
//   type Array[T] = ForgeArray[T]

//   val tileSize = 768
//   val innerPar = 8
//   val outerPar = 1
//   val pp = 3840
//   val maximumNNZ = 60
//   val margin = 1

//   def smv(AC: Rep[Array[SInt]], AD: Rep[Array[T]], S: Rep[Array[SInt]], V: Rep[Array[T]],nn: Rep[SInt], NNZ: Rep[SInt]) = {
//     val N = ArgIn[SInt]
//     setArg(N,nn)

//     val aC = DRAM[SInt](pp,maximumNNZ)
//     val aD = DRAM[T](pp,maximumNNZ)
//     val sizes = DRAM[SInt](pp)
//     val v = DRAM[T](pp)
//     val out = DRAM[T](N)

//     val op = outerPar (1 -> 6)
//     val ip = innerPar (1 -> 96)
//     val stPar    = innerPar (1 -> 1)

//     setMem(aC, AC)
//     setMem(aD, AD)
//     setMem(sizes, S)
//     setMem(v, V)

//     Accel {
//       Pipe(N by tileSize){ rowchunk =>
//         val smvresult = SRAM[T](tileSize)
//         val smvtileSizes = SRAM[SInt](tileSize)
//         smvtileSizes := sizes(rowchunk :: rowchunk+tileSize par ip)
//         Pipe(tileSize by 1 par op){row =>
//           val csrCols = SRAM[SInt](tileSize)
//           val csrData = SRAM[T](tileSize)
//           val vecGathered = SRAM[T](tileSize)

//           // Load dense csr piece
//           val len = smvtileSizes(row)
//           val OCROW = (rowchunk+row) // TODO: Issue #47
//           Parallel{
//             csrCols := aC(OCROW, 0 :: len par ip)
//             csrData := aD(OCROW, 0 :: len par ip)
//           }
//           vecGathered := v(csrCols, len)

//           val acc = Reduce(len by 1 par ip)(0.as[T]) { i =>
//             csrData(i) * vecGathered(i)
//           }{_+_}

//           smvresult(row) = acc.value

//         }
//       out(rowchunk::rowchunk+tileSize par stPar) := smvresult
//       }
//     }
//     getMem(out)
//   }

//   def printArr(a: Rep[Array[T]], str: String = "") {
//     println(str)
//     (0 until a.length) foreach { i => print(a(i) + " ") }
//     println("")
//   }

//   def main() = {
//     val N = args(0).to[SInt]
//     val NNZ = maximumNNZ//args(1).to[SInt]
//     val P = pp

//     val AC = Array.tabulate(N){ i => Array.tabulate(NNZ) { j => j * 3}}
//     val AD = Array.tabulate(N){ i => Array.fill(NNZ) {random[T](5) }}
//     val S = Array.tabulate(N){ i => NNZ }
//     val V = Array.tabulate(P){ i => i }

//     val smvresult = smv(AC.flatten, AD.flatten, S, V, N, NNZ)

//     val gold = AC.zip(AD) { (col, data) => col.zip(data) {(c, d) =>
//       d*V(c)
//     }.reduce{_+_}}

//     printArr(gold, "gold: ")
//     printArr(smvresult, "smvresult: ")

//     val cksum = smvresult.zip(gold){(a,b) => a - margin < b && a + margin > b}.reduce{_&&_}
//     println("PASS: " + cksum + " (SMV)")

//   }
// }
