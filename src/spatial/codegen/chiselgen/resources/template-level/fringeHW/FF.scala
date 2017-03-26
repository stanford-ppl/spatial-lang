package fringe

import chisel3._

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
  val ff = RegNext(d, io.init)
  when (io.enable) {
    d := io.in
  } .otherwise {
    d := ff
  }
  io.out := ff
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
