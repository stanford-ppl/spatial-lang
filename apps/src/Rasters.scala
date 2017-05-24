import spatial._
import org.virtualized._
import spatial.targets.DE1

object MovingBox extends SpatialApp {
  import IR._

  override val target = DE1
  val Cmax = 320
  val Rmax = 240


  @struct case class Pixel16(b: UInt5, g: UInt6, r: UInt5)

  @virtualize
  def convolveVideoStream(): Unit = {
    val imgOut = BufferedOut[Pixel16](target.VGA)
    val dwell = ArgIn[Int]
    val d = args(0).to[Int]
    setArg(dwell, d)

    Accel (*) {
      Foreach(0 until 4) { i => 
        Foreach(0 until dwell) { _ =>
          Foreach(0 until Rmax, 0 until Cmax){ (r, c) =>
            val pixel1 = mux(r > 60 && r < 120 && c > 60 && c < 120, Pixel16(0,63,0), Pixel16(0,0,0))
            val pixel2 = mux(r > 120 && r < 180 && c > 60 && c < 120, Pixel16(31,0,0), Pixel16(0,0,0))
            val pixel3 = mux(r > 120 && r < 180 && c > 120 && c < 180, Pixel16(0,0,31), Pixel16(0,0,0))
            val pixel4 = mux(r > 60 && r < 120 && c > 120 && c < 180, Pixel16(31,0,0), Pixel16(0,0,0))
            val pixel = mux(i == 0, pixel1, mux( i == 1, pixel2, mux( i == 2, pixel3, mux(i == 3, pixel4, Pixel16(0,0,0)))))
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

object LinebufRaster extends SpatialApp { // try arg = 100
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
    val dumbdelay = 20 // extra delay so fill and drain take different number of cycles

    Accel (*) {
      val lb = LineBuffer[Pixel16](1,Cmax)
      Foreach(0 until Rmax) { i => 
        Foreach(0 until dwell) { _ => 
          Foreach(0 until Rmax){ r =>
            Foreach(0 until Cmax) { c => 
                val bluepixel = mux(abs(r - i) < 4, Pixel16(31.to[UInt5],0,0), Pixel16(0,0,0))
                val greenpixel = mux(abs(r - i) < 4, Pixel16(0,31.to[UInt6],0), Pixel16(0,0,0))
                val redpixel = mux(abs(r - i) < 4, Pixel16(0,0,31.to[UInt5]), Pixel16(0,0,0))
                val pixel = mux(c < Cmax/3, bluepixel, mux(c < Cmax*2/3, greenpixel, redpixel))
                lb.enq(pixel)
            }
            Foreach(0 until Cmax) { c => 
              imgOut(r, c) = lb(0, c)
            }
            
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


object ColorSelect extends SpatialApp { // try arg = 100
  import IR._

  override val target = DE1
  val Cmax = 320
  val Rmax = 240

  type UINT3 = FixPt[FALSE,_3,_0]
  type UINT4 = FixPt[FALSE,_4,_0]

  @struct class sw3(b1: UINT3, b2: UINT4, b3: UINT3)
  @struct case class Pixel16(b: UInt5, g: UInt6, r: UInt5)

  @virtualize
  def convolveVideoStream(): Unit = {
    val dwell = ArgIn[Int]
    val d = args(0).to[Int]
    setArg(dwell, d)
    val switch = target.SliderSwitch
    val swInput = StreamIn[sw3](switch)

    val imgOut = BufferedOut[Pixel16](target.VGA)

    Accel (*) {
      Foreach(0 until Rmax) { i => 
        Foreach(0 until dwell) { _ => 
          Foreach(0 until Rmax, 0 until Cmax){ (r, c) =>
            val swBits = swInput.value()
            val bluepixel = mux(r == i, Pixel16(31.to[UInt5],0,0), Pixel16(0,0,0))
            val greenpixel = mux(r == i, Pixel16(0,63.to[UInt6],0), Pixel16(0,0,0))
            val redpixel = mux(r == i, Pixel16(0,0,31.to[UInt5]), Pixel16(0,0,0))
            val pixel = mux(swBits.b1 == 0.to[UINT3], bluepixel, mux(swBits.b1 == 1.to[UINT3], greenpixel, redpixel))
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
