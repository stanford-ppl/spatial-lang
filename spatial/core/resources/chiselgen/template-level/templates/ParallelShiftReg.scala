package templates

import chisel3._

// This exposes all registers as output ports now

// ENHANCEMENT: support PARALLEL_READS < size, in case horizontal parallelism (selected by DSE) < kernel width
// - Currently only PARALLEL_READS == size is supported, i.e. all ports are exposed. This would support fewer parallel reads.
// - E.g. if shift register contains 1 2 3 4, this supports reading e.g. 1 and 2 in cycle 1, then 3 and 4 in cycle 2, etc
// - Note this requires muxes on output ports (but not a full crossbar) and an extra select input
// - If parallel PARALLEL_READS not divide size, then zero should be returned for leftover ports

class ParallelShiftReg(val size: Int, val stride: Int, val PARALLEL_READS: Int) extends Module {

  def this(tuple: (Int, Int, Int)) = this(tuple._1, tuple._2, tuple._3)
  val io = IO(new Bundle { // TODO: follow io.input and io.output convention
    val data_in  = Vec(stride, Input(UInt(32.W)))
    val w_en     = Input(UInt(1.W)) // TODO: Bool()
    val reset    = Input(UInt(1.W)) // TODO: Bool()
    val data_out = Vec(/*PARALLEL_READS*/size, Output(UInt(32.W)))
  })
  
  val size_rounded_up = ((size+stride-1)/stride)*stride
  val registers = List.fill(size_rounded_up)(Reg(UInt(32.W))) // Note: Can change to use FF template
  
  when(io.reset === 1.U) {
    for (i <- 0 until (size_rounded_up)) {
      registers(i) := 0.U(32.W)
    }
  } .elsewhen(io.w_en === 1.U) {
    for (i <- 0 until (stride)) {
      registers(i) := io.data_in(i)
    }
    for (i <- stride until (size_rounded_up)) {
      registers(i) := registers(i-stride)
    }
  }
  
  for (i <- 0 until (size)) {
    io.data_out(i) := registers(size_rounded_up - size + i)
  }
  
}
