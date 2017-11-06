package fringe

import chisel3._
import chisel3.util._
import axi4._
import templates.Utils.log2Up

/**
 * FringeZynq: Top module for FPGA shell
 * @param w: Word width
 * @param numArgIns: Number of input scalar arguments
 * @param numArgOuts: Number of output scalar arguments
 */
class FringeZynq(
  val w: Int,
  val numArgIns: Int,
  val numArgOuts: Int,
  val numArgIOs: Int,
  val numChannels: Int,
  val numArgInstrs: Int,
  val loadStreamInfo: List[StreamParInfo],
  val storeStreamInfo: List[StreamParInfo],
  val streamInsInfo: List[StreamParInfo],
  val streamOutsInfo: List[StreamParInfo],
  val blockingDRAMIssue: Boolean,
  val axiLiteParams: AXI4BundleParameters,
  val axiParams: AXI4BundleParameters
) extends Module {
  val numRegs = numArgIns + numArgOuts + numArgIOs + 2  // (command, status registers)
  // val addrWidth = log2Up(numRegs)

  val commandReg = 0  // TODO: These vals are used in test only, logic below does not use them.
  val statusReg = 1   //       Changing these values alone has no effect on the logic below.

  // Some constants (mostly MAG-related) that will later become module parameters
  val v = 16 // Number of words in the same stream
  val numOutstandingBursts = 1024  // Picked arbitrarily
  val burstSizeBytes = 64
  val d = 16 // FIFO depth: Controls FIFO sizes for address, size, and wdata. Rdata is not buffered

  val io = IO(new Bundle {
    // Host scalar interface
    val S_AXI = Flipped(new AXI4Lite(axiLiteParams))

    // DRAM interface
    val M_AXI = Vec(numChannels, new AXI4Inlined(axiParams))

    // Accel Control IO
    val enable = Output(Bool())
    val done = Input(Bool())

    // Accel Scalar IO
    val argIns = Output(Vec(numArgIns, UInt(w.W)))
    val argOuts = Vec(numArgOuts, Flipped(Decoupled((UInt(w.W)))))

    // Accel memory IO
    val memStreams = new AppStreams(loadStreamInfo, storeStreamInfo)

    // External enable
    val externalEnable = Input(Bool()) // For AWS, enable comes in as input to top module

    // Accel stream IO
//    val genericStreams = new GenericStreams(streamInsInfo, streamOutsInfo)
  })

  // Common Fringe
  val fringeCommon = Module(new Fringe(w, numArgIns, numArgOuts, numArgIOs, numChannels, numArgInstrs, loadStreamInfo, storeStreamInfo, streamInsInfo, streamOutsInfo, blockingDRAMIssue))

  // AXI-lite bridge
  if (FringeGlobals.target == "zynq" || FringeGlobals.target == "zcu") {
    val datawidth = if (FringeGlobals.target == "zcu") 32 else 32
    val axiLiteBridge = Module(new AXI4LiteToRFBridge(w, datawidth))
    axiLiteBridge.io.S_AXI <> io.S_AXI

    fringeCommon.reset := ~reset.toBool
    fringeCommon.io.raddr := axiLiteBridge.io.raddr
    fringeCommon.io.wen   := axiLiteBridge.io.wen
    fringeCommon.io.waddr := axiLiteBridge.io.waddr
    fringeCommon.io.wdata := axiLiteBridge.io.wdata
    axiLiteBridge.io.rdata := fringeCommon.io.rdata
  }

  fringeCommon.io.aws_top_enable := io.externalEnable

  io.enable := fringeCommon.io.enable
  fringeCommon.io.done := io.done

  io.argIns := fringeCommon.io.argIns
  fringeCommon.io.argOuts <> io.argOuts
  // io.argIOIns := fringeCommon.io.argIOIns
  // fringeCommon.io.argIOOuts <> io.argIOOuts

  io.memStreams <> fringeCommon.io.memStreams

  // AXI bridge
  io.M_AXI.zipWithIndex.foreach { case (maxi, i) =>
    val axiBridge = Module(new MAGToAXI4Bridge(axiParams, fringeCommon.mags(i).tagWidth))
    axiBridge.io.in <> fringeCommon.io.dram(i)
    maxi <> axiBridge.io.M_AXI
  }
}
