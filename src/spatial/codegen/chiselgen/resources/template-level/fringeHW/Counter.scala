package fringe

import chisel3._
import chisel3.util._

/**
 * Counter: 1-dimensional counter. Counts upto 'max', each time incrementing
 * by 'stride', beginning at zero.
 * @param w: Word width
 */
class Counter(val w: Int) extends Module {
  val io = IO(new Bundle {
    val max      = Input(UInt(w.W))
    val stride   = Input(UInt(w.W))
    val out      = Output(UInt(w.W))
    val next     = Output(UInt(w.W))
    val reset  = Input(Bool())
    val enable = Input(Bool())
    val saturate = Input(Bool())
    val done   = Output(Bool())
  })

//  val reg = Module(new FF(w))
  val reg = Module(new FF(w))
  val init = 0.U(w.W)
  reg.io.init := init
  reg.io.enable := io.reset | io.enable

  val count = Cat(0.U(1.W), reg.io.out)
  val newval = count + io.stride
  val isMax = newval >= io.max
  val next = Mux(isMax, Mux(io.saturate, count, init), newval)
  when (io.reset) {
    reg.io.in := init
  } .otherwise {
    reg.io.in := next
  }

  io.out := count
  io.next := next
  io.done := io.enable & isMax
}

class CounterReg(val w: Int) extends Module {
  val io = IO(new Bundle {
    val max      = Input(UInt(w.W))
    val stride   = Input(UInt(w.W))
    val out      = Output(UInt(w.W))
    val reset = Input(Bool())
    val enable = Input(Bool())
    val saturate = Input(Bool())
    val done   = Output(Bool())
  })

  // Register the inputs
  val maxReg = Module(new FF(w))
  maxReg.io.enable := true.B
  maxReg.io.in := io.max
  val max = maxReg.io.out

  val strideReg = Module(new FF(w))
  strideReg.io.enable := true.B
  strideReg.io.in := io.stride
  val stride = strideReg.io.out

  val rstReg = Module(new FF(1))
  rstReg.io.enable := true.B
  rstReg.io.in := io.reset
  val rst = rstReg.io.out

  val enableReg = Module(new FF(1))
  enableReg.io.enable := true.B
  enableReg.io.in := io.enable
  val enable = enableReg.io.out

  val saturateReg = Module(new FF(1))
  saturateReg.io.enable := true.B
  saturateReg.io.in := io.saturate
  val saturate = saturateReg.io.out

  // Instantiate counter
  val counter = Module(new Counter(w))
  counter.io.max := max
  counter.io.stride := stride
  counter.io.enable := enable
  counter.io.reset := rst
  counter.io.enable := enable
  counter.io.saturate := saturate

  // Register outputs
  val outReg = Module(new FF(w))
  outReg.io.enable := true.B
  outReg.io.in := counter.io.out
  io.out := outReg.io.out
  val doneReg = Module(new FF(1))
  doneReg.io.enable := true.B
  doneReg.io.in := counter.io.done
  io.done := doneReg.io.out
}



