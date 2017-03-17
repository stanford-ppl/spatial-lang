import spatial._
import org.virtualized._

object Convolution_FPGA extends SpatialApp {
  import IR._

  val Kh = 3
  val Kw = 3
  val Cmax = 100

  @virtualize
  def convolve[T:Staged:Num](image: Matrix[T]): Matrix[T] = {
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
      val k  = RegFile[T](Kh, Kw)

      // TODO: Better syntax for initialization of lookup tables
      Pipe {
        k(0,0) = 1.as[T]
        k(0,1) = 0.as[T]
        k(0,2) = -1.as[T]
        k(1,0) = 2.as[T]
        k(1,1) = 0.as[T]
        k(1,2) = -2.as[T]
        k(2,0) = 1.as[T]
        k(2,1) = 0.as[T]
        k(2,2) = -1.as[T]
      }

      val sr = RegFile[T](Kh, Kw)
      val lineOut = SRAM[T](Cmax)

      Foreach(0 until R) { r =>
        lb load img(r, 0::C)

        Foreach(0 until C){c => print("" + lb(r,c) + "\t") }
        println("")

        Foreach(0 until C) { c =>
          Foreach(0 until Kh par Kh){i => sr(i, *) <<= lb(i, c) }

          Foreach(0 until 3, 0 until 3){(i,j) => print("" + sr(i,j) + " ") }
          println("")
          Foreach(0 until 3, 0 until 3){(i,j) => print("" + k(i,j) + " ") }
          println("")

          val pixel = Reduce(Reg[T])(Kh by 1, Kw by 1){ (i,j) => sr(i,j) * k(i,j) }{_+_}

          lineOut(c) = pixel
        }

        imgOut(r, 0::C) store lineOut
      }

    }

    getMatrix(imgOut)

  }

  @virtualize
  def main() {
    val R = 15
    val C = 15
    val image = (0::R, 0::C){(i,j) => if (j > 7) 256 else 0 }

    val output = convolve(image)

    val gold = (0::R, 0::C){(i,j) => if (j == 7) 1 else 0 }

    printMatrix(image, "Image")
    printMatrix(output, "Output")
  }
}
