import spatial.dsl._
import org.virtualized._
import spatial.targets.DE1

object StreamingSobel extends SpatialApp { 


  override val target = DE1

  val Kh = 3
  val Kw = 3
  val Rmax = 240
  val Cmax = 320

  type Int16 = FixPt[TRUE,_16,_0]
  type UInt8 = FixPt[FALSE,_8,_0]
  type UInt5 = FixPt[FALSE,_5,_0]
  type UInt6 = FixPt[FALSE,_6,_0]
  @struct class sw3(forward: Bool, backward: Bool, unused: UInt8)
  @struct case class Pixel16(b: UInt5, g: UInt6, r: UInt5)

  @virtualize
  def convolveVideoStream(rows: Int, cols: Int): Unit = {
    // val switch = target.SliderSwitch
    // val swInput = StreamIn[sw3](switch)

    val imgIn  = StreamIn[Pixel16](target.VideoCamera)
    // val imgOut = BufferedOut[Pixel16](target.VGA)
    val imgOut = BufferedOut[Pixel16](target.VGA)

    Accel(*) {
      val kv = LUT[Int16](3, 3)(
         1,  2,  1,
         0,  0,  0,
        -1, -2, -1
      )
      val kh = LUT[Int16](3, 3)(
         1,  0, -1,
         2,  0, -2,
         1,  0, -1
      )

      val sr = RegFile[Int16](Kh, Kw)
      val lb = LineBuffer[Int16](Kh, Cmax)

      Foreach(0 until Rmax) { r =>
        Foreach(0 until Cmax) { _ => 
          val pixel = imgIn.value()
          val grayPixel = (pixel.b.to[Int16] + pixel.g.to[Int16] + pixel.r.to[Int16]) / 3
          lb.enq(grayPixel)
        }


        val horz = Reg[Int16](0)
        val vert = Reg[Int16](0)
        Foreach(0 until Cmax) { c =>
          Foreach(0 until Kh par Kh) { i => sr(i, *) <<= lb(i, c) }

          Reduce(horz)(Kh by 1, Kw by 1 par 1) { (i, j) =>
            val number = mux(r < Kh-1 || c < Kw-1, 0.to[Int16], sr(i, j))
            number * kh(i, j)
          }{_+_}

          Reduce(vert)(Kh by 1, Kw by 1 par 1) { (i, j) =>
            val number = mux(r < Kh-1 || c < Kw-1, 0.to[Int16], sr(i, j))
            number * kv(i, j)
          }{_+_};

          val result = abs(horz.value) + abs(vert.value)
          imgOut(r,c) = Pixel16(result(5::1).as[UInt5], result(5::0).as[UInt6], result(5::1).as[UInt5]) // Technically should be sqrt(horz**2 + vert**2)
        }
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

object StreamingSobelSRAM extends SpatialApp { 


  override val target = DE1

  val Kh = 3
  val Kw = 3
  val Rmax = 240
  val Cmax = 320

  type Int16 = FixPt[TRUE,_16,_0]
  type UInt8 = FixPt[FALSE,_8,_0]
  type UInt5 = FixPt[FALSE,_5,_0]
  type UInt6 = FixPt[FALSE,_6,_0]
  @struct class sw3(forward: Bool, backward: Bool, unused: UInt8)
  @struct case class Pixel16(b: UInt5, g: UInt6, r: UInt5)

  @virtualize
  def convolveVideoStream(rows: Int, cols: Int): Unit = {
    // val switch = target.SliderSwitch
    // val swInput = StreamIn[sw3](switch)

    val imgIn  = StreamIn[Pixel16](target.VideoCamera)
    // val imgOut = BufferedOut[Pixel16](target.VGA)
    val imgOut = BufferedOut[Pixel16](target.VGA)

    Accel(*) {
      val kv = LUT[Int16](3, 3)(
         1,  2,  1,
         0,  0,  0,
        -1, -2, -1
      )
      val kh = LUT[Int16](3, 3)(
         1,  0, -1,
         2,  0, -2,
         1,  0, -1
      )

      val sr = RegFile[Int16](Kh, Kw)
      val frame = SRAM[Int16](Rmax, Cmax)
      val lb = LineBuffer[Int16](Kh, Cmax)

      Foreach(0 until Rmax, 0 until Cmax) { (r,c) =>
        val pixel = imgIn.value()
        val grayPixel = (pixel.b.to[Int16] + pixel.g.to[Int16] + pixel.r.to[Int16]) / 3
        frame(r,c) = grayPixel
      }



      Foreach(0 until Rmax) { r => 
        Foreach(0 until Cmax) { c => 
          lb.enq(frame(r,c))
        }
        val horz = Reg[Int16](0)
        val vert = Reg[Int16](0)
        Foreach(0 until Cmax) { c =>
          Foreach(0 until Kh par Kh) { i => sr(i, *) <<= lb(i, c) }

          Reduce(horz)(Kh by 1, Kw by 1) { (i, j) =>
            val number = mux(r < Kh-1 || c < Kw-1, 0.to[Int16], sr(i, j))
            number * kh(i, j)
          }{_+_}

          Reduce(vert)(Kh by 1, Kw by 1) { (i, j) =>
            val number = mux(r < Kh-1 || c < Kw-1, 0.to[Int16], sr(i, j))
            number * kv(i, j)
          }{_+_};

          val result = abs(horz.value) + abs(vert.value)
          imgOut(r,c) = Pixel16(result(5::1).as[UInt5], result(5::0).as[UInt6], result(5::1).as[UInt5]) // Technically should be sqrt(horz**2 + vert**2)
        }
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


object Linebuf extends SpatialApp { 


  override val target = DE1

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

    val imgIn  = StreamIn[Pixel16](target.VideoCamera)
    val imgOut = BufferedOut[Pixel16](target.VGA)

    Accel(*) {

      val lb = LineBuffer[Int16](1, Cmax)

      Foreach(0 until Rmax) { r =>

        Foreach(0 until Cmax) { _ => 
          val pixel = imgIn.value()
          val grayPixel = (pixel.b.to[Int16] + pixel.g.to[Int16] + pixel.r.to[Int16]) / 3
          lb.enq( grayPixel )
        }

        Foreach(0 until Cmax) { c =>
          Foreach(0 until 1) { k => 
            val result = lb(k,c)
            imgOut(r,c) = Pixel16(result(5::1).as[UInt5], result(5::0).as[UInt6], result(5::1).as[UInt5]) // Technically should be sqrt(horz**2 + vert**2)
          }
        }


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

object Shiftreg extends SpatialApp { 


  override val target = DE1

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

    Accel(*) {

      val fifoIn = FIFO[Int16](2*Cmax)
      // val fifoOut = FIFO[Int16](2*Cmax)
      val sr = RegFile[Int16](1, Cmax/4)
      // val submitReady = FIFO[Bool](3)
      // val rowReady = FIFO[Bool](3)

      Foreach(0 until Rmax) { r =>
        Foreach(0 until Cmax) { _ => 
          val pixel = imgIn.value()
          val grayPixel = (pixel.b.to[Int16] + pixel.g.to[Int16] + pixel.r.to[Int16]) / 3
          fifoIn.enq( grayPixel )
        }


        Foreach(0 until Cmax by Cmax/4) { c =>
          Foreach(0 until Cmax/4) { cc => 
            sr(0, *) <<= fifoIn.deq
          }
          Foreach(0 until Cmax/4) { cc => 
            val result = sr(0,cc)
            imgOut(r,c+cc) = Pixel16(result(5::1).as[UInt5], result(5::0).as[UInt6], result(5::1).as[UInt5]) // Technically should be sqrt(horz**2 + vert**2)
          }
          // imgOut(r,c) = Pixel16(result(10::6).as[UInt5], result(10::5).as[UInt6], result(10::6).as[UInt5]) // Technically should be sqrt(horz**2 + vert**2)
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


  override val target = DE1

  val Kh = 3
  val Kw = 3
  val Rmax = 240
  val Cmax = 320

//  @struct case class Pixel24(b: UInt8, g: UInt8, r: UInt8)
  @struct case class Pixel16(b: UInt5, g: UInt6, r: UInt5)
  @struct class sw3(sel: UInt5, unused: UInt5)

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
          val f0 = swBits.sel
          val pixel = imgIn.value()
          val rpixel = Pixel16(0.to[UInt5], 0.to[UInt6], pixel.r)
          val gpixel = Pixel16(0.to[UInt5], pixel.g, 0.to[UInt5])
          val bpixel = Pixel16(pixel.b, 0.to[UInt6], 0.to[UInt5])
          val funpixel = Pixel16(pixel.r, pixel.b.as[UInt6], pixel.g.as[UInt5])
          val grayVal = (pixel.b.to[Int16] + pixel.g.to[Int16] + pixel.r.to[Int16]) / 3
          val grayPixel = Pixel16(grayVal(5::1).as[UInt5], grayVal(5::0).as[UInt6], grayVal(5::1).as[UInt5])
          val muxpix = mux(f0 <= 0, pixel,
                        mux(f0 <= 1, rpixel,
                        mux(f0 <= 2, gpixel,
                        mux(f0 <= 4, bpixel,
                        mux(f0 <= 8, funpixel,
                        mux(f0 <= 16, grayPixel, pixel))))))
          fifoIn.enq(muxpix)
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


  override val target = DE1

  val Kh = 3
  val Kw = 3
  val Rmax = 240
  val Cmax = 320

  @struct class sw3(forward: Bool, backward: Bool, unused: UInt8)

  @struct case class Pixel16(b: UInt5, g: UInt6, r: UInt5)

  @virtualize
  def convolveVideoStream(rows: Int, cols: Int): Unit = {
    val imgOut = BufferedOut[Pixel16](target.VGA)
    val imgIn  = StreamIn[Pixel16](target.VideoCamera)

    Accel(*) {
  
      val fifoIn = FIFO[Pixel16](Cmax*2)
      Foreach(0 until Rmax) { i =>
        Foreach(0 until Cmax ) { _ => 
          fifoIn.enq(imgIn.value())
        }

        Foreach(0 until Cmax) { j => 
          val pixel = fifoIn.deq()
          imgOut(i,j) = Pixel16(pixel.b, pixel.g, pixel.r)
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

