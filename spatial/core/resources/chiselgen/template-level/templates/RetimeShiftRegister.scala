package templates

import chisel3._
import chisel3.util._
import chisel3.core.IntParam

// This wrapper is needed because we need to wire reset as an input,
// and reset is accessible only from within Modules in Chisel
class RetimeWrapper(val width: Int, val delay: Int) extends Module {
  val io = IO(new Bundle {
    val in = Input(UInt(width.W))
    val out = Output(UInt(width.W))
  })

    val sr = Module(new RetimeShiftRegister(width, delay))
    sr.io.clock := clock
    sr.io.reset := reset.toBool
    sr.io.in := io.in
    io.out := sr.io.out
}
class RetimeShiftRegister(val width: Int, val delay: Int) extends BlackBox(
  Map(
    "WIDTH" -> IntParam(width),
    "STAGES" -> IntParam(delay)
    )
) {
  val io = IO(new Bundle {
    val clock = Input(Clock())
    val reset = Input(Bool())
    val in = Input(UInt(width.W))
    val out = Output(UInt(width.W))
  })
}
