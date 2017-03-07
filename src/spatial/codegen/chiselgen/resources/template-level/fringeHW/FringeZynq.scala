package fringe

import chisel3._
import chisel3.util._
import axi4._

/**
 * FringeZynq: Top module for FPGA shell
 * @param w: Word width
 * @param numArgIns: Number of input scalar arguments
 * @param numArgOuts: Number of output scalar arguments
 */
class FringeZynq(val w: Int, val numArgIns: Int, val numArgOuts: Int, val numMemoryStreams: Int = 1) extends Module {
  val numRegs = numArgIns + numArgOuts + 2  // (command, status registers)
  val addrWidth = log2Up(numRegs)

  val commandReg = 0  // TODO: These vals are used in test only, logic below does not use them.
  val statusReg = 1   //       Changing these values alone has no effect on the logic below.

  // Some constants (mostly MAG-related) that will later become module parameters
  val v = 16 // Number of words in the same stream
  val numOutstandingBursts = 1024  // Picked arbitrarily
  val burstSizeBytes = 64
  val d = 16 // FIFO depth: Controls FIFO sizes for address, size, and wdata. Rdata is not buffered

  val axiLiteParams = new AXI4BundleParameters(w, w, 1)
  val axiParams = new AXI4BundleParameters(w, w, 5)
  val io = IO(new Bundle {
    // Host scalar interface
    val S_AXI = Flipped(new AXI4Lite(axiLiteParams))

    // DRAM interface
    val M_AXI = new AXI4Lite(axiParams)

    // Accel Control IO
    val enable = Output(Bool())
    val done = Input(Bool())

    // Accel Scalar IO
    val argIns = Output(Vec(numArgIns, UInt(w.W)))
    val argOuts = Vec(numArgOuts, Flipped(Decoupled((UInt(w.W)))))

    // Accel memory IO
    val memStreams = Vec(numMemoryStreams, new MemoryStream(w, v))
  })

  // Common Fringe
  val fringeCommon = Module(new Fringe(w, numArgIns, numArgOuts, numMemoryStreams))

  // AXI-lite bridge
  val axiLiteBridge = Module(new AXI4LiteToRFBridge(w, w))
  axiLiteBridge.io.S_AXI <> io.S_AXI

  fringeCommon.reset := ~reset
  fringeCommon.io.raddr := axiLiteBridge.io.raddr
  fringeCommon.io.wen   := axiLiteBridge.io.wen
  fringeCommon.io.waddr := axiLiteBridge.io.waddr
  fringeCommon.io.wdata := axiLiteBridge.io.wdata
  axiLiteBridge.io.rdata := fringeCommon.io.rdata

  io.enable := fringeCommon.io.enable
  fringeCommon.io.done := io.done

  io.argIns := fringeCommon.io.argIns
  fringeCommon.io.argOuts <> io.argOuts

  io.memStreams <> fringeCommon.io.memStreams

  // AXI bridge
  val axiBridge = Module(new MAGToAXI4Bridge(w, w))
  axiBridge.io.in <> fringeCommon.io.dram
  io.M_AXI <> axiBridge.io.M_AXI
}

object FringeZynq {
  val w = 32
  val numArgIns = 5
  val numArgOuts = 1

  def main(args: Array[String]) {
    Driver.execute(Array[String]("--target-dir", "chisel_out/FringeZynq"), () => new FringeZynq(w, numArgIns, numArgOuts))
  }
}
