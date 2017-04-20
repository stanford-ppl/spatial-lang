import spatial._
import org.virtualized._

object FIRFilter extends SpatialApp {
  import IR._

  override val target = targets.DE1
  val Nmax = 16

  @virtualize def main(): Unit = {
    val weightArray = loadCSV1D[Int]("weights.csv")

    val N = ArgIn[Int]
    setArg(N, weightArray.length)

    val weights = DRAM[Int](N)
    setMem(weights, weightArray)

    val in = StreamIn[Int](target.GPInput)
    val out = StreamOut[Int](target.GPOutput)
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