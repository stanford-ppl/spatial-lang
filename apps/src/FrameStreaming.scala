import spatial._
import org.virtualized._
import spatial.targets.DE1

object FrameStreaming extends SpatialApp {
  import IR._

  override val target = DE1

  val Kh = 3
  val Kw = 3
  val Cmax = 512
  val Rmax = 512
  
  @struct case class Pixel24(b: UInt8, g: UInt8, r: UInt8)
  @struct case class Pixel16(b: UInt5, g: UInt6, r: UInt5)

  @virtualize
  def convolveVideoStream(rows: Int, cols: Int): Unit = {
    val R = ArgIn[Int]
    val C = ArgIn[Int]
    setArg(R, rows)
    setArg(C, cols)

    val imgIn  = StreamIn[Pixel24](target.VideoCamera)
    val imgOut = BufferedOut[Pixel16](Rmax, Cmax)(target.VGA)

    Accel {
      val fifoIn = FIFO[Int12](128)

      Stream(*) { _ =>
        Pipe {
          val pixel = imgIn.value()
          val grayPixel = (pixel.b.to[Int12] + pixel.g.to[Int12] + pixel.r.to[Int12]) / 3
          fifoIn.enq(grayPixel)
        }

        Foreach(0 until R, 0 until C) { (r, c) =>
          val pixel = fifoIn.deq()
          imgOut(r, c) = Pixel16(pixel.to[UInt5], pixel.to[UInt6], pixel.to[UInt5])
        }
      }

      ()
    }
  }

  @virtualize
  def main() {
    val R = args(0).to[Int]
    val C = Cmax
    convolveVideoStream(R, C)
  }
}