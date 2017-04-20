// See LICENSE.txt for license details.
package templates

import chisel3._

// THIS CLASS IS DEPRECATED!!!!

class Retimer(val length: Int, val bitWidth: Int) extends Module {
  def this(tuple: (Int, Int)) = this(tuple._1, tuple._2)

  val io = IO(new Bundle {
    val input = new Bundle {
      val data      = Input(UInt(bitWidth.W))
      val init      = Input(UInt(bitWidth.W))
      val en        = Input(Bool())
    }
    val output = new Bundle {
      val data = Output(UInt(bitWidth.W))
    }
  })

  if (length == 0) {
    io.output.data := io.input.data
  } else {
    val regs = (0 until length).map { i => RegInit(io.input.init) }
    regs(0) := Mux(io.input.en, io.input.data, regs(0))
    (length-1 until 0 by -1).map { i => 
      regs(i) := Mux(io.input.en, regs(i-1), regs(i))
    }
    io.output.data := regs(length-1)
  }

}

