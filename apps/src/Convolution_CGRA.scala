package src

import spatial._
import org.virtualized._

object Convolution_CGRA extends SpatialApp {
  import IR._

  val Kh = 3
  val Kw = 3

  @virtualize
  def convolve[T:Staged:Num](image: Array[T], rows: Int, cols: Int): Array[T] = {
    val B = 16 (1 -> 1 -> 16)

    val R = ArgIn[Int]
    val C = ArgIn[Int]
    setArg(R, rows)
    setArg(C, cols)
    val img = DRAM[T](R, C)
    val imgOut = DRAM[T](R, C)

    Accel {
      val lb = LineBuffer[T](Kh, C)
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

      val lineOut = SRAM[T](C)

      val sr = RegFile[T](B + Kw - 1)

      Foreach(0 until R) { r =>

        lb load img(r, 0::C)

        Foreach(0 until C by B) { c =>
          val out = RegFile[T](B)
          Foreach(Kh by 1) { i =>
            sr <<= lb(i, c :: c + B)

            Foreach(Kw by 1) { j =>
              Foreach(B by 1) { cc =>
                val prev = mux(i == 0, 0.as[T], out(cc))
                out(cc) = prev + sr(cc) * k(i, j)
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

    getMem(imgOut)

  }

  @virtualize
  def main() {
    val R = 15
    val C = 15
    val image = Array.tabulate(R){i => Array.tabulate(C){j => if (j > 7) 256 else 0 }}

    val output = convolve(image.flatten, R, C)

    val gold = Array.tabulate(R){i => Array.tabulate(C){j => if (j == 7) 1 else 0 }}

    printArray(image, "Image")
    printArray(output, "Output")
  }
}
