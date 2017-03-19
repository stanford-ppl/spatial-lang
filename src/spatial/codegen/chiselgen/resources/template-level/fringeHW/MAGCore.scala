package fringe

import chisel3._
import chisel3.util._
import templates.SRFF

/**
 * DRAM Memory Access Generator
 * MAG config register format
 */
case class MAGOpcode() extends Bundle {
  val scatterGather = Bool()
//  val isWr = Bool()

  override def cloneType(): this.type = {
    new MAGOpcode().asInstanceOf[this.type]
  }
}

class Command(w: Int, v: Int) extends Bundle {
  val addr = Vec(v, UInt(w.W))
  val isWr = Bool()
  val size = UInt(w.W)

  override def cloneType(): this.type = {
    new Command(w, v).asInstanceOf[this.type]
  }
}

class MemoryStream(w: Int, v: Int) extends Bundle {
  val cmd = Flipped(Decoupled(new Command(w, v)))
  val wdata = Flipped(Decoupled(Vec(v, UInt(w.W))))
  val rdata = Decoupled(Vec(v, UInt(w.W)))

  override def cloneType(): this.type = {
    new MemoryStream(w, v).asInstanceOf[this.type]
  }
}

class DRAMCommand(w: Int, v: Int) extends Bundle {
  val addr = UInt(w.W)
  val isWr = Bool() // 1
  val tag = UInt(w.W)
  val streamId = UInt(w.W)
  val wdata = Vec(v, UInt(w.W)) // v

  override def cloneType(): this.type = {
    new DRAMCommand(w, v).asInstanceOf[this.type]
  }

}

class DRAMResponse(w: Int, v: Int) extends Bundle {
  val rdata = Vec(v, UInt(w.W)) // v
  val tag = UInt(w.W)
  val streamId = UInt(w.W)

  override def cloneType(): this.type = {
    new DRAMResponse(w, v).asInstanceOf[this.type]
  }

}

class DRAMStream(w: Int, v: Int) extends Bundle {
  val cmd = Decoupled(new DRAMCommand(w, v))
  val resp = Flipped(Decoupled(new DRAMResponse(w, v)))

  override def cloneType(): this.type = {
    new DRAMStream(w, v).asInstanceOf[this.type]
  }

}

class MAGCore(
  val w: Int,
  val d: Int,
  val v: Int,
  val numStreams: Int,
  val numOutstandingBursts: Int,
  val burstSizeBytes: Int
) extends Module { // AbstractMAG(w, d, v, numOutstandingBursts, burstSizeBytes) {

  // The data bus width to DRAM is 1-burst wide
  // While it should be possible in the future to add a write combining buffer
  // and decouple Plasticine's data bus width from the DRAM bus width,
  // this is currently left out as a wishful todo.
  // Check and error out if the data bus width does not match DRAM burst size
  Predef.assert(w/8*v == 64,
  s"Unsupported combination of w=$w and v=$v; data bus width must equal DRAM burst size ($burstSizeBytes bytes)")

  val wordSizeBytes = w/8
  val burstSizeWords = burstSizeBytes / wordSizeBytes
  val tagWidth = log2Up(numStreams)

  val io = IO(new Bundle {
    val app = Vec(numStreams, new MemoryStream(w, v))
    val dram = new DRAMStream(w, v) // Should not have to pass vector width; must be DRAM burst width
    val config = Input(new MAGOpcode())
  })

  def extractBurstAddr(addr: UInt) = {
    val burstOffset = log2Up(burstSizeBytes)
    addr(addr.getWidth-1, burstOffset)
  }

  def extractBurstOffset(addr: UInt) = {
    val burstOffset = log2Up(burstSizeBytes)
    addr(burstOffset-1, 0)
  }

  def extractWordOffset(addr: UInt) = {
    val burstOffset = log2Up(burstSizeBytes)
    val wordOffset = log2Up(w)
    addr(burstOffset, wordOffset)
  }

  // Addr FIFO
  val addrFifo = Module(new FIFOArbiter(w, d, v, numStreams))
  val addrFifoConfig = Wire(new FIFOOpcode(d, v))
  addrFifoConfig.chainRead := 1.U
  addrFifoConfig.chainWrite := ~io.config.scatterGather
  addrFifo.io.config := addrFifoConfig

  addrFifo.io.forceTag.valid := 0.U
  addrFifo.io.enq.zip(io.app) foreach {case (enq, app) => enq := app.cmd.bits.addr }
  addrFifo.io.enqVld.zip(io.app) foreach {case (enqVld, app) => enqVld := app.cmd.valid }

  val burstAddrs = addrFifo.io.deq map { extractBurstAddr(_) }
  val wordOffsets = addrFifo.io.deq map { extractWordOffset(_) }

  // isWr FIFO: Currently a 1-bit FIFO. Ideally we would have a single FIFO
  // for the entire 'cmd' struct, which would require changes to the FIFO and SRAM templates.
  val isWrFifo = Module(new FIFOArbiter(1, d, v, numStreams))
  val isWrFifoConfig = Wire(new FIFOOpcode(d, v))
  isWrFifoConfig.chainRead := 1.U
  isWrFifoConfig.chainWrite := 1.U
  isWrFifo.io.config := isWrFifoConfig

  isWrFifo.io.forceTag.valid := 0.U
  isWrFifo.io.enq.zip(io.app) foreach { case (enq, app) => enq(0) := app.cmd.bits.isWr }
  isWrFifo.io.enqVld.zip(io.app) foreach {case (enqVld, app) => enqVld := app.cmd.valid }

  // Size FIFO
  val sizeFifo = Module(new FIFOArbiter(w, d, v, numStreams))
  val sizeFifoConfig = Wire(new FIFOOpcode(d, v))
  sizeFifoConfig.chainRead := 1.U
  sizeFifoConfig.chainWrite := 1.U
  sizeFifo.io.config := sizeFifoConfig
  sizeFifo.io.forceTag.valid := 0.U
  sizeFifo.io.enq.zip(io.app) foreach { case (enq, app) => enq(0) := app.cmd.bits.size }
  sizeFifo.io.enqVld.zip(io.app) foreach {case (enqVld, app) => enqVld := app.cmd.valid & ~io.config.scatterGather }

//  val sizeIn = Wire(Vec(v, UInt(w.W)))
//  sizeIn.zipWithIndex.foreach { case (wire, i) =>
//    wire := (if (i == 0) io.app.cmd.bits.size else 0.U)
//  }
//  sizeFifo.io.enq := sizeIn
//  sizeFifo.io.enqVld := io.app.cmd.valid & ~io.config.scatterGather
//  sizeFifo.io.enq := Wire(Vec(List.tabulate(v) { i => if (i == 0) io.app.cmd.bits.size else Wire(0.U) }))

  val sizeTop = sizeFifo.io.deq(0)
  val sizeInBursts = extractBurstAddr(sizeTop) + (extractBurstOffset(sizeTop) != 0.U)

  // Data FIFO
  // Deriving the 'enqVld' signal from the other FIFO states is potentially
  // dangerous because there is no guarantee that the data input pins actually contain
  // valid data. The safest approach is to have separate enables for command (addr, size, rdwr)
  // and data.
  val dataFifo = Module(new FIFOArbiter(w, d, v, numStreams))
  val dataFifoConfig = Wire(new FIFOOpcode(d, v))
  dataFifoConfig.chainRead := 0.U
  dataFifoConfig.chainWrite := io.config.scatterGather
  dataFifo.io.config := dataFifoConfig

  val burstCounter = Module(new Counter(w))
  val wrPhase = Module(new SRFF())

  val burstVld = ~templates.Utils.delay(sizeFifo.io.empty,0) & Mux(wrPhase.io.output.data | (isWrFifo.io.deq(0)(0)), ~dataFifo.io.empty, true.B)
  val dramReady = io.dram.cmd.ready

  dataFifo.io.forceTag.bits := addrFifo.io.tag
  dataFifo.io.forceTag.valid := 1.U
  dataFifo.io.enq.zip(io.app) foreach { case (enq, app) => enq := app.wdata.bits }
  dataFifo.io.enqVld.zip(io.app) foreach {case (enqVld, app) => enqVld := app.wdata.valid }

  // Burst offset counter
  burstCounter.io.max := Mux(io.config.scatterGather, 1.U, sizeInBursts)
  burstCounter.io.stride := 1.U
  burstCounter.io.reset := 0.U
  burstCounter.io.enable := Mux(io.config.scatterGather, ~addrFifo.io.empty, burstVld) & dramReady
  burstCounter.io.saturate := 0.U

  // Burst Tag counter
  val burstTagCounter = Module(new Counter(log2Up(numOutstandingBursts+1)))
  burstTagCounter.io.max := numOutstandingBursts.U
  burstTagCounter.io.stride := 1.U
  burstTagCounter.io.reset := 0.U
  burstTagCounter.io.enable := Mux(io.config.scatterGather, ~addrFifo.io.empty, burstVld) & dramReady
  burstCounter.io.saturate := 0.U
  val elementID = burstTagCounter.io.out(log2Up(v)-1, 0)

  // Coalescing cache
//  val ccache = Module(new CoalescingCache(w, d, v))
//  ccache.io.raddr := Cat(io.dram.tagIn, UInt(0, width=log2Up(burstSizeBytes)))
//  ccache.io.readEn := config.scatterGather & io.dram.vldIn
//  ccache.io.waddr := addrFifo.io.deq(0)
//  ccache.io.wen := config.scatterGather & ~addrFifo.io.empty
//  ccache.io.position := elementID
//  ccache.io.wdata := dataFifo.io.deq(0)
//  ccache.io.isScatter := Bool(false) // TODO: Remove this restriction once ready

  addrFifo.io.deqVld := burstCounter.io.done
  isWrFifo.io.deqVld := burstCounter.io.done
  sizeFifo.io.deqVld := burstCounter.io.done
  dataFifo.io.deqVld := burstVld & isWrFifo.io.deq(0) & dramReady // io.config.isWr & burstVld
//  addrFifo.io.deqVld := burstCounter.io.done & ~ccache.io.full
//  dataFifo.io.deqVld := Mux(io.config.scatterGather, burstCounter.io.done & ~ccache.io.full, io.config.isWr & burstVld)


  // Parse Metadata line
  def parseMetadataLine(m: UInt) = {
    // m is burstSizeWords * 8 wide. Each byte 'i' has the following format:
    // | 7 6 5 4     | 3    2   1    0     |
    // | x x x <vld> | <crossbar_config_i> |
    val validAndConfig = List.tabulate(burstSizeWords) { i =>
      parseMetadata(m(i*8+8-1, i*8))
    }

    val valid = validAndConfig.map { _._1 }
    val crossbarConfig = validAndConfig.map { _._2 }
    (valid, crossbarConfig)
  }

  def parseMetadata(m: UInt) = {
    // m is an 8-bit word. Each byte has the following format:
    // | 7 6 5 4     | 3    2   1    0     |
    // | x x x <vld> | <crossbar_config_i> |
    val valid = m(log2Up(burstSizeWords))
    val crossbarConfig = m(log2Up(burstSizeWords)-1, 0)
    (valid, crossbarConfig)
  }

//  val (validMask, crossbarConfig) = parseMetadataLine(ccache.io.rmetaData)

//  val registeredData = Vec(io.dram.resp.bits.rdata.map { d => Reg(UInt(width=w), d) })
//  val registeredVld = Reg(UInt(width=1), io.dram.resp.valid)

  // Gather crossbar
//  val gatherCrossbar = Module(new CrossbarCore(w, v, v))
//  gatherCrossbar.io.ins := registeredData
//  gatherCrossbar.io.config := Vec(crossbarConfig)

  // Gather buffer
//  val gatherBuffer = List.tabulate(burstSizeWords) { i =>
//    val ff = Module(new FF(w))
//    ff.io.control.enable := registeredVld & validMask(i)
//    ff.io.data.in := gatherCrossbar.io.outs(i)
//    ff
//  }
//  val gatherData = Vec.tabulate(burstSizeWords) { gatherBuffer(_).io.data.out }

  // Completion mask
//  val completed = UInt()
//  val completionMask = List.tabulate(burstSizeWords) { i =>
//    val ff = Module(new FF(1))
//    ff.io.control.enable := completed | (validMask(i) & registeredVld)
//    ff.io.data.in := validMask(i)
//    ff
//  }
//  completed := completionMask.map { _.io.data.out }.reduce { _ & _ }

  // FIFO for sizes to be received
//  val receivedFifo = Module(new FIFOCore(w, d, v))
//  val receivedFifoConfig = Wire(new FIFOOpcode(d, v))
//  receivedFifoConfig.chainRead := 1.U
//  receivedFifoConfig.chainWrite := 1.U
//  receivedFifo.io.config := receivedFifoConfig
//
//  receivedFifo.io.enq := Vec(List.tabulate(v) { i => if (i == 0) sizeInBursts else 0.U})
//  receivedFifo.io.enqVld := burstVld & burstCounter.io.out === 0.U
//
//  val maxReceivedSizeBursts = receivedFifo.io.deq(0)
//
//  // Burst received counter
//  val receivedCounter = Module(new Counter(w))
//  receivedCounter.io.max := maxReceivedSizeBursts
//  receivedCounter.io.stride := 1.U
//  receivedCounter.io.reset := 0.U
//  receivedCounter.io.enable := Mux(io.config.scatterGather, 0.U, io.dram.resp.valid)
//  receivedCounter.io.saturate := 0.U
//  receivedFifo.io.deqVld := receivedCounter.io.done

  // Counter chain, where innermost counter is chained to receivedCounter
//  val counterChain = Module(new CounterChainCore(w, numCounters, startDelayWidth, endDelayWidth))
//  val counterEnable = counterChain.io.enable
//  val counterDone = counterChain.io.done

  // Control block for memory unit
//  val controlBox = Module(new CUControlBox(tokenIns.size, inst.control))
//  controlBox.io.config_enable := io.config_enable
//  controlBox.io.config_data := io.config_data
//  controlBox.io.tokenIns := tokenIns
//  counterEnable.zipWithIndex foreach { case (en, i) =>
//    if (i == 0) { // Chain counter 0 to receivedCounter
//      counterEnable(i) := receivedCounter.io.control.done
//    } else {
//      counterEnable(i) := controlBox.io.enable(i)
//    }
//  }
//  controlBox.io.done.zip(counterDone) foreach { case (done, d) => done := d }
//  tokenOuts.zip(controlBox.io.tokenOuts) foreach { case (out, o) => out := o }

  class Tag extends Bundle {
    val streamTag = UInt(tagWidth.W)
    val burstTag = UInt((w-tagWidth).W)
  }

  def getStreamTag(x: UInt) = x(w-1, w-1-tagWidth+1)

  val tagOut = Wire(new Tag())
  tagOut.streamTag := addrFifo.io.tag
  tagOut.burstTag := Mux(io.config.scatterGather, burstAddrs(0), burstTagCounter.io.out)

  io.dram.cmd.bits.addr := Cat((burstAddrs(0) + burstCounter.io.out), 0.U(log2Up(burstSizeBytes).W))
  io.dram.cmd.bits.tag := Cat(tagOut.streamTag, tagOut.burstTag)
  io.dram.cmd.bits.streamId := tagOut.streamTag
  io.dram.cmd.bits.wdata := dataFifo.io.deq
//  io.dram.cmd.valid := Mux(config.scatterGather, ccache.io.miss, burstVld)
  io.dram.cmd.bits.isWr := isWrFifo.io.deq(0)
  wrPhase.io.input.set := isWrFifo.io.deq(0)
  wrPhase.io.input.reset := templates.Utils.delay(burstVld,1)
  io.dram.cmd.valid := burstVld

//  rdata := Mux(io.config.scatterGather, gatherData, io.dram.resp.bits.rdata)
//  vldOut := Mux(io.config.scatterGather, completed, io.dram.vldIn)
  val streamTagFromDRAM = getStreamTag(io.dram.resp.bits.tag)
  io.app foreach { app => app.rdata.bits := io.dram.resp.bits.rdata }
  io.app.zipWithIndex.foreach { case (app, i) => app.rdata.valid := io.dram.resp.valid & streamTagFromDRAM === i.U }
  io.app.zipWithIndex.foreach { case (app, i) =>
    app.cmd.ready := ~addrFifo.io.full(i)
    app.wdata.ready := ~dataFifo.io.full(i)
  }
}

//class MemoryTester (
//  val w: Int,
//  val d: Int,
//  val v: Int,
//  val numOutstandingBursts: Int,
//  val burstSizeBytes: Int,
//  val startDelayWidth: Int,
//  val endDelayWidth: Int,
//  val numCounters: Int,
//  val inst: MAGConfig) extends Module {
//
//  val io = new ConfigInterface {
//    val config_enable = Bool(INPUT)
//    val interconnect = new PlasticineMemoryCmdInterface(w, v)
//    val dram = new DRAMCmdInterface(w, v) // Should not have to pass vector width; must be DRAM burst width
//  }
//
//  /* Data IO aliases to input buses */
//  def addr = io.interconnect.dataIns(0)             // First data bus contains addr / size
//  def size = addr(1)                                // Second word in address bus
//  def wdata = io.interconnect.dataIns(1)   // Second bus corresponds to wdata
//  def rdata = io.interconnect.dataOut
//
//  /* Control IO aliases */
//  def vldIn = io.interconnect.ctrlIns(0)    // Addr/Size valid in
//  val dataVldIn = io.interconnect.ctrlIns(1)         // Wdata valid in
//  def tokenIns = io.interconnect.ctrlIns.drop(2)     // Rest of ctrlIns are called tokenIns
//  def vldOut = io.interconnect.ctrlOuts(0)  // Data valid for read data
//  def rdyOut = io.interconnect.ctrlOuts(1)  // Addr/size FIFO-not-full
//  def dataRdyOut = io.interconnect.ctrlOuts(2)       // Data FIFO-not-full
//  def tokenOuts = io.interconnect.ctrlOuts.drop(3)   // TokenOuts from counters
//
//
//
//  val mu = Module(new MAG(w, d, v, numOutstandingBursts, burstSizeBytes, startDelayWidth, endDelayWidth, numCounters, inst))
//  mu.io.config_enable := io.config_enable
//  mu.io.config_data := io.config_data
////  mu.rdyIn := rdyIn
//  mu.vldIn := vldIn
//  rdyOut := mu.rdyOut
//  vldOut := mu.vldOut
//  mu.addr := addr
//  mu.wdata := wdata
//  mu.dataVldIn := dataVldIn
//  mu.size := size
//  rdata := mu.rdata
//
//  io.dram.addr := mu.io.dram.addr
//  io.dram.wdata := mu.io.dram.wdata
//  io.dram.tagOut := mu.io.dram.tagOut
//  io.dram.vldOut := mu.io.dram.vldOut
//  io.dram.isWr := mu.io.dram.isWr
//
////  val idealMem = Module(new IdealMemory(w, burstSizeBytes))
////  idealMem.io.addr := mu.io.dram.addr
////  idealMem.io.wdata := mu.io.dram.wdata
////  idealMem.io.tagIn := mu.io.dram.tagOut
////  idealMem.io.vldIn := mu.io.dram.vldOut
////  idealMem.io.isWr := mu.io.dram.isWr
////  mu.io.dram.rdata := idealMem.io.rdata
////  mu.io.dram.vldIn := idealMem.io.vldOut
////  mu.io.dram.tagIn := idealMem.io.tagOut
//
//  val DRAMSimulator = Module(new DRAMSimulator(w, burstSizeBytes))
//  DRAMSimulator.io.addr := mu.io.dram.addr
//  DRAMSimulator.io.wdata := mu.io.dram.wdata
//  DRAMSimulator.io.tagIn := mu.io.dram.tagOut
//  DRAMSimulator.io.vldIn := mu.io.dram.vldOut
//  DRAMSimulator.io.isWr := mu.io.dram.isWr
//  mu.io.dram.rdata := DRAMSimulator.io.rdata
//  mu.io.dram.vldIn := DRAMSimulator.io.vldOut
//  mu.io.dram.tagIn := DRAMSimulator.io.tagOut
//}


