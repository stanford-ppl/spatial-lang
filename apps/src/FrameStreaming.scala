import spatial._
import org.virtualized._
import spatial.targets.DE1

object FrameStreaming extends SpatialApp {
  import IR._

  override val target = DE1
  val Cmax = 240
  val Rmax = 320

  @struct case class Pixel16(b: UInt5, g: UInt6, r: UInt5)

  @virtualize
  def convolveVideoStream(): Unit = {
    val imgOut = BufferedOut[Pixel16](target.VGA)

    Accel (*) {
      Foreach(0 until Rmax, 0 until Cmax){ (r, c) =>
        val pixel = mux(r > 60 && r < 120 && c > 60 && c < 120, Pixel16(0,63,0), Pixel16(0,0,0))
        imgOut(r, c) = pixel
      }
    }
  }

  @virtualize
  def main() {
    val R = args(0).to[Int]
    val C = Cmax
    convolveVideoStream()
  }
}
