package fringe

import chisel3._
import chisel3.util._

/**
 * Fringe: Top module for FPGA shell
 * @param w: Word width
 * @param numArgIns: Number of input scalar arguments
 * @param numArgOuts: Number of output scalar arguments
 */
class Fringe(val w: Int, val numArgIns: Int, val numArgOuts: Int, val numMemoryStreams: Int = 1) extends Module {
  val numRegs = numArgIns + numArgOuts + 2  // (command, status registers)
  val addrWidth = log2Up(numRegs)

  val commandReg = 0  // TODO: These vals are used in test only, logic below does not use them.
  val statusReg = 1   //       Changing these values alone has no effect on the logic below.

  // Some constants (mostly MAG-related) that will later become module parameters
  val v = 16 // Number of words in the same stream
  val numOutstandingBursts = 1024  // Picked arbitrarily
  val burstSizeBytes = 64
  val d = 512 // FIFO depth: Controls FIFO sizes for address, size, and wdata. Rdata is not buffered

  val io = IO(new Bundle {
    // Host scalar interface
    val raddr = Input(UInt(addrWidth.W))
    val wen  = Input(Bool())
    val waddr = Input(UInt(addrWidth.W))
    val wdata = Input(Bits(w.W))
    val rdata = Output(Bits(w.W))

    // Accel Control IO
    val enable = Output(Bool())
    val done = Input(Bool())

    // Accel Scalar IO
    val argIns = Output(Vec(numArgIns, UInt(w.W)))
    val argOuts = Vec(numArgOuts, Flipped(Decoupled((UInt(w.W)))))

    // Accel memory IO
    val memStreams = Vec(numMemoryStreams, new MemoryStream(w, v))
    val dram = new DRAMStream(w, v)
  })

  // Scalar, command, and status register file
  val regs = Module(new RegFile(w, numRegs, numArgIns+2, numArgOuts+1))
  regs.io.raddr := io.raddr
  regs.io.waddr := io.waddr
  regs.io.wen := io.wen
  regs.io.wdata := io.wdata
  io.rdata := regs.io.rdata

  val command = regs.io.argIns(0)   // commandReg = first argIn
  val curStatus = regs.io.argIns(1) // current status
  io.enable := command(0) & ~curStatus(0)          // enable = LSB of first argIn
  io.argIns := regs.io.argIns.drop(2) // Accel argIns: Everything except first argIn

  val depulser = Module(new Depulser())
  depulser.io.in := io.done
  val status = Wire(EnqIO(UInt(w.W)))
  status.bits := command & depulser.io.out.asUInt
  status.valid := depulser.io.out
  regs.io.argOuts.zipWithIndex.foreach { case (argOutReg, i) =>
    // Manually assign bits and valid, because direct assignment with :=
    // is causing issues with chisel compilation due to the 'ready' signal
    // which we do not care about
    if (i == 0) { // statusReg: First argOut
      argOutReg.bits := status.bits
      argOutReg.valid := status.valid
    } else {
      argOutReg.bits := io.argOuts(i-1).bits
      argOutReg.valid := io.argOuts(i-1).valid
    }
  }

  // Memory address generator
  val mag = Module(new MAGCore(w, d, v, numMemoryStreams, numOutstandingBursts, burstSizeBytes))
  val magConfig = Wire(new MAGOpcode())
  magConfig.scatterGather := false.B
  mag.io.config := magConfig
  mag.io.app.zip(io.memStreams) foreach { case (magIn, in) => magIn.cmd.bits := in.cmd.bits }
  mag.io.app.zip(io.memStreams) foreach { case (magIn, in) => magIn.cmd.valid := in.cmd.valid }
  mag.io.app.zip(io.memStreams) foreach { case (magIn, in) => magIn.wdata.bits := in.wdata.bits }
  mag.io.app.zip(io.memStreams) foreach { case (magIn, in) => magIn.wdata.valid := in.wdata.valid }
  io.memStreams.zip(mag.io.app) foreach { case (in, magIn) => in.rdata.bits := magIn.rdata.bits }
  io.memStreams.zip(mag.io.app) foreach { case (in, magIn) => in.rdata.valid := magIn.rdata.valid }

  io.dram.cmd.bits := mag.io.dram.cmd.bits
  io.dram.cmd.valid := mag.io.dram.cmd.valid
  mag.io.dram.cmd.ready := io.dram.cmd.ready

  mag.io.dram.resp.bits := io.dram.resp.bits
  mag.io.dram.resp.valid := io.dram.resp.valid
  io.dram.resp.ready := mag.io.dram.cmd.ready
}
