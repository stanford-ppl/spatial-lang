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
class FringeDE1SoC(val w: Int, val numArgIns: Int, val numArgOuts: Int, val numMemoryStreams: Int = 1) extends Module {
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
  val fringeCommon = Module(new Fringe(w, numArgIns, numArgOuts, 0))

  // Connect to Avalon Slave
  fringeCommon.reset := reset
  fringeCommon.io.raddr := io.S_AVALON.address
  fringeCommon.io.wen   := ~io.S_AVALON.write_n 

  fringeCommon.io.waddr := io.S_AVALON.address
  fringeCommon.io.wdata := io.S_AVALON.writedata
  io.S_AVALON.readdata := fringeCommon.io.rdata

  io.enable := fringeCommon.io.enable
  fringeCommon.io.done := io.done

  io.argIns := fringeCommon.io.argIns
  fringeCommon.io.argOuts <> io.argOuts
}

object FringeDE1SoC {
  val w = 32
  val numArgIns = 5
  val numArgOuts = 1

  def main(args: Array[String]) {
    Driver.execute(Array[String]("--target-dir", "chisel_out/FringeDE1SoC"), () => new FringeDE1SoC(w, numArgIns, numArgOuts))
  }
}
