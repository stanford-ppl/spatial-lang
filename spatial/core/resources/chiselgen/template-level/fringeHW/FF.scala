package fringe

import chisel3._
import templates._

class FFType[T<:Data](val t: T) extends Module {
  val io = IO(new Bundle {
    val in   = Input(t.cloneType)
    val init = Input(t.cloneType)
    val out  = Output(t.cloneType)
    val enable = Input(Bool())
  })

  val d = Wire(t.cloneType)
  if (t.getWidth > 0) {
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

class FF(val w: Int) extends Module {
  val io = IO(new Bundle {
    val in   = Input(UInt(w.W))
    val init = Input(UInt(w.W))
    val out  = Output(UInt(w.W))
    val enable = Input(Bool())
  })

  val ff = Module(new FFType(UInt(w.W)))
  io <> ff.io
}
