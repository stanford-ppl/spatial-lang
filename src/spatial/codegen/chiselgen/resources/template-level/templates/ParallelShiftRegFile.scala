package templates

import chisel3._
import chisel3.util.log2Ceil

// This exposes all registers as output ports now

class ParallelShiftRegFile(val size: Int, val stride: Int) extends Module {

  def this(tuple: (Int, Int)) = this(tuple._1, tuple._2)
  val io = IO(new Bundle { // TODO: follow io.input and io.output convention
    val data_in  = Vec(stride, Input(UInt(32.W)))
    val w_addr   = Input(UInt(log2Ceil(((size+stride-1)/stride)*stride + 1).W))
    val w_en     = Input(UInt(1.W)) // TODO: Bool()
    val shift_en = Input(UInt(1.W)) // TODO: Bool()
    val reset    = Input(UInt(1.W)) // TODO: Bool()
    val data_out = Vec(size, Output(UInt(32.W)))
  })
  
  val size_rounded_up = ((size+stride-1)/stride)*stride // Unlike shift reg, for shift reg file it is user's problem if size does not match (no functionality guarantee)
  val registers = List.fill(size_rounded_up)(Reg(UInt(32.W))) // Note: Can change to use FF template
  
  when(io.reset === 1.U) {
    for (i <- 0 until (size_rounded_up)) {
      registers(i) := 0.U(32.W)
    }
  } .elsewhen(io.shift_en === 1.U) {
    for (i <- 0 until (stride)) {
      registers(i) := io.data_in(i)
    }
    for (i <- stride until (size_rounded_up)) {
      registers(i) := registers(i-stride)
    }
  } .elsewhen(io.w_en === 1.U) {
    for (i <- 0 until (size)) { // Note here we just use size, i.e. if size doesn't match, user will get unexpected answer
      when(i.U === io.w_addr) {
        registers(i) := io.data_in(0)
      }
    }
  }
  
  for (i <- 0 until (size)) {
    io.data_out(i) := registers(size_rounded_up - size + i)
  }
  
}
