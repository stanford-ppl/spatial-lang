import spatial._
import org.virtualized._

object Load1Dcsv extends SpatialApp { 
  import IR._

  @virtualize
  def main() {
    val len = 64
    // val data = loadCSV("/home/mattfel/testdata.csv", len)
    val data = Array.fill(len) { 1.to[Int] }
    val srcmem = DRAM[Int](len)
    setMem(srcmem, data)
    val result = ArgOut[Int]

    Accel{
      val fpgamem = SRAM[Int](len)
      fpgamem load srcmem
      val accum = Reduce(Reg[Int](0.to[Int]))(len by 1) { i => 
        fpgamem(i)
      } { _ + _ }
      result := accum
    }

    val r = getArg(result)

    val gold = data.reduce{_+_}

    val cksum = gold === r
    println("PASS: " + cksum + " (Load1Dcsv)")
  }
}
