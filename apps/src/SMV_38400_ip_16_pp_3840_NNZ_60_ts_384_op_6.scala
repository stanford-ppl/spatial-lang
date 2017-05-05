import spatial._
import org.virtualized._

object SMV_38400_ip_16_pp_3840_NNZ_60_ts_384_op_6 extends SpatialApp {  // Regression (Sparse) // Args: 768
  import IR._

  type T = Int //FixPt[Signed,B16,B16]

val pp = 3840
val NNZ = 60

val ip = 16
val op = 6


val ts = 384

  val margin = 1

  @virtualize
  def main() = {
    val nn = args(0).to[Int]
    val P = pp

    val AC = Array.tabulate(nn){ i => Array.tabulate(NNZ) { j => (j * 3).to[Int]}}
    val AD = Array.tabulate(nn){ i => Array.fill(NNZ) {random[Int](5) }}
    val S = Array.tabulate(nn){ i => NNZ.to[Int] }
    val V = Array.tabulate(P){ i => i.to[Int] }

    val N = ArgIn[Int]
    setArg(N,nn)

    val aC = DRAM[Int](pp,NNZ)
    val aD = DRAM[Int](pp,NNZ)
    val sizes = DRAM[Int](pp)
    val v = DRAM[Int](pp)
    val out = DRAM[Int](N)

    val stPar    = ip (1 -> 1)

    setMem(aC, AC.flatten)
    setMem(aD, AD.flatten)
    setMem(sizes, S)
    setMem(v, V)

    Accel {
      Foreach(N by ts par op){ rowchunk =>
        val smvresult = SRAM[Int](ts)
        val smvtileSizes = SRAM[Int](ts)
        smvtileSizes load sizes(rowchunk :: rowchunk+ts par ip)
        Foreach(ts by 1){row =>
          val csrCols = SRAM[Int](ts)
          val csrData = SRAM[Int](ts)
          val vecGathered = SRAM[Int](ts)

          // Load dense csr piece
          val len = smvtileSizes(row)
          val OCROW = (rowchunk+row) // TODO: Issue #47
          Parallel{
            csrCols load aC(OCROW, 0 :: len par ip)
            csrData load aD(OCROW, 0 :: len par ip)
          }
          vecGathered gather v(csrCols, len)

          val acc = Reduce(Reg[Int](0.to[Int]))(len by 1 par ip) { i =>
            csrData(i) * vecGathered(i)
          }{_+_}

          smvresult(row) = acc.value
        }
        out(rowchunk::rowchunk+ts par stPar) store smvresult
      }
    }
    val smvresult = getMem(out)



    val gold = AC.zip(AD) { (col, data) => col.zip(data) {(c, d) =>
      d*V(c)
    }.reduce{_+_}}

    printArray(gold, "gold: ")
    printArray(smvresult, "smvresult: ")

    val cksum = smvresult.zip(gold){(a,b) => a - margin < b && a + margin > b}.reduce{_&&_}
    println("PASS: " + cksum + " (SMV)")

  }


}
