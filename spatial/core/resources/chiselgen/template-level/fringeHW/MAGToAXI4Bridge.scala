package fringe

import chisel3._
import chisel3.util._
import axi4._

class MAGToAXI4Bridge(val p: AXI4BundleParameters, val tagWidth: Int) extends Module {
  Predef.assert(p.dataBits == 512, s"ERROR: Unsupported data width ${p.dataBits} in MAGToAXI4Bridge")

  val io = IO(new Bundle {
    val in = Flipped(new DRAMStream(32, 16))  // hardcoding stuff here
    val M_AXI = new AXI4Inlined(p)
  })

  val numPipelinedLevels = FringeGlobals.magPipelineDepth

  val size = io.in.cmd.bits.size
  // AR
  val id = Cat(io.in.cmd.bits.streamId, io.in.cmd.bits.tag((p.idBits - tagWidth)-1, 0)) // Fill(p.idBits - tagWidth,  0.U))
//  io.M_AXI.ARID     := 0.U
  io.M_AXI.ARID     := ShiftRegister(id, numPipelinedLevels)
  io.M_AXI.ARUSER   := ShiftRegister(io.in.cmd.bits.streamId, numPipelinedLevels)
  io.M_AXI.ARADDR   := ShiftRegister(io.in.cmd.bits.addr, numPipelinedLevels)
  io.M_AXI.ARLEN    := ShiftRegister(size - 1.U, numPipelinedLevels)
  io.M_AXI.ARSIZE   := 6.U  // 110, for 64-byte burst size
  io.M_AXI.ARBURST  := 1.U  // INCR mode
  io.M_AXI.ARLOCK   := 0.U
  io.M_AXI.ARCACHE  := 3.U  // Xilinx recommended value
  io.M_AXI.ARPROT   := 0.U  // Xilinx recommended value
  io.M_AXI.ARQOS    := 0.U
  io.M_AXI.ARVALID  := ShiftRegister(io.in.cmd.valid & ~io.in.cmd.bits.isWr, numPipelinedLevels)
  io.in.cmd.ready   := ShiftRegister(Mux(io.in.cmd.bits.isWr, io.M_AXI.AWREADY, io.M_AXI.ARREADY), numPipelinedLevels)

  // AW
//  io.M_AXI.AWID     := 0.U
  io.M_AXI.AWID     := ShiftRegister(id, numPipelinedLevels)
  io.M_AXI.AWUSER   := ShiftRegister(io.in.cmd.bits.streamId, numPipelinedLevels)
  io.M_AXI.AWADDR   := ShiftRegister(io.in.cmd.bits.addr, numPipelinedLevels)
  io.M_AXI.AWLEN    := ShiftRegister(size - 1.U, numPipelinedLevels)
  io.M_AXI.AWSIZE   := 6.U  // 110, for 64-byte burst size
  io.M_AXI.AWBURST  := 1.U  // INCR mode
  io.M_AXI.AWLOCK   := 0.U
  io.M_AXI.AWCACHE  := 3.U  // Xilinx recommended value
  io.M_AXI.AWPROT   := 0.U  // Xilinx recommended value
  io.M_AXI.AWQOS    := 0.U
  io.M_AXI.AWVALID  := ShiftRegister(io.in.cmd.valid & io.in.cmd.bits.isWr, numPipelinedLevels)

  // W
  io.M_AXI.WDATA    := ShiftRegister(io.in.wdata.bits.wdata.reverse.reduce{ Cat(_,_) }, numPipelinedLevels)
  io.M_AXI.WSTRB    := Fill(64, 1.U)
  io.M_AXI.WLAST    := ShiftRegister(io.in.wdata.bits.wlast, numPipelinedLevels)
  io.M_AXI.WVALID   := ShiftRegister(io.in.wdata.valid, numPipelinedLevels)
  io.in.wdata.ready := ShiftRegister(io.M_AXI.WREADY, numPipelinedLevels)

  // R
//  io.M_AXI.RID  // Not using this
  val rdataAsVec = Vec(List.tabulate(16) { i =>
      io.M_AXI.RDATA(512 - 1 - i*32, 512 - 1 - i*32 - 31)
  }.reverse)
  io.in.rresp.bits.rdata := ShiftRegister(rdataAsVec, numPipelinedLevels)

//  io.M_AXI.RRESP
//  io.M_AXI.RLAST
////  io.M_AXI.RUSER
  io.M_AXI.RREADY := ShiftRegister(io.in.rresp.ready, numPipelinedLevels)

  // B
  io.M_AXI.BREADY := ShiftRegister(io.in.wresp.ready, numPipelinedLevels)

  // MAG Response channel is currently shared between reads and writes
  io.in.rresp.valid := ShiftRegister(io.M_AXI.RVALID, numPipelinedLevels)
  io.in.wresp.valid := ShiftRegister(io.M_AXI.BVALID, numPipelinedLevels)

  // Construct the response tag, with streamId in the MSB
  io.in.rresp.bits.streamId := ShiftRegister(io.M_AXI.RID, numPipelinedLevels)
  io.in.wresp.bits.streamId := ShiftRegister(io.M_AXI.BID, numPipelinedLevels)

//  io.in.rresp.bits.tag := ShiftRegister(Cat(io.M_AXI.RID(tagWidth-1, 0), Fill(io.in.rresp.bits.tag.getWidth - tagWidth,  0.U)), numPipelinedLevels)
//  io.in.wresp.bits.tag := ShiftRegister(Cat(io.M_AXI.BID(tagWidth-1, 0), Fill(io.in.wresp.bits.tag.getWidth - tagWidth,  0.U)), numPipelinedLevels)
  io.in.rresp.bits.tag := ShiftRegister(Cat(io.M_AXI.RID(p.idBits - 1, p.idBits - tagWidth), Fill(io.in.rresp.bits.tag.getWidth - tagWidth,  0.U)), numPipelinedLevels)
  io.in.wresp.bits.tag := ShiftRegister(Cat(io.M_AXI.BID(p.idBits - 1, p.idBits - tagWidth), Fill(io.in.wresp.bits.tag.getWidth - tagWidth,  0.U)), numPipelinedLevels)
}
