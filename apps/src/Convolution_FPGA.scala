import spatial._
import org.virtualized._

object Convolution_FPGA extends SpatialApp { // Regression (Dense) // Args: none
  import IR._

  val Kh = 3
  val Kw = 3
  val Cmax = 100

  @virtualize
  def convolve[T:Type:Num](image: Matrix[T]): Matrix[T] = {
    val B = 16 (1 -> 1 -> 16)

    val R = ArgIn[Int]
    val C = ArgIn[Int]
    setArg(R, image.rows)
    setArg(C, image.cols)

    val img = DRAM[T](R, C)
    val imgOut = DRAM[T](R, C)

    setMem(img, image)

    Accel {
      val lb = LineBuffer[T](Kh, Cmax)
      val kh = RegFile[T](Kh, Kw)
      val kv = RegFile[T](Kh, Kw)

      // TODO: Better syntax for initialization of lookup tables
      Pipe {
        Pipe{kh(0,0) = 1.to[T]}
        Pipe{kh(1,0) = 2.to[T]}
        Pipe{kh(2,0) = 1.to[T]}
        Pipe{kh(0,1) = 0.to[T]}
        Pipe{kh(1,1) = 0.to[T]}
        Pipe{kh(2,1) = 0.to[T]}
        Pipe{kh(0,2) = -1.to[T]}
        Pipe{kh(1,2) = -2.to[T]}
        Pipe{kh(2,2) = -1.to[T]}

        Pipe{kv(0,0) = 1.to[T]}
        Pipe{kv(0,1) = 2.to[T]}
        Pipe{kv(0,2) = 1.to[T]}
        Pipe{kv(1,0) = 0.to[T]}
        Pipe{kv(1,1) = 0.to[T]}
        Pipe{kv(1,2) = 0.to[T]}
        Pipe{kv(2,0) = -1.to[T]}
        Pipe{kv(2,1) = -2.to[T]}
        Pipe{kv(2,2) = -1.to[T]}
      }

      val sr = RegFile[T](Kh, Kw)
      val lineOut = SRAM[T](Cmax)

      Foreach(0 until R) { r =>
        lb load img(r, 0::C)

        /*println("Row " + r)
        Foreach(0 until Kh) { i =>
          Foreach(0 until C) { c => print("" + lb(i,c) + "\t") }
          println("")
        }*/

        Sequential.Foreach(0 until C) { c =>
          Foreach(0 until Kh par Kh){i => sr(i, *) <<= lb(i, c) }

          
          val horz = Reduce(Reg[T])(Kh by 1, Kw by 1){ (i,j) => 
            val number = mux((r < 2) || (c < 2) , 0.to[T], sr(i,j))
            number * kh(i,j) 
          }{_+_}
          val vert = Reduce(Reg[T])(Kh by 1, Kw by 1){ (i,j) => 
            val number = mux((r < 2) || (c < 2) , 0.to[T], sr(i,j))
            number * kv(i,j) 
          }{_+_}

          lineOut(c) = abs(horz.value) + abs(vert.value) // Technically should be sqrt(horz**2 + vert**2)
        }

        imgOut(r, 0::C par 16) store lineOut
      }

    }

    getMatrix(imgOut)

  }

  @virtualize
  def main() {
    val R = 16
    val C = 16
    val border = 3
    // val image = (0::R, 0::C){(i,j) => if (j > 3 && i > 3 && j < 11 && i < 11) 256 else 0 }
    val image = (0::R, 0::C){(i,j) => if (j > border && j < C-border && i > border && i < C - border) i*16 else 0}
    val ids = (0::R, 0::C){(i,j) => if (i < 2) 0 else 1}

    val kh = List((List(1,2,1), List(0,0,0), List(-1,-2,-1)))
    val kv = List((List(1,0,-1), List(2,0,-2), List(1,0,-1)))

    val output = convolve(image)

    /*
      Filters: 
      1   2   1 
      0   0   0 
     -1  -2  -1

      1   0  -1 
      2   0  -2 
      1   0  -1

    */
    val gold = (0::R, 0::C){(i,j) => 
      // Shift result down by 2 and over by 2 because of the way accel is written
      val px00 = if ((j-2) > border && (j-2) < C-border && (i-2) > border && (i-2) < C - border) (i-2)*16 else 0
      val px01 = if ((j-1) > border && (j-1) < C-border && (i-2) > border && (i-2) < C - border) (i-2)*16 else 0
      val px02 = if ((j+0) > border && (j+0) < C-border && (i-2) > border && (i-2) < C - border) (i-2)*16 else 0
      val px10 = if ((j-2) > border && (j-2) < C-border && (i-1) > border && (i-1) < C - border) (i-1)*16 else 0
      val px11 = if ((j-1) > border && (j-1) < C-border && (i-1) > border && (i-1) < C - border) (i-1)*16 else 0
      val px12 = if ((j+0) > border && (j+0) < C-border && (i-1) > border && (i-1) < C - border) (i-1)*16 else 0
      val px20 = if ((j-2) > border && (j-2) < C-border && (i+0) > border && (i+0) < C - border) (i+0)*16 else 0
      val px21 = if ((j-1) > border && (j-1) < C-border && (i+0) > border && (i+0) < C - border) (i+0)*16 else 0
      val px22 = if ((j+0) > border && (j+0) < C-border && (i+0) > border && (i+0) < C - border) (i+0)*16 else 0
      abs(px00 * 1 + px01 * 2 + px02 * 1 - px20 * 1 - px21 * 2 - px22 * 1) + abs(px00 * 1 - px02 * 1 + px10 * 2 - px12 * 2 + px20 * 1 - px22 * 1)
    };

    // // This contains the "weird scheduling bug"
    printMatrix(image, "Image")
    printMatrix(gold, "Gold")
    printMatrix(output, "Output")

    val gold_sum = gold.map{g => g}.reduce{_+_} 
    val output_sum = output.zip(ids){case (o,i) => i * o}.reduce{_+_}
    println("gold " + gold_sum + " =?= output " + output_sum)
    val cksum = gold_sum == output_sum
    // val cksum = gold.zip(output){(g, o) => g == o}.reduce{_&&_}
    println("PASS: " + cksum + " (Convolution_FPGA)")



  }
}
