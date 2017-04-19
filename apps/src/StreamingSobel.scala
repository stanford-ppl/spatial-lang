import spatial._
import org.virtualized._
import spatial.targets.DE1

object StreamingSobel extends SpatialApp { 
  import IR._

  override val target = DE1

  val Kh = 3
  val Kw = 3
  val Cmax = 512

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

      // TODO: Better syntax for initialization of lookup tables
      Pipe {
        Pipe{kh(0,0) = 1 }
        Pipe{kh(1,0) = 2 }
        Pipe{kh(2,0) = 1 }
        Pipe{kh(0,1) = 0 }
        Pipe{kh(1,1) = 0 }
        Pipe{kh(2,1) = 0 }
        Pipe{kh(0,2) = -1 }
        Pipe{kh(1,2) = -2 }
        Pipe{kh(2,2) = -1 }

        Pipe{kv(0,0) = 1 }
        Pipe{kv(0,1) = 2 }
        Pipe{kv(0,2) = 1 }
        Pipe{kv(1,0) = 0 }
        Pipe{kv(1,1) = 0 }
        Pipe{kv(1,2) = 0 }
        Pipe{kv(2,0) = -1 }
        Pipe{kv(2,1) = -2 }
        Pipe{kv(2,2) = -1 }
      }

      val sr = RegFile[Int12](Kh, Kw)
      val fifoIn = FIFO[Int12](128)
      val fifoOut = FIFO[Int12](128)
      val lb = LineBuffer[Int12](Kh, Cmax)

      Stream(*) { _ =>
        val pixel = imgIn.value()
        val grayPixel = (pixel.b.to[Int12] + pixel.g.to[Int12] + pixel.r.to[Int12]) / 3
        // println(pixel + " -> " + grayPixel)
        fifoIn.enq( grayPixel )

        Foreach(0 until R, 0 until Cmax) { (r, c) =>
          lb.enq(fifoIn.deq(), true)

          Foreach(0 until Kh par Kh) { i => sr(i, *) <<= lb(i, c) }

          val horz = Reduce(Reg[Int12])(Kh by 1, Kw by 1) { (i, j) =>
            val number = mux(r < 2 || c < 2, 0.to[Int12], sr(i, j))
            number * kh(i, j)
          }{_+_}

          val vert = Reduce(Reg[Int12])(Kh by 1, Kw by 1) { (i, j) =>
            val number = mux(r < 2 || c < 2, 0.to[Int12], sr(i, j))
            number * kv(i, j)
          }{_+_}

          fifoOut.enq( abs(horz.value) + abs(vert.value) ) // Technically should be sqrt(horz**2 + vert**2)
        }

        val pixelOut = fifoOut.deq()
        imgOut := Pixel16(pixelOut.to[UInt5], pixelOut.to[UInt6], pixelOut.to[UInt5])
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