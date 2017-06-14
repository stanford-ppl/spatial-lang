import spatial.dsl._
import org.virtualized._
import spatial.targets.DE1

object BOStreamingSobel extends SpatialApp { 


  override val target = DE1

  val Kh = 3
  val Kw = 3
  val Rmax = 240
  val Cmax = 320

  type Int16 = FixPt[TRUE,_16,_0]
  type UInt8 = FixPt[FALSE,_8,_0]
  type UInt5 = FixPt[FALSE,_5,_0]
  type UInt6 = FixPt[FALSE,_6,_0]
//  @struct case class Pixel24(b: UInt8, g: UInt8, r: UInt8)
  @struct case class Pixel16(b: UInt5, g: UInt6, r: UInt5)

  @virtualize
  def convolveVideoStream(rows: Int, cols: Int): Unit = {
    val R = ArgIn[Int]
    val C = ArgIn[Int]
    setArg(R, rows)
    setArg(C, cols)

    val imgIn  = StreamIn[Pixel16](target.VideoCamera)
    val imgOut = BufferedOut[Pixel16](target.VGA)

    Accel {
      val kh = RegFile[Int16](Kh, Kw)
      val kv = RegFile[Int16](Kh, Kw)

      Pipe {
        kh(0, 0) = 1
        kh(1, 0) = 2
        kh(2, 0) = 1
        kh(0, 1) = 0
        kh(1, 1) = 0
        kh(2, 1) = 0
        kh(0, 2) = -1
        kh(1, 2) = -2
        kh(2, 2) = -1
        kv(0, 0) = 1
        kv(0, 1) = 2
        kv(0, 2) = 1
        kv(1, 0) = 0
        kv(1, 1) = 0
        kv(1, 2) = 0
        kv(2, 0) = -1
        kv(2, 1) = -2
        kv(2, 2) = -1
      }

      val sr = RegFile[Int16](Kh, Kw)
      val fifoIn = FIFO[Int16](128)
      val lb = LineBuffer[Int16](Kh, Cmax)

      Stream(*) { _ =>
        val pixel = imgIn.value()
        val grayPixel = (pixel.b.to[Int16]*2 + pixel.g.to[Int16] + pixel.r.to[Int16]*2) / 3
        fifoIn.enq( grayPixel )

        Foreach(0 until R) { r =>

          Pipe {
            Foreach(0 until Cmax){ c =>
              val pixel = fifoIn.deq()
              lb.enq(pixel, true)
            }
          }

          Pipe {
            Foreach(0 until Cmax) { c =>
              Foreach(0 until Kh par Kh) { i => sr(i, *) <<= lb(i, c) }

              val horz = Reduce(Reg[Int16])(Kh by 1, Kw by 1) { (i, j) =>
                val number = mux(r < Kh-1 || c < Kw-1, 0.to[Int16], sr(i, j))
                number * kh(i, j) // 8 bits needed
              }{_+_}

              val vert = Reduce(Reg[Int16])(Kh by 1, Kw by 1) { (i, j) =>
                val number = mux(r < Kh-1 || c < Kw-1, 0.to[Int16], sr(i, j))
                number * kv(i, j)  // 8 bits needed
              }{_+_}

              val pixelOut = abs(horz.value) + abs(vert.value) // 9 bits
              imgOut(r,c) = Pixel16(pixelOut(8::4).as[UInt5], pixelOut(8::3).as[UInt6], pixelOut(8::4).as[UInt5])
            }
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


object SimplestTest extends SpatialApp { 


  override val target = DE1

  val Kh = 3
  val Kw = 3
  val Rmax = 240
  val Cmax = 320

  type Int16 = FixPt[TRUE,_16,_0]
  type UInt8 = FixPt[FALSE,_8,_0]
  type UInt5 = FixPt[FALSE,_5,_0]
  type UInt6 = FixPt[FALSE,_6,_0]
//  @struct case class Pixel24(b: UInt8, g: UInt8, r: UInt8)
  @struct case class Pixel16(b: UInt5, g: UInt6, r: UInt5)

  @virtualize
  def convolveVideoStream(rows: Int, cols: Int): Unit = {
    val R = Rmax
    val C = Cmax

    val imgIn  = StreamIn[Pixel16](target.VideoCamera)
    val imgOut = BufferedOut[Pixel16](target.VGA)

    Accel(*) {
     
      Foreach(0 until Rmax, 0 until Cmax) {(i,j) =>
        imgOut(i,j) = Pixel16(imgIn.value().b, 0.to[UInt6], 0.to[UInt5])
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
