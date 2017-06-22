import spatial.dsl._
import org.virtualized._

object FIRFilter extends SpatialApp {


  override val target = targets.DE1
  val Nmax = 16

  @virtualize def main(): Unit = {
    val weightArray = loadCSV1D[Int]("weights.csv")

    val N = ArgIn[Int]
    setArg(N, weightArray.length)

    val weights = DRAM[Int](N)
    setMem(weights, weightArray)

    val in = StreamIn[Int](target.GPInput1)
    val out = StreamOut[Int](target.GPOutput1)
    Accel {
      val w = RegFile[Int](Nmax)
      val taps = RegFile[Int](Nmax)

      w load weights(0::N)
      Stream(*) { _ =>
        taps <<= in
        out := Reduce(0)(Nmax by 1 par Nmax){i => w(i) * taps(i) }{_+_}
      }
    }
  }
}

object FIRFilter2 extends SpatialApp {


  override val target = targets.DE1
  val Nmax = 16

  @virtualize def main(): Unit = {
    val file = loadCSV1D[Int]("weights.csv")
    val N = ArgIn[Int]
    setArg(N, file.length)
    val weights = DRAM[Int](N)
    setMem(weights, file)

    val in = StreamIn[Int](target.GPInput1)
    val out = StreamOut[Int](target.GPOutput1)

    Accel {
      val w = RegFile[Int](Nmax)
      val tapOuts = RegFile[Int](Nmax + 1)
      val in_t = Reg[Int]

      w load weights(0::N)

      Stream(*){ _ =>
        in_t := in
        Foreach(Nmax par Nmax){i => tapOuts(i+1) = w(i)*in_t + tapOuts(i)}
        out := tapOuts(N)
      }
    }
  }
}
