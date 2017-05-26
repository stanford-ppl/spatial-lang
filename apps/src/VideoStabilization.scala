import spatial._
import org.virtualized._
import spatial.targets.DE1

object VideoStabilization extends SpatialApp {
  import IR._

  override val target = DE1

  val R = 128  // FIXME
  val C = 128  // FIXME
  val t = 50.to[Int16]  // FIXME
  val n = 12
  val lb_height = 7
  val lb_width = 640
  val sr_height = 7
  val sr_width = 7
  val hamming_threshold = 0.8.to[Float]

  type Int16 = FixPt[TRUE,_16,_0]
  type UInt10 = FixPt[FALSE,_10,_0]
  type UInt8 = FixPt[FALSE,_8,_0]
  type UInt5 = FixPt[FALSE,_5,_0]
  type UInt6 = FixPt[FALSE,_6,_0]
  type UInt3 = FixPt[FALSE,_3,_0]
  type UInt1 = FixPt[FALSE,_1,_0]
  @struct case class Pixel24(b: UInt8, g: UInt8, r: UInt8)
  @struct case class Pixel16(b: UInt5, g: UInt6, r: UInt5)

  @struct case class Coordinate(x: Int16, y: Int16)

  @virtualize
  def detect_FAST(): Unit = {
    val imgIn  = StreamIn[Pixel24](target.VideoCamera)  // TODO: change back to Pixel16
    // val imgIn2  = StreamIn[Pixel24](target.VideoCamera)  // TODO: change back to Pixel16
    val imgOut = StreamOut[Pixel24](target.VGA)
    // val imgOut = BufferedOut[Pixel24](target.VGA)
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

      val descriptor_coord_1 = RegFile[Coordinate](16)
      Pipe{
        descriptor_coord_1(0) = Coordinate(0.to[Int16], 0.to[Int16])
        descriptor_coord_1(1) = Coordinate(1.to[Int16], 0.to[Int16])
        descriptor_coord_1(2) = Coordinate(2.to[Int16], 0.to[Int16])
        descriptor_coord_1(3) = Coordinate(3.to[Int16], 0.to[Int16])
        descriptor_coord_1(4) = Coordinate(4.to[Int16], 0.to[Int16])
        descriptor_coord_1(5) = Coordinate(5.to[Int16], 0.to[Int16])
        descriptor_coord_1(6) = Coordinate(5.to[Int16], 0.to[Int16])

        descriptor_coord_1(7) = Coordinate(0.to[Int16], 0.to[Int16])
        descriptor_coord_1(8) = Coordinate(0.to[Int16], 1.to[Int16])
        descriptor_coord_1(9) = Coordinate(0.to[Int16], 2.to[Int16])
        descriptor_coord_1(10) = Coordinate(0.to[Int16], 3.to[Int16])
        descriptor_coord_1(11) = Coordinate(0.to[Int16], 4.to[Int16])
        descriptor_coord_1(12) = Coordinate(0.to[Int16], 5.to[Int16])
        descriptor_coord_1(13) = Coordinate(0.to[Int16], 6.to[Int16])

        descriptor_coord_1(14) = Coordinate(0.to[Int16], 0.to[Int16])
        descriptor_coord_1(15) = Coordinate(0.to[Int16], 6.to[Int16])
      }

      val descriptor_coord_2 = RegFile[Coordinate](16)
      Pipe{
        descriptor_coord_2(0) = Coordinate(0.to[Int16], 6.to[Int16])
        descriptor_coord_2(1) = Coordinate(1.to[Int16], 6.to[Int16])
        descriptor_coord_2(2) = Coordinate(2.to[Int16], 6.to[Int16])
        descriptor_coord_2(3) = Coordinate(3.to[Int16], 6.to[Int16])
        descriptor_coord_2(4) = Coordinate(4.to[Int16], 6.to[Int16])
        descriptor_coord_2(5) = Coordinate(5.to[Int16], 6.to[Int16])
        descriptor_coord_2(6) = Coordinate(5.to[Int16], 6.to[Int16])

        descriptor_coord_2(7) = Coordinate(6.to[Int16], 0.to[Int16])
        descriptor_coord_2(8) = Coordinate(6.to[Int16], 1.to[Int16])
        descriptor_coord_2(9) = Coordinate(6.to[Int16], 2.to[Int16])
        descriptor_coord_2(10) = Coordinate(6.to[Int16], 3.to[Int16])
        descriptor_coord_2(11) = Coordinate(6.to[Int16], 4.to[Int16])
        descriptor_coord_2(12) = Coordinate(6.to[Int16], 5.to[Int16])
        descriptor_coord_2(13) = Coordinate(6.to[Int16], 6.to[Int16])

        descriptor_coord_2(14) = Coordinate(6.to[Int16], 0.to[Int16])
        descriptor_coord_2(15) = Coordinate(6.to[Int16], 6.to[Int16])
      }

      val sr = RegFile[Int16](sr_height, sr_width)
      val fifoIn = FIFO[Int16](C)
      val fifoOut = FIFO[Int16](C)
      val lb = LineBuffer[Int16](lb_height, lb_width)

      val fifoDescriptor1 = FIFO[UInt1](3200)
      val fifoDescriptor2 = FIFO[UInt1](3200)

      val numDescriptors = Reg[Int16](0)
      val numDescriptorsPrev = Reg[Int16](0)

      // val lastDescriptor = RegFile[UInt1](16)

      // val row = 0
      // val col = 0

      val curr = Reg[Int16](0)
      val is_feature = Reg[Int16](0)
      val running_count = Reg[Int16](0)
      val frame_counter = Reg[UInt1](0)
      val hamming_distance = Reg[Int16](0)
      val min_hamming_distance = Reg[Int16](0)
      val second_min_hamming_distance = Reg[Int16](0)

      frame_counter := 0

      println("Start of stream")

      Stream(*) { _ =>
        val pixel = imgIn.value()
        val grayscale_pixel = (pixel.b.to[Int16] + pixel.g.to[Int16] + pixel.r.to[Int16]) / 3
        fifoIn.enq(grayscale_pixel)

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

        Foreach(0 until R, 0 until C){ (r, c) =>
          Sequential {
            val grayscale_pixel = fifoIn.deq()
            lb.enq(grayscale_pixel)
            Foreach(0 until sr_height){ i =>
              sr(i, *) <<= lb(i, c)
            }

            // println("row " + r + " col " + c + " grayscale " + grayscale_pixel)

            val ring_values = RegFile[Int16](32)

            Foreach(0 until 16){ i =>
              val ring_pixel = sr_coord(i)
              val ring_pixel_val = sr(ring_pixel.x.to[Index], ring_pixel.y.to[Index])
              // println(ring_pixel_val)
              // if (r == 113 && c == 77) {
              //   println(ring_pixel_val)
              // }
              // println(ring_pixel_val + " " + (grayscale_pixel + t))
              // println(ring_pixel_val > (grayscale_pixel + t))
              Sequential {
                ring_values(i) = 0
                ring_values(i+16) = 0
                if (ring_pixel_val < (grayscale_pixel - t)) {
                  ring_values(i) = -1
                  ring_values(i+16) = -1
                }
                if (ring_pixel_val > (grayscale_pixel + t)) {
                  ring_values(i) = 1
                  ring_values(i+16) = 1
                }
              }
            }

            // Figure out if 12 contiguous values below or above threshold
            running_count := 1
            curr := ring_values(0)
            is_feature := 0
            Foreach(1 until 32){ i =>
              if (ring_values(i) == curr.value) {
                running_count := running_count.value + 1
              } else {
                running_count := 1
                curr := ring_values(i)
              }

              if (running_count.value == 12.to[Int16] && curr.value != 0.to[Int16]) {
                is_feature := 1
                println(r + " " + c)
              }
            }

            if ((is_feature.value == 1.to[Int16]) && (r > 4.to[Index])) {
              val brief_descriptor = RegFile[UInt1](16)
              Foreach(0 until 16){ i =>
                Sequential {
                  val pt1 = descriptor_coord_1(i)
                  val pt2 = descriptor_coord_2(i)
                  brief_descriptor(i) = 0.to[UInt1]
                  if (sr(pt1.x.to[Index], pt1.y.to[Index]) > sr(pt2.x.to[Index], pt2.y.to[Index])) {
                    brief_descriptor(i) = 1.to[UInt1]
                  }
                }
              }
              // println(brief_descriptor(0) + " " + brief_descriptor(1) + " " +
              //         brief_descriptor(2) + " " + brief_descriptor(3) + " " + brief_descriptor(4) + " " +
              //         brief_descriptor(5) + " " + brief_descriptor(6) + " " + brief_descriptor(7) + " " +
              //         brief_descriptor(8) + " " + brief_descriptor(9) + " " + brief_descriptor(10) + " " +
              //         brief_descriptor(11) + " " + brief_descriptor(12) + " " + brief_descriptor(13) + " " +
              //         brief_descriptor(14) + " " + brief_descriptor(15))

              // val curr_fifo = mux[FIFO[UInt1]](frame_counter.value == 0.to[UInt1], fifoDescriptor1, fifoDescriptor2)
              // val prev_fifo = mux[FIFO[UInt1]](frame_counter.value == 0.to[UInt1], fifoDescriptor2, fifoDescriptor1)

              // if (frame_counter.value == 0.to[UInt1]) {
              //   curr_fifo := fifoDescriptor1
              // } else {
              //   prev_fifo := fifoDescriptor2
              // }

              // match with descriptors from previous frame
              min_hamming_distance := 16
              second_min_hamming_distance := 16
              Foreach(0 until numDescriptorsPrev.value.to[Index]){ i =>
                hamming_distance := 0
                // Foreach(0 until 16){ j =>
                //   val bit = prev_fifo.deq()
                //   if (bit != brief_descriptor(j)) {
                //     hamming_distance := hamming_distance.value + 1
                //   }
                //   prev_fifo.enq(bit)
                // }
                if (frame_counter.value == 0.to[UInt1]) {
                  Foreach(0 until 16){ j =>
                    val bit = fifoDescriptor2.deq()
                    if (bit != brief_descriptor(j)) {
                      hamming_distance := hamming_distance.value + 1
                    }
                    fifoDescriptor2.enq(bit, frame_counter.value == 0.to[UInt1])
                  }
                } else {
                  Foreach(0 until 16){ j =>
                    val bit = fifoDescriptor1.deq()
                    if (bit != brief_descriptor(j)) {
                      hamming_distance := hamming_distance.value + 1
                    }
                    fifoDescriptor1.enq(bit, frame_counter.value == 1.to[UInt1])
                  }
                }
                if (hamming_distance.value < min_hamming_distance.value) {
                  second_min_hamming_distance := min_hamming_distance.value
                  min_hamming_distance := hamming_distance.value
                } else if (hamming_distance.value < second_min_hamming_distance.value) {
                  second_min_hamming_distance := hamming_distance.value
                }
              }
              // TODO: add distance and orientation checks from IEEE paper
              if (min_hamming_distance.value.to[Float] / second_min_hamming_distance.value.to[Float] > hamming_threshold) {
                // TODO: do something with match
              }

              // // alternate matching scheme
              // Foreach(0 until numDescriptorsPrev){ i =>
              //   hamming_distance := 0
              //   if (frame_counter.value == 0) {
              //     Foreach(0 until 16){ j =>
              //       val bit = fifoDescriptor2.deq()
              //       if (bit != brief_descriptor(j)) {
              //         hamming_distance := hamming_distance.value + 1
              //       }
              //       fifoDescriptor2.enq(bit)
              //     }
              //   } else {
              //     Foreach(0 until 16){ j =>
              //       val bit = fifoDescriptor1.deq()
              //       if (bit != brief_descriptor(j)) {
              //         hamming_distance := hamming_distance.value + 1
              //       }
              //       fifoDescriptor1.enq(bit)
              //     }
              //   }
              //   if (hamming_distance.value < 12) {
              //     // TODO: do something with match
              //   }
              // }

              // enqueue the current descriptor
              Foreach(0 until 16){ i =>
                if (frame_counter.value == 0.to[UInt1]) {
                  fifoDescriptor1.enq(brief_descriptor(i), frame_counter.value == 0.to[UInt1])
                } else {
                  fifoDescriptor2.enq(brief_descriptor(i), frame_counter.value == 1.to[UInt1])
                }
              }
              numDescriptors := numDescriptors.value + 1
            }

            // For debug purposes
            fifoOut.enq(mux[Int16]((is_feature.value == 1.to[Int16]) && (r > 4.to[Index]), 255, grayscale_pixel))

            // end of frame updates
            if (r == R-1 && c == C-1) {
              frame_counter := mux[UInt1](frame_counter == 0.to[UInt1], 1, 0)
              numDescriptorsPrev := numDescriptors.value
              numDescriptors := 0
            }

            // val pixel_value = fifoOut.deq()
            // imgOut(r,c) = Pixel24(pixel_value(7::0).as[UInt8],
            //   pixel_value(7::0).as[UInt8],
            //   pixel_value(7::0).as[UInt8])
          }
        }

        // TODO: This step should be performed outside of the Foreach loop over the whole frame. You will need to dequeue pixels from fifOut, and send it to imgOut (The StreamOut port).
        // YOUR CODE HERE:
        val pixel_value = fifoOut.deq()
        imgOut := Pixel24(pixel_value(7::0).as[UInt8],
          pixel_value(7::0).as[UInt8],
          pixel_value(7::0).as[UInt8])
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