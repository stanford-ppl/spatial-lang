import spatial._
import org.virtualized._
import spatial.targets.DE1

object StreamingSobel extends SpatialApp { 
  import IR._

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
    // val R = ArgIn[Int]
    // val C = ArgIn[Int]
    // setArg(R, rows)
    // setArg(C, cols)

    val imgIn  = StreamIn[Pixel16](target.VideoCamera)
    // val imgOut = BufferedOut[Pixel16](target.VGA)
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
      val fifoIn = FIFO[Int16](2*Cmax)
      // val fifoOut = FIFO[Int16](2*Cmax)
      val lb = LineBuffer[Int16](Kh, Cmax)
      // val submitReady = FIFO[Bool](3)
      // val rowReady = FIFO[Bool](3)

      Stream(*) { _ =>

        Sequential.Foreach(0 until Rmax) { r =>
          Foreach(0 until Cmax) { _ => 
            val pixel = imgIn.value()
            val grayPixel = (pixel.b.to[Int16] + pixel.g.to[Int16] + pixel.r.to[Int16]) / 3
            fifoIn.enq( grayPixel )
          }

          Foreach(0 until Cmax) { _ => 
            lb.enq(fifoIn.deq, true)
          }

          Foreach(0 until Cmax) { c =>
            Foreach(0 until Kh par Kh) { i => sr(i, *) <<= lb(i, c) }

            val horz = Reduce(Reg[Int16])(Kh by 1, Kw by 1) { (i, j) =>
              val number = mux(r < Kh-1 || c < Kw-1, 0.to[Int16], sr(i, j))
              number * kh(i, j)
            }{_+_}

            val vert = Reduce(Reg[Int16])(Kh by 1, Kw by 1) { (i, j) =>
              val number = mux(r < Kh-1 || c < Kw-1, 0.to[Int16], sr(i, j))
              number * kv(i, j)
            }{_+_};

            val result = abs(horz.value) + abs(vert.value)
            imgOut(r,c) = Pixel16(result(4::0).as[UInt5], result(5::0).as[UInt6], result(4::0).as[UInt5]) // Technically should be sqrt(horz**2 + vert**2)
            // imgOut(r,c) = Pixel16(result(10::6).as[UInt5], result(10::5).as[UInt6], result(10::6).as[UInt5]) // Technically should be sqrt(horz**2 + vert**2)
          }
        }

        // Pipe {
        //   Pipe{ submitReady.deq() }
        //   Foreach(0 until R*Cmax) {i => 
        //     val pixelOut = fifoOut.deq()
        //     // Ignore MSB - pixelOut is a signed number that's definitely positive, so MSB is always 0
        //     imgOut(r,i) = Pixel16(pixelOut(10::6).as[UInt5], pixelOut(10::5).as[UInt6], pixelOut(10::6).as[UInt5])
        //   }
        // }
      }
      ()
    }
  }

  @virtualize
  def main() {
    val R = Rmax
    val C = Cmax
    convolveVideoStream(R, C)
  }
}



object SwitchVid extends SpatialApp { // BUSTED.  HOW TO USE SWITCHES?
  import IR._

  override val target = DE1

  val Kh = 3
  val Kw = 3
  val Rmax = 240
  val Cmax = 320

//  @struct case class Pixel24(b: UInt8, g: UInt8, r: UInt8)
  @struct case class Pixel16(b: UInt5, g: UInt6, r: UInt5)
  @struct class sw3(b1: Bool, b2: Bool, b3: Bool)

  @virtualize
  def convolveVideoStream(rows: Int, cols: Int): Unit = {
    val imgOut = BufferedOut[Pixel16](target.VGA)
    val imgIn  = StreamIn[Pixel16](target.VideoCamera)    
    val switch = target.SliderSwitch
    val swInput = StreamIn[sw3](switch)

    Accel(*) {
      val fifoIn = FIFO[Pixel16](Cmax*2)
      Foreach(0 until Rmax) { i =>
        Foreach(0 until Cmax) { j => 
          val swBits = swInput.value()
          val f0 = swBits.b1
          val pixel = imgIn.value()
          val grayVal = (pixel.b.to[Int16] + pixel.g.to[Int16] + pixel.r.to[Int16]) / 3
          val grayPixel = Pixel16(grayVal.to[UInt5], grayVal.to[UInt6], grayVal.to[UInt5])
          fifoIn.enq(mux(f0, grayPixel, pixel))
        }
        Foreach(0 until Cmax) { j => 
          val pixel = fifoIn.deq()
          imgOut(i,j) = Pixel16(pixel.b, pixel.g, pixel.r)
        }
      }
    }
  }

  @virtualize
  def main() {
    val R = Rmax
    val C = Cmax
    convolveVideoStream(R, C)
  }
}

object Grayscale extends SpatialApp { 
  import IR._

  override val target = DE1

  val Kh = 3
  val Kw = 3
  val Rmax = 240
  val Cmax = 320

//  @struct case class Pixel24(b: UInt8, g: UInt8, r: UInt8)
  @struct case class Pixel16(b: UInt5, g: UInt6, r: UInt5)

  @virtualize
  def convolveVideoStream(rows: Int, cols: Int): Unit = {
    val imgOut = BufferedOut[Pixel16](target.VGA)
    val imgIn  = StreamIn[Pixel16](target.VideoCamera)

    Accel(*) {

  
        val fifoIn = FIFO[Int16](Cmax*2)
        Foreach(0 until Rmax) { i =>
          Foreach(0 until Cmax) { j => 
            val pixel = imgIn.value()
            val grayPixel = (pixel.b.to[Int16] + pixel.g.to[Int16] + pixel.r.to[Int16]) / 3
            fifoIn.enq(grayPixel)
          }
          Foreach(0 until Cmax) { j => 
            val pixel = fifoIn.deq()
            imgOut(i,j) = Pixel16(pixel(5::1).as[UInt5], pixel(5::0).as[UInt6], pixel(5::1).as[UInt5])
          }
        }
    }
  }

  @virtualize
  def main() {
    val R = Rmax
    val C = Cmax
    convolveVideoStream(R, C)
  }
}


object FifoVideo extends SpatialApp { 
  import IR._

  override val target = DE1

  val Kh = 3
  val Kw = 3
  val Rmax = 240
  val Cmax = 320

//  @struct case class Pixel24(b: UInt8, g: UInt8, r: UInt8)
  @struct case class Pixel16(b: UInt5, g: UInt6, r: UInt5)

  @virtualize
  def convolveVideoStream(rows: Int, cols: Int): Unit = {
    val imgOut = BufferedOut[Pixel16](target.VGA)
    val imgIn  = StreamIn[Pixel16](target.VideoCamera)

    Accel {

      Stream(*) { _ =>
  
        val fifoIn = FIFO[Pixel16](Cmax*2)
        Foreach(0 until Rmax) { i =>
          Foreach(0 until Cmax) { j => 
            // val data = Pixel16(j.as[UInt5], j.as[UInt6], j.as[UInt5])
            // fifoIn.enq(data)
            fifoIn.enq(imgIn.value())
          }
          Foreach(0 until Cmax) { j => 
            val pixel = fifoIn.deq()
            imgOut(i,j) = Pixel16(pixel.b, pixel.g, pixel.r)
          }
        }
      }
      // ()
    }
  }

  @virtualize
  def main() {
    val R = Rmax
    val C = Cmax
    convolveVideoStream(R, C)
  }
}

