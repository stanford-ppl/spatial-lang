package top

import chisel3._
import chisel3.util._
import fringe._
import accel._
import axi4._
import templates.Utils.log2Up


// import AccelTop
abstract class TopInterface extends Bundle {
  // Host scalar interface
  var raddr = Input(UInt(1.W))
  var wen  = Input(Bool())
  var waddr = Input(UInt(1.W))
  var wdata = Input(Bits(1.W))
  var rdata = Output(Bits(1.W))

}

case class TopParams(
  val addrWidth: Int,
  val dataWidth: Int,
  val v: Int,
  val numArgIns: Int,
  val numArgOuts: Int,
  val loadStreamInfo: List[StreamParInfo],
  val storeStreamInfo: List[StreamParInfo],
  val streamInsInfo: List[StreamParInfo],
  val streamOutsInfo: List[StreamParInfo],
  target: String
)

class VerilatorInterface(p: TopParams) extends TopInterface {
  // Host scalar interface
  raddr = Input(UInt(p.addrWidth.W))
  wen  = Input(Bool())
  waddr = Input(UInt(p.addrWidth.W))
  wdata = Input(Bits(64.W))
  rdata = Output(Bits(64.W))

  // DRAM interface - currently only one stream
  val dram = new DRAMStream(p.dataWidth, p.v)

  // Input streams
  val genericStreamIn = StreamIn(StreamParInfo(32,1))
  val genericStreamOut = StreamOut(StreamParInfo(32,1))

}

class ZynqInterface(p: TopParams) extends TopInterface {
  private val axiLiteParams = new AXI4BundleParameters(p.dataWidth, p.dataWidth, 1)
  private val axiParams = new AXI4BundleParameters(p.dataWidth, 512, 5)
  val S_AXI = Flipped(new AXI4Lite(axiLiteParams))
  val M_AXI = new AXI4Inlined(axiParams)
}

class DE1SoCInterface(p: TopParams) extends TopInterface {
  private val axiLiteParams = new AXI4BundleParameters(16, p.dataWidth, 1)
  val S_AVALON = new AvalonSlave(axiLiteParams)
  val S_STREAM = new AvalonStream(axiLiteParams)
}

class AWSInterface(p: TopParams) extends TopInterface {
  val enable = Input(UInt(p.dataWidth.W))
  val done = Output(UInt(p.dataWidth.W))
  val scalarIns = Input(Vec(p.numArgIns, UInt(64.W)))
  val scalarOuts = Output(Vec(p.numArgOuts, UInt(64.W)))

  // DRAM interface - currently only one stream
  val dram = new DRAMStream(p.dataWidth, p.v)
}

/**
 * Top: Top module including Fringe and Accel
 * @param w: Word width
 * @param numArgIns: Number of input scalar arguments
 * @param numArgOuts: Number of output scalar arguments
 */
class Top(
  val w: Int,
  val numArgIns: Int,
  val numArgOuts: Int,
  val loadStreamInfo: List[StreamParInfo],
  val storeStreamInfo: List[StreamParInfo],
  val streamInsInfo: List[StreamParInfo],
  val streamOutsInfo: List[StreamParInfo],
  target: String = "") extends Module {
  val numRegs = numArgIns + numArgOuts + 2  // (command, status registers)
  val addrWidth = log2Up(numRegs)
  val v = 16
  val topParams = TopParams(addrWidth, w, v, numArgIns, numArgOuts, loadStreamInfo, storeStreamInfo, streamInsInfo, streamOutsInfo, target)

  val io = target match {
    case "verilator"  => IO(new VerilatorInterface(topParams))
    case "vcs"        => IO(new VerilatorInterface(topParams))
    case "aws"        => IO(new AWSInterface(topParams))
    case "zynq"       => IO(new ZynqInterface(topParams))
    case "de1soc"     => IO(new DE1SoCInterface(topParams))
    case _ => throw new Exception(s"Unknown target '$target'")
  }

  // Accel
  val accel = Module(new AccelTop(w, numArgIns, numArgOuts, loadStreamInfo, storeStreamInfo, streamInsInfo, streamOutsInfo))

  target match {
    case "verilator" | "vcs" =>
      // Simulation Fringe
      val blockingDRAMIssue = false
      val fringe = Module(new Fringe(w, numArgIns, numArgOuts, loadStreamInfo, storeStreamInfo, streamInsInfo, streamOutsInfo, blockingDRAMIssue))
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
      fringe.io.memStreams <> accel.io.memStreams
      accel.io.enable := fringe.io.enable
      fringe.io.done := accel.io.done

    case "de1soc" =>
      // DE1SoC Fringe
      val fringe = Module(new FringeDE1SoC(w, numArgIns, numArgOuts, loadStreamInfo, storeStreamInfo, streamInsInfo, streamOutsInfo))
      val topIO = io.asInstanceOf[DE1SoCInterface]

      // Fringe <-> Host connections
      fringe.io.S_AVALON <> topIO.S_AVALON

      // TODO: In DE1SoC, Top takes the streamIn / Out signals and connect these directly to 
      // the resampler. Would be more preferrable if we move these part to fringe...
      // Accel <-> Stream
      accel.io.stream_in_data                 := topIO.S_STREAM.stream_in_data         
      accel.io.stream_in_startofpacket        := topIO.S_STREAM.stream_in_startofpacket
      accel.io.stream_in_endofpacket          := topIO.S_STREAM.stream_in_endofpacket  
      accel.io.stream_in_empty                := topIO.S_STREAM.stream_in_empty        
      accel.io.stream_in_valid                := topIO.S_STREAM.stream_in_valid        
      accel.io.stream_out_ready               := topIO.S_STREAM.stream_out_ready       
       
      // Video Stream Outputs
      topIO.S_STREAM.stream_in_ready          := accel.io.stream_in_ready          
      topIO.S_STREAM.stream_out_data          := accel.io.stream_out_data          
      topIO.S_STREAM.stream_out_startofpacket := accel.io.stream_out_startofpacket 
      topIO.S_STREAM.stream_out_endofpacket   := accel.io.stream_out_endofpacket   
      topIO.S_STREAM.stream_out_empty         := accel.io.stream_out_empty         
      topIO.S_STREAM.stream_out_valid         := accel.io.stream_out_valid         

      accel.io.argIns := fringe.io.argIns
      fringe.io.argOuts.zip(accel.io.argOuts) foreach { case (fringeArgOut, accelArgOut) =>
          fringeArgOut.bits := accelArgOut.bits
          fringeArgOut.valid := 1.U
      }
      accel.io.enable := fringe.io.enable
      fringe.io.done := accel.io.done
      // Top reset is connected to a rst controller on DE1SoC, which converts active low to active high
      accel.reset := reset

//      //
//      // Fringe <-> Peripheral connections
//      fringe.io.genericStreamInTop <> topIO.genericStreamIn
//      fringe.io.genericStreamOutTop <> topIO.genericStreamOut
//
//      // Fringe <-> Accel stream connections
//      accel.io.genericStreams <> fringe.io.genericStreamsAccel
//      // fringe.io.genericStreamsAccel <> accel.io.genericStreams

    case "zynq" =>
      // Zynq Fringe
      val fringe = Module(new FringeZynq(w, numArgIns, numArgOuts, loadStreamInfo, storeStreamInfo, streamInsInfo, streamOutsInfo))
      val topIO = io.asInstanceOf[ZynqInterface]

      // Fringe <-> Host connections
      fringe.io.S_AXI <> topIO.S_AXI

      // Fringe <-> DRAM connections
      topIO.M_AXI <> fringe.io.M_AXI

      accel.io.argIns := fringe.io.argIns
      fringe.io.argOuts.zip(accel.io.argOuts) foreach { case (fringeArgOut, accelArgOut) =>
          fringeArgOut.bits := accelArgOut.bits
          fringeArgOut.valid := 1.U
      }
      accel.io.enable := fringe.io.enable
      fringe.io.done := accel.io.done
      accel.reset := ~reset

    case "aws" =>
      // Simulation Fringe
      val fringe = Module(new Fringe(w, numArgIns, numArgOuts, loadStreamInfo, storeStreamInfo, streamInsInfo, streamOutsInfo))
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
