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
      val kh = RegFile[T](Kh, Kw)
      val kv = RegFile[T](Kh, Kw)

      // TODO: Better syntax for initialization of lookup tables
      Pipe {
        // Foreach(Kh by 1, Kw by 1) { (i,j) => 
        //   kh(i,j) = Mux(unwrap(i.to[T] == 0.as[T] && j.to[T] == 0.as[T]), 1, 2).as[T]
        // }
        Pipe{kh(0,0) = 1.as[T]}
        Pipe{kh(1,0) = 2.as[T]}
        Pipe{kh(2,0) = 1.as[T]}
        Pipe{kh(0,1) = 0.as[T]}
        Pipe{kh(1,1) = 0.as[T]}
        Pipe{kh(2,1) = 0.as[T]}
        Pipe{kh(0,2) = -1.as[T]}
        Pipe{kh(1,2) = -2.as[T]}
        Pipe{kh(2,2) = -1.as[T]}

        Pipe{kv(0,0) = 1.as[T]}
        Pipe{kv(0,1) = 2.as[T]}
        Pipe{kv(0,2) = 1.as[T]}
        Pipe{kv(1,0) = 0.as[T]}
        Pipe{kv(1,1) = 0.as[T]}
        Pipe{kv(1,2) = 0.as[T]}
        Pipe{kv(2,0) = -1.as[T]}
        Pipe{kv(2,1) = -2.as[T]}
        Pipe{kv(2,2) = -1.as[T]}
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

        Foreach(0 until C) { c =>
          Foreach(0 until Kh par Kh){i => sr(i, *) <<= lb(i, c) }

          val horz = Reduce(Reg[T])(Kh by 1, Kw by 1){ (i,j) => sr(i,j) * kh(i,j) }{_+_}
          val vert = Reduce(Reg[T])(Kh by 1, Kw by 1){ (i,j) => sr(i,j) * kv(i,j) }{_+_}

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
    // val image = (0::R, 0::C){(i,j) => if (j > 3 && i > 3 && j < 11 && i < 11) 256 else 0 }
    val image = (0::R, 0::C){(i,j) => i*16 + j}

    val output = convolve(image)

    val gold = (0::R, 0::C){(i,j) => if (j == 7) 1 else 0 }

    printMatrix(image, "Image")
    printMatrix(output, "Output")
  }
}
