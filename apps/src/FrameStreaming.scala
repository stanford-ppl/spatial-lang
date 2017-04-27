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

  type Int12 = FixPt[TRUE,_12,_0]
  type UInt8 = FixPt[FALSE,_8,_0]
  type UInt5 = FixPt[FALSE,_5,_0]
  type UInt6 = FixPt[FALSE,_6,_0]
  @struct case class Pixel24(b: UInt8, g: UInt8, r: UInt8)
  @struct case class Pixel16(b: UInt5, g: UInt6, r: UInt5)

  @virtualize
  def convolveVideoStream(rows: Int, cols: Int): Unit = {
    val R = ArgIn[Int]
    val C = ArgIn[Int]
    setArg(R, rows)
    setArg(C, cols)

    val imgIn  = StreamIn[Pixel24](target.VideoCamera)
    val imgOut = StreamOut[Pixel16](target.VGA)

    Accel {
      val kh = RegFile[Int12](Kh, Kw)
      val kv = RegFile[Int12](Kh, Kw)
      val sr = RegFile[Int12](Kh, Kw)

      val pixelIn = FIFO[Int12](128)
      val frameIn = SRAM[Int12](Rmax, Cmax)
      val frameOut = SRAM[Int12](Rmax, Cmax)
      val frameInReady = FIFO[Boolean](128)
      val frameOutReady = FIFO[Boolean](128)

      Stream(*) { _ =>
        val pixel = imgIn.value()
        val grayPixel = (pixel.b.to[Int12] + pixel.g.to[Int12] + pixel.r.to[Int12]) / 3
        pixelIn.enq( grayPixel )

        Foreach(0 until R, 0 until C){ (r, c) =>
          frameIn(r, c) = pixelIn.deq()
          frameInReady.enq(true, r == R-1 && c == C-1)
        }

        Pipe {
          frameInReady.deq()
          Foreach(0 until R, 0 until C) { (r, c) =>
            frameOut(r, c) = frameIn(r, c)
          }
          frameOutReady.enq(true)
        }

        Pipe {
          frameOutReady.deq()
          Foreach(0 until R, 0 until C){ (r, c) =>
            val pixel = frameOut(r, c)
            imgOut := Pixel16(pixel.to[UInt5], pixel.to[UInt6], pixel.to[UInt5])
          }
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