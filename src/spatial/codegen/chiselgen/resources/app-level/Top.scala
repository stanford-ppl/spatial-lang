package top

import chisel3._
import chisel3.util._
import fringe._
import accel._

// import AccelTop
/**
 * Top: Top module including Fringe and Accel
 * @param w: Word width
 * @param numArgIns: Number of input scalar arguments
 * @param numArgOuts: Number of output scalar arguments
 */
class Top(val w: Int, val numArgIns: Int, val numArgOuts: Int, val numMemoryStreams: Int = 1, target: String = "") extends Module {
  val numRegs = numArgIns + numArgOuts + 2  // (command, status registers)
  val addrWidth = log2Up(numRegs)
  val v = 16
  val io = IO(new Bundle {
    // Host scalar interface
    val raddr = Input(UInt(addrWidth.W))
    val wen  = Input(Bool())
    val waddr = Input(UInt(addrWidth.W))
    val wdata = Input(Bits(w.W))
    val rdata = Output(Bits(w.W))

    // Scalar ins and outs - if target wants to directly
    // pass them in
    val enable = Input(UInt(w.W))
    val done = Output(UInt(w.W))
    val scalarIns = Input(Vec(numArgIns, UInt(w.W)))
    val scalarOuts = Output(Vec(numArgOuts, UInt(w.W)))

    // DRAM interface - currently only one stream
    val dram = new DRAMStream(w, v)
  })

  // Fringe
  val fringe = Module(new Fringe(w, numArgIns, numArgOuts, numMemoryStreams))

  // Accel
  val accel = Module(new AccelTop(w, numArgIns, numArgOuts, numMemoryStreams))

  // Fringe <-> Host connections
  fringe.io.raddr := io.raddr
  fringe.io.wen := io.wen
  fringe.io.waddr := io.waddr
  fringe.io.wdata := io.wdata
  io.rdata := fringe.io.rdata

  // Fringe <-> DRAM connections
  io.dram.cmd.bits := fringe.io.dram.cmd.bits
  io.dram.cmd.valid := fringe.io.dram.cmd.valid
  fringe.io.dram.resp.bits := io.dram.resp.bits
  fringe.io.dram.resp.valid := io.dram.resp.valid

  // Accel: Scalar and control connections
  if (target == "aws") {
    accel.io.argIns := io.scalarIns
    io.scalarOuts.zip(accel.io.argOuts) foreach { case (ioOut, accelOut) => ioOut := accelOut.bits }
    accel.io.enable := io.enable
    io.done := accel.io.done
  } else {
    accel.io.argIns := fringe.io.argIns
    fringe.io.argOuts.zip(accel.io.argOuts) foreach { case (fringeArgOut, accelArgOut) =>
        fringeArgOut.bits := accelArgOut.bits
        fringeArgOut.valid := 1.U
    }
    fringe.io.memStreams <> accel.io.memStreams
    accel.io.enable := fringe.io.enable
    fringe.io.done := accel.io.done
  }

  // TBD: Accel <-> Fringe Memory connections
//  for (i <- 0 until numMemoryStreams) {
//    fringe.io.memStreams(i).cmd.bits := accel.io.memStreams(i).cmd.bits
//    fringe.io.memStreams(i).cmd.valid := accel.io.memStreams(i).cmd.valid
//    fringe.io.memStreams(i).wdata.bits := accel.io.memStreams(i).wdata.bits
//    fringe.io.memStreams(i).wdata.valid := accel.io.memStreams(i).wdata.valid
//    accel.io.memStreams(i).rdata.bits := fringe.io.memStreams(i).rdata.bits
//    accel.io.memStreams(i).rdata.valid := fringe.io.memStreams(i).rdata.valid
//  }

}
