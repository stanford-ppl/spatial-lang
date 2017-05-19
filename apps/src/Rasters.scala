import spatial._
import org.virtualized._
import spatial.targets.DE1

object MovingBox extends SpatialApp {
  import IR._

  override val target = DE1
  val Cmax = 320
  val Rmax = 240

  val dwell = 1000

  @struct case class Pixel16(b: UInt5, g: UInt6, r: UInt5)

  @virtualize
  def convolveVideoStream(): Unit = {
    val imgOut = BufferedOut[Pixel16](target.VGA)

    Accel (*) {
      Foreach(0 until dwell*4) { i => 
        Foreach(0 until Rmax, 0 until Cmax){ (r, c) =>
          val pixel1 = mux(r > 60 && r < 120 && c > 60 && c < 120, Pixel16(0,63,0), Pixel16(0,0,0))
          val pixel2 = mux(r > 120 && r < 180 && c > 60 && c < 120, Pixel16(31,0,0), Pixel16(0,0,0))
          val pixel3 = mux(r > 120 && r < 180 && c > 120 && c < 180, Pixel16(0,0,31), Pixel16(0,0,0))
          val pixel4 = mux(r > 60 && r < 120 && c > 120 && c < 180, Pixel16(31,0,0), Pixel16(0,0,0))
          val pixel = mux(i < dwell, pixel1, mux( i < 2*dwell, pixel2, mux( i < 3*dwell, pixel3, mux(i < 4*dwell, pixel4, Pixel16(0,0,0)))))
          imgOut(r, c) = pixel
        }
      }
    }
  }

  @virtualize
  def main() {
    val R = Rmax
    val C = Cmax
    convolveVideoStream()
  }
}

object ColoredLines extends SpatialApp { // try arg = 100
  import IR._

  override val target = DE1
  val Cmax = 320
  val Rmax = 240



  @struct case class Pixel16(b: UInt5, g: UInt6, r: UInt5)

  @virtualize
  def convolveVideoStream(): Unit = {
    val dwell = ArgIn[Int]
    val d = args(0).to[Int]
    setArg(dwell, d)
    val imgOut = BufferedOut[Pixel16](target.VGA)

    Accel (*) {
      Foreach(0 until Rmax) { i => 
        Foreach(0 until dwell) { _ => 
          Foreach(0 until Rmax, 0 until Cmax){ (r, c) =>
            val bluepixel = mux(r == i, Pixel16((r%32).as[UInt5],0,0), Pixel16(0,0,0))
            val greenpixel = mux(r == i, Pixel16(0,(r%32).as[UInt6],0), Pixel16(0,0,0))
            val redpixel = mux(r == i, Pixel16(0,0,(r%32).as[UInt5]), Pixel16(0,0,0))
            val pixel = mux(r < (Rmax/3), bluepixel, mux(r < (2*Rmax/3), greenpixel, redpixel))
            imgOut(r, c) = pixel
          }
        }
      }
    }
  }

  @virtualize
  def main() {
    val R = Rmax
    val C = Cmax
    convolveVideoStream()
  }
}
