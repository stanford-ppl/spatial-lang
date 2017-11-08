package fringe

import chisel3._
import templates._

/**
 * FF: Flip-flop with the ability to set enable and init
 * value as IO
 * @param w: Word width
 */
class FF(val w: Int) extends Module {
  val io = IO(new Bundle {
    val in   = Input(UInt(w.W))
    val init = Input(UInt(w.W))
    val out  = Output(UInt(w.W))
    val enable = Input(Bool())
  })

  val d = Wire(UInt(w.W))
  if (w > 0) {
    val ff = Utils.getRetimed(d, 1)
    when (io.enable) {
      d := io.in
    }.elsewhen (reset.toBool) {
      d := io.init
    } .otherwise {
      d := ff
    }
    io.out := ff    
  } else {
    Console.println("[" + Console.YELLOW + "warn" + Console.RESET + "] FF of width 0 detected!")
    io.out := io.in // Not sure what to connect in this case  
  }
}

class TFF(val w: Int) extends Module {
  val io = new Bundle {
    val out  = Output(UInt(w.W))
    val enable = Input(Bool())
  }

  val d = Wire(UInt(w.W))
  val ff = RegNext(d, 0.U(w.W))
  when (io.enable) {
    d := ~ff
  } .otherwise {
    d := ff
  }
  io.out := ff
}
