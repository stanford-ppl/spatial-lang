import spatial._
import org.virtualized._

object Convolution_CGRA extends SpatialApp {
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
      val kh = RegFile[T](Kh, Kw)

      // TODO: Better syntax for initialization of lookup tables
      Pipe {
        kh(0,0) = 1.as[T]
        kh(1,0) = 2.as[T]
        kh(2,0) = 1.as[T]
        kh(0,1) = 0.as[T]
        kh(1,1) = 0.as[T]
        kh(2,1) = 0.as[T]
        kh(0,2) = -1.as[T]
        kh(1,2) = -2.as[T]
        kh(2,2) = -1.as[T]
      }

      val lineOut = SRAM[T](Cmax)

      val sr = RegFile[T](B + Kw - 1)

      Foreach(0 until R) { r =>

        lb load img(r, 0::C)

        /*println("Row " + r)
        Foreach(0 until Kh) { i =>
          Foreach(0 until C) { c => print("" + lb(i,c) + "\t") }
          println("")
        }*/

        Foreach(0 until C by B) { c =>
          val out = RegFile[T](B)
          Foreach(Kh by 1) { i =>
            sr <<= lb(i, c :: c + B)

            //Foreach((B + Kw - 1) by 1){j => println("" + sr(j) + "\t") }
            //println("")

            Foreach(Kw by 1) { j =>
              Foreach(B by 1) { cc =>
                val prev = mux(i == 0, 0.as[T], out(cc))
                out(cc) = prev + sr(cc) * kh(i, j)
              }
            }
          }

          // TODO: This looks like a random write to SRAM with the current affine analysis...
          Foreach(B by 1){i =>
            lineOut(c + i) = out(i)
          }
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
    val image = (0::R, 0::C){(i,j) => if (j > 3 && i > 3 && j < 11 && i < 11) 256 else 0 }

    val output = convolve(image)

    val gold = (0::R, 0::C){(i,j) => if (j == 7) 1 else 0 }

    printMatrix(image, "Image")
    printMatrix(output, "Output")
  }
}
