package example

import chisel3._

/**
 * UpDownCtr: 1-dimensional counter. Counts upto 'max', each time incrementing
 * by 'stride', beginning at zero.
 * @param w: Word width
 */
class UpDownCtr(val w: Int) extends Module {
  val io = IO(new Bundle {
    val initval  = Input(UInt(w.W))
    val max      = Input(UInt(w.W))
    val strideInc   = Input(UInt(w.W))
    val strideDec   = Input(UInt(w.W))
    val initAtConfig     = Input(Bool())
    val init     = Input(Bool())
    val inc      = Input(Bool())
    val dec      = Input(Bool())
    val gtz      = Output(Bool())  // greater-than-zero
    val isMax    = Output(Bool())
    val out      = Output(UInt(w.W))
  })

//  val reg = Module(new FF(w))
  val reg = Module(new FF(w))
  val configInit = Mux(io.initAtConfig, io.initval, UInt(0, w.W))
  reg.io.init := configInit

  // If inc and dec go high at the same time, the counter
  // should be unaffected. Catch that with an xor
  reg.io.enable := (io.inc ^ io.dec) | io.init

  val incval = reg.io.out + io.strideInc
  val decval = reg.io.out - io.strideDec

  io.isMax := incval > io.max
  reg.io.in := Mux (io.init, io.initval, Mux(io.inc, incval, decval))
  io.gtz := (reg.io.out > UInt(0))
  io.out := reg.io.out
}


