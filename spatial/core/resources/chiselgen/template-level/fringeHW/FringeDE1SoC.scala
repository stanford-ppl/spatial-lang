package fringe

import chisel3._
import chisel3.util._
import axi4._
import templates.Utils.log2Up

/**
 * FringeDE1SoC: Top module for FPGA shell
 * @param w: Word width
 * @param numArgIns: Number of input scalar arguments
 * @param numArgOuts: Number of output scalar arguments
 */
class FringeDE1SoC(
  val w: Int,
  val numArgIns: Int,
  val numArgOuts: Int,
  val loadStreamInfo: List[StreamParInfo],
  val storeStreamInfo: List[StreamParInfo],
  val streamInsInfo: List[StreamParInfo],
  val streamOutsInfo: List[StreamParInfo]
) extends Module {
  val numRegs = numArgIns + numArgOuts + 2  // (command, status registers)
  val addrWidth = log2Up(numRegs)

  val commandReg = 0  // TODO: These vals are used in test only, logic below does not use them.
  val statusReg = 1   //       Changing these values alone has no effect on the logic below.

  // Some constants (mostly MAG-related) that will later become module parameters
  val v = 16 // Number of words in the same stream
  val numOutstandingBursts = 1024  // Picked arbitrarily
  val burstSizeBytes = 64
  val d = 16 // FIFO depth: Controls FIFO sizes for address, size, and wdata. Rdata is not buffered

  val axiLiteParams = new AXI4BundleParameters(16, w, 1)
  val io = IO(new Bundle {
    // Host scalar interface
    val S_AVALON = new AvalonSlave(axiLiteParams)

    // Accel Control IO
    val enable = Output(Bool())
    val done = Input(Bool())

    // Accel Scalar IO
    val argIns = Output(Vec(numArgIns, UInt(w.W)))
    val argOuts = Vec(numArgOuts, Flipped(Decoupled((UInt(w.W)))))
  })

  // Common Fringe
  val fringeCommon = Module(new Fringe(w, numArgIns, numArgOuts, loadStreamInfo, storeStreamInfo, streamInsInfo, streamOutsInfo))

  // Connect to Avalon Slave
  // Avalon is using reset and write_n
  fringeCommon.reset := reset
  fringeCommon.io.raddr := io.S_AVALON.address
  fringeCommon.io.wen   := ~io.S_AVALON.write_n & io.S_AVALON.chipselect

  fringeCommon.io.waddr := io.S_AVALON.address
  fringeCommon.io.wdata := io.S_AVALON.writedata
  io.S_AVALON.readdata := fringeCommon.io.rdata

  io.enable := fringeCommon.io.enable
  fringeCommon.io.done := io.done

  if (io.argIns.length > 0) {
    io.argIns := fringeCommon.io.argIns
  }
  
  if (io.argOuts.length > 0) {
    fringeCommon.io.argOuts <> io.argOuts
  }
}

object FringeDE1SoC {
  val w = 32
  val numArgIns = 5
  val numArgOuts = 1
  val loadStreamInfo = List[StreamParInfo]()
  val storeStreamInfo = List[StreamParInfo]()
  val streamInsInfo = List[StreamParInfo]()
  val streamOutsInfo = List[StreamParInfo]()

  def main(args: Array[String]) {
    Driver.execute(Array[String]("--target-dir", "chisel_out/FringeDE1SoC"), () => new FringeDE1SoC(w, numArgIns, numArgOuts, loadStreamInfo, storeStreamInfo, streamInsInfo, streamOutsInfo))
  }
}
