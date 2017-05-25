/*import spatial._
import org.virtualized._
import spatial.targets.DE1

object FrameStreaming extends SpatialApp {
  import IR._

  override val target = DE1
  val Cmax = 320
  val Rmax = 240

  @struct case class Pixel16(b: UInt5, g: UInt6, r: UInt5)
  @struct case class Video(pixel: Pixel16, start: Boolean, end: Boolean)

  @virtualize
  def convolveVideoStream(): Unit = {
    val videoIn = StreamIn[Video](target.VideoCamera)
    val imgOut = BufferedOut[Pixel16](target.VGA)

    Accel (*) {
      val video = videoIn.value()

      FSM[Boolean](s => true) { state =>
        if (state == true) {
          Foreach(0 until Rmax, 0 until Cmax) { (r, c) =>
            if (r >= 60 && r < 120 && c >= 60 && c < 120) {
              imgOut(r - 60, c - 60) = Video(video.pixel, r == 0 && c == 0, r == 119 && c == 119)
            }
          }
        }
      }{state => !(state && video.end) || video.start }

    }
  }

  @virtualize
  def main() {
    val R = args(0).to[Int]
    val C = Cmax
    convolveVideoStream()
  }
}*/
