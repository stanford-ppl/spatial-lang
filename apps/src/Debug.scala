import spatial._
import org.virtualized._
import spatial.targets.DE1

object Debug extends SpatialApp {
  import IR._

  override val target = DE1

  val R = 128  // FIXME
  val C = 128  // FIXME
  val t = 50  // FIXME
  val n = 12
  val lb_height = 7
  val lb_width = 128
  val sr_height = 7
  val sr_width = 7

  type Int16 = FixPt[TRUE,_16,_0]
  type UInt10 = FixPt[FALSE,_10,_0]
  type UInt8 = FixPt[FALSE,_8,_0]
  type UInt5 = FixPt[FALSE,_5,_0]
  type UInt6 = FixPt[FALSE,_6,_0]
  type UInt3 = FixPt[FALSE,_3,_0]
  @struct case class Pixel24(b: UInt8, g: UInt8, r: UInt8)
  @struct case class Pixel16(b: UInt5, g: UInt6, r: UInt5)

  @struct case class Coordinate(x: Int16, y: Int16)

  @virtualize
  def detect_FAST(): Unit = {
    val imgIn  = StreamIn[Pixel24](target.VideoCamera)
    // val imgOut = StreamOut[Pixel24](target.VGA)
    val imgOut = BufferedOut[Pixel24](target.VGA)
    // val imgOut = StreamOut[Pixel16](target.VGA)

    Accel {
      val sr_coord = RegFile[Coordinate](16)
      Pipe{
        sr_coord(0) = Coordinate(2.to[Int16], 0.to[Int16])
        sr_coord(1) = Coordinate(3.to[Int16], 0.to[Int16])
        sr_coord(2) = Coordinate(4.to[Int16], 0.to[Int16])
        sr_coord(3) = Coordinate(5.to[Int16], 1.to[Int16])
        sr_coord(4) = Coordinate(6.to[Int16], 2.to[Int16])
        sr_coord(5) = Coordinate(6.to[Int16], 3.to[Int16])
        sr_coord(6) = Coordinate(6.to[Int16], 4.to[Int16])
        sr_coord(7) = Coordinate(5.to[Int16], 5.to[Int16])
        sr_coord(8) = Coordinate(4.to[Int16], 6.to[Int16])
        sr_coord(9) = Coordinate(3.to[Int16], 6.to[Int16])
        sr_coord(10) = Coordinate(2.to[Int16], 6.to[Int16])
        sr_coord(11) = Coordinate(1.to[Int16], 5.to[Int16])
        sr_coord(12) = Coordinate(0.to[Int16], 4.to[Int16])
        sr_coord(13) = Coordinate(0.to[Int16], 3.to[Int16])
        sr_coord(14) = Coordinate(0.to[Int16], 2.to[Int16])
        sr_coord(15) = Coordinate(1.to[Int16], 1.to[Int16])
      }

      val sr = RegFile[Int16](sr_height, sr_width)
      val fifoIn = FIFO[Int16](C)
      // val fifoOut = FIFO[Int16](C)
      val lb = LineBuffer[Int16](lb_height, C)

      // val row = 0
      // val col = 0

      val curr = Reg[Int16](0)
      val is_feature = Reg[Int16](0)
      val running_count = Reg[Int16](0)

      println("Start of stream")

      Stream(*) { _ =>

        // if (col == 639) {
        //  col = 0
        //  if (row == 479) {
        //      row = 0
        //  } else {
        //      row = row + 1
        //  }
        // } else {
        //  col = col + 1
        // }

        Sequential.Foreach(0 until R){ r =>
          Sequential.Foreach(0 until C){ _ =>
            val pixel = imgIn.value()
            val grayscale_pixel = (pixel.b.to[Int16] + pixel.g.to[Int16] + pixel.r.to[Int16]) / 3
            lb.enq(grayscale_pixel)
            fifoIn.enq(grayscale_pixel)
          }
          Sequential.Foreach(0 until C){ c =>
            // println("loop " + r + " " + c)
            val grayscale_pixel = fifoIn.deq()

            Foreach(0 until sr_height){ i =>
              sr(i, *) <<= lb(i, c)
            }

            val ring_values = RegFile[Int16](32)

            Sequential.Foreach(0 until 16){ i =>
              val ring_pixel = sr_coord(i)
              println(ring_pixel.x.to[Index] + " " + ring_pixel.y.to[Index])
              val ring_pixel_val = sr(ring_pixel.x.to[Index], ring_pixel.y.to[Index])
              // if (r == 113 && c == 77) {
              //   println(ring_pixel_val)
              // }
              Sequential {
                if (ring_pixel_val < grayscale_pixel - t) {
                  ring_values(i) = -1
                  ring_values(i+16) = -1
                } else if (ring_pixel_val > grayscale_pixel + t) {
                  ring_values(i) = 1
                  ring_values(i+16) = 1
                } else {
                  ring_values(i) = 0
                  ring_values(i+16) = 0
                }
              }
            }

            // Figure out if 12 continguous values below or above threshold
            running_count := 1
            curr := ring_values(0)
            is_feature := 0
            Sequential.Foreach(1 until 32){ i =>
              if (ring_values(i) == curr.value) {
                running_count := running_count.value + 1
              } else {
                running_count := 1
                curr := ring_values(i)
              }

              if (running_count.value == 12.to[Int16] && curr.value != 0.to[Int16]) {
                is_feature := 1
                println(r + " " + c + " " + ring_values(0) + " " + ring_values(1) + " " +
                  ring_values(2) + " " + ring_values(3) + " " + ring_values(4) + " " +
                  ring_values(5) + " " + ring_values(6) + " " + ring_values(7) + " " +
                  ring_values(8) + " " + ring_values(9) + " " + ring_values(10) + " " +
                  ring_values(11) + " " + ring_values(12) + " " + ring_values(13) + " " +
                  ring_values(14) + " " + ring_values(15))
                println(" grayscale " + grayscale_pixel)
                // TODO: does Spatial have break statement?
              }
            }

            // if (is_feature.value == 1.to[Int16]) {
            //   fifoOut.enq(0)  // black
            // } else {
            //   fifoOut.enq(grayscale_pixel)
            // }

            // fifoOut.enq(mux[Int16](is_feature.value == 1.to[Int16], 255, grayscale_pixel))
            val pixel_value = mux[Int16](is_feature.value == 1.to[Int16], 255, grayscale_pixel)
            imgOut(r, c) = Pixel24(pixel_value(7::0).as[UInt8],
              pixel_value(7::0).as[UInt8],
              pixel_value(7::0).as[UInt8])
          }
        }

        // TODO: This step should be performed outside of the Foreach loop over the whole frame. You will need to dequeue pixels from fifOut, and send it to imgOut (The StreamOut port).
        // YOUR CODE HERE:
        // val pixel_value = fifoOut.deq()
        // imgOut := Pixel24(pixel_value(7::0).as[UInt8],
        //   pixel_value(7::0).as[UInt8],
        //   pixel_value(7::0).as[UInt8])
        // imgOut := Pixel16(pixel_value(7::3).as[UInt5],
        //   pixel_value(7::2).as[UInt6],
        //   pixel_value(7::3).as[UInt5])
      }
      ()
    }
  }

  @virtualize
  def main() {
    detect_FAST()
  }
}
