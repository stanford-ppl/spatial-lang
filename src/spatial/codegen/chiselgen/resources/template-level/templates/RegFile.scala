package templates

import chisel3._
import chisel3.util.log2Up

// 1D Reg File

class RegFile(val size: Int) extends Module {

  // def this(tuple: (Int)) = this(tuple._1)
  val io = IO(new Bundle { // TODO: follow io.input and io.output convention
    val data_in  = Input(UInt(32.W))
    val w_addr   = Input(UInt(log2Up(size+1).W))
    val w_en     = Input(UInt(1.W)) // TODO: Bool()
    val reset    = Input(UInt(1.W)) // TODO: Bool()
    val data_out = Vec(size, Output(UInt(32.W)))
  })
  
  val registers = List.fill(size)(Reg(UInt(32.W))) // Note: Can change to use FF template
  
  when(io.reset === 1.U) {
    for (i <- 0 until (size)) {
      registers(i) := 0.U(32.W)
    }
  } .elsewhen(io.w_en === 1.U) {
    // registers(io.w_addr) := io.data_in
    // /*
    for (i <- 0 until (size)) {
      when(i.U === io.w_addr) {
        registers(i) := io.data_in
      }
    }
    // */
  }
  
  for (i <- 0 until (size)) {
    io.data_out(i) := registers(i)
  }
  
}
