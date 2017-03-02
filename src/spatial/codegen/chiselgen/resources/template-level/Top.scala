package top

import chisel3._
import chisel3.util._
import fringe._
import accel._
import axi4._

// import AccelTop
abstract class TopInterface extends Bundle

case class TopParams(
  val addrWidth: Int,
  val dataWidth: Int,
  val v: Int,
  val numArgIns: Int,
  val numArgOuts: Int,
  val numMemoryStreams: Int,
  target: String
)

class VerilatorInterface(p: TopParams) extends TopInterface {
  // Host scalar interface
  val raddr = Input(UInt(p.addrWidth.W))
  val wen  = Input(Bool())
  val waddr = Input(UInt(p.addrWidth.W))
  val wdata = Input(Bits(p.dataWidth.W))
  val rdata = Output(Bits(p.dataWidth.W))

  // DRAM interface - currently only one stream
  val dram = new DRAMStream(p.dataWidth, p.v)
}

class ZynqInterface(p: TopParams) extends TopInterface {
  private val params = new AXI4BundleParameters(p.dataWidth, p.dataWidth, 1)
  val S_AXI = Flipped(new AXI4Lite(params))
  val dram = new DRAMStream(p.dataWidth, p.v)
}

class AWSInterface(p: TopParams) extends TopInterface {
  val enable = Input(UInt(p.dataWidth.W))
  val done = Output(UInt(p.dataWidth.W))
  val scalarIns = Input(Vec(p.numArgIns, UInt(p.dataWidth.W)))
  val scalarOuts = Output(Vec(p.numArgOuts, UInt(p.dataWidth.W)))

  // DRAM interface - currently only one stream
  val dram = new DRAMStream(p.dataWidth, p.v)
}

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
  val topParams = TopParams(addrWidth, w, v, numArgIns, numArgOuts, numMemoryStreams, target)

  val io = target match {
    case "verilator"  => IO(new VerilatorInterface(topParams))
    case "zynq"       => IO(new ZynqInterface(topParams))
    case _ => throw new Exception(s"Unknown target '$target'")
  }

  // Accel
  val accel = Module(new AccelTop(w, numArgIns, numArgOuts, numMemoryStreams))

  target match {
    case "verilator" =>
      // Simulation Fringe
      val fringe = Module(new Fringe(w, numArgIns, numArgOuts, numMemoryStreams))
      val topIO = io.asInstanceOf[VerilatorInterface]

      // Fringe <-> Host connections
      fringe.io.raddr := topIO.raddr
      fringe.io.wen   := topIO.wen
      fringe.io.waddr := topIO.waddr
      fringe.io.wdata := topIO.wdata
      topIO.rdata := fringe.io.rdata

      // Fringe <-> DRAM connections
      topIO.dram <> fringe.io.dram

      accel.io.argIns := fringe.io.argIns
      fringe.io.argOuts.zip(accel.io.argOuts) foreach { case (fringeArgOut, accelArgOut) =>
          fringeArgOut.bits := accelArgOut.bits
          fringeArgOut.valid := 1.U
      }
      accel.io.enable := fringe.io.enable
      fringe.io.done := accel.io.done

    case "zynq" =>
      // Zynq Fringe
      val fringe = Module(new FringeZynq(w, numArgIns, numArgOuts, numMemoryStreams))
      val topIO = io.asInstanceOf[ZynqInterface]

      // Fringe <-> Host connections
      fringe.io.S_AXI <> topIO.S_AXI

      // Fringe <-> DRAM connections
      topIO.dram <> fringe.io.dram

      accel.io.argIns := fringe.io.argIns
      fringe.io.argOuts.zip(accel.io.argOuts) foreach { case (fringeArgOut, accelArgOut) =>
          fringeArgOut.bits := accelArgOut.bits
          fringeArgOut.valid := 1.U
      }
      accel.io.enable := fringe.io.enable
      fringe.io.done := accel.io.done

    case "aws" =>
      // Simulation Fringe
      val fringe = Module(new Fringe(w, numArgIns, numArgOuts, numMemoryStreams))
      val topIO = io.asInstanceOf[AWSInterface]

      // Fringe <-> DRAM connections
      topIO.dram <> fringe.io.dram

      // Accel: Scalar and control connections
      accel.io.argIns := topIO.scalarIns
      topIO.scalarOuts.zip(accel.io.argOuts) foreach { case (ioOut, accelOut) => ioOut := accelOut.bits }
      accel.io.enable := topIO.enable
      topIO.done := accel.io.done
    case _ =>
      throw new Exception(s"Unknown target '$target'")
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
