// See LICENSE.txt for license details.
package templates

import chisel3._

class Delayer(val length: Int) extends Module {
  val io = IO(new Bundle {
    val input = new Bundle {
      val data      = Input(UInt(32.W))
    }
    val output = new Bundle {
      val data = Output(UInt(32.W))
    }
  })

  if (length == 0) {
    io.output.data := io.input.data
  } else {
    val regs = (0 until length).map { i => Reg(init = 0.U) }
    regs(0) := io.input.data
    (length-1 until 0 by -1).map { i => 
      regs(i) := regs(i-1)
    }
    io.output.data := regs(length-1)
  }

}

