package fringe

import util._
import chisel3._
import chisel3.util._
import templates.SRFF
import templates.Utils.log2Up

class MAGCore(
  val w: Int,
  val d: Int,
  val v: Int,
  val loadStreamInfo: List[StreamParInfo],
  val storeStreamInfo: List[StreamParInfo],
  val numOutstandingBursts: Int,
  val burstSizeBytes: Int,
  val blockingDRAMIssue: Boolean = false
) extends Module { // AbstractMAG(w, d, v, numOutstandingBursts, burstSizeBytes) {

  // The data bus width to DRAM is 1-burst wide
  // While it should be possible in the future to add a write combining buffer
  // and decouple Plasticine's data bus width from the DRAM bus width,
  // this is currently left out as a wishful todo.
  // Check and error out if the data bus width does not match DRAM burst size
  Predef.assert(w/8*v == 64,
  s"Unsupported combination of w=$w and v=$v; data bus width must equal DRAM burst size ($burstSizeBytes bytes)")

  val numStreams = loadStreamInfo.size + storeStreamInfo.size
  val wordSizeBytes = w/8
  val burstSizeWords = burstSizeBytes / wordSizeBytes
  val tagWidth = log2Up(numStreams)

  val io = IO(new Bundle {
//    val app = Vec(numStreams, new MemoryStream(w, v))
    val app = new AppStreams(loadStreamInfo, storeStreamInfo)
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

  val addrWidth = 64
  // Addr FIFO
  val addrFifo = Module(new FIFOArbiter(addrWidth, d, v, numStreams))
  val addrFifoConfig = Wire(new FIFOOpcode(d, v))
  addrFifoConfig.chainRead := 1.U
  addrFifoConfig.chainWrite := ~io.config.scatterGather
  addrFifo.io.config := addrFifoConfig

  addrFifo.io.forceTag.valid := 0.U
  val cmds = io.app.loads.map { _.cmd} ++ io.app.stores.map {_.cmd}
  addrFifo.io.enq.zip(cmds) foreach {case (enq, cmd) => enq(0) := cmd.bits.addr }
  addrFifo.io.enqVld.zip(cmds) foreach {case (enqVld, cmd) => enqVld := cmd.valid }

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
  isWrFifo.io.enq.zip(cmds) foreach { case (enq, cmd) => enq(0) := cmd.bits.isWr }
  isWrFifo.io.enqVld.zip(cmds) foreach {case (enqVld, cmd) => enqVld := cmd.valid }

  // Size FIFO
  val sizeFifo = Module(new FIFOArbiter(w, d, v, numStreams))
  val sizeFifoConfig = Wire(new FIFOOpcode(d, v))
  sizeFifoConfig.chainRead := 1.U
  sizeFifoConfig.chainWrite := 1.U
  sizeFifo.io.config := sizeFifoConfig
  sizeFifo.io.forceTag.valid := 0.U
  sizeFifo.io.enq.zip(cmds) foreach { case (enq, cmd) => enq(0) := cmd.bits.size }
  sizeFifo.io.enqVld.zip(cmds) foreach {case (enqVld, cmd) => enqVld := cmd.valid & ~io.config.scatterGather }

  val sizeTop = sizeFifo.io.deq(0)
  val sizeInBursts = extractBurstAddr(sizeTop) + (extractBurstOffset(sizeTop) != 0.U)

  // WData FIFO
  // Deriving the 'enqVld' signal from the other FIFO states is potentially
  // dangerous because there is no guarantee that the data input pins actually contain
  // valid data. The safest approach is to have separate enables for command (addr, size, rdwr)
  // and data.
  val wins = storeStreamInfo.map {_.w}
  val vins = storeStreamInfo.map {_.v}
  val wdataFifo = Module(new FIFOArbiterWidthConvert(wins, vins, 32, 16, d))
  val wrPhase = Module(new SRFF())


  val burstVld = ~sizeFifo.io.empty & Mux(wrPhase.io.output.data | (~isWrFifo.io.empty & isWrFifo.io.deq(0)(0)), ~wdataFifo.io.empty, true.B)
  val dramReady = io.dram.cmd.ready

  // For blocking calls, create a new 'issued' signal. This wire is high if we have
  // a request in flight
  val issued = Wire(Bool())
  val issuedFF = Module(new FF(1))
  issuedFF.io.init := 0.U
  issuedFF.io.in := Mux(issued, ~io.dram.resp.valid, burstVld & dramReady)
  issuedFF.io.enable := 1.U
  if (blockingDRAMIssue) {
    issued := issuedFF.io.out
  } else { // Non-blocking, this should be a no-op
    issued := false.B
  }

  wdataFifo.io.forceTag.bits := addrFifo.io.tag - loadStreamInfo.size.U
  wdataFifo.io.forceTag.valid := addrFifo.io.tag >= loadStreamInfo.size.U
  wdataFifo.io.enq.zip(io.app.stores.map{_.wdata}) foreach { case (enq, wdata) => enq := wdata.bits }
  wdataFifo.io.enqVld.zip(io.app.stores.map{_.wdata}) foreach {case (enqVld, wdata) => enqVld := wdata.valid }

  // Burst offset counter
  val burstCounter = Module(new Counter(w))
  burstCounter.io.max := Mux(io.config.scatterGather, 1.U, sizeInBursts)
  burstCounter.io.stride := 1.U
  burstCounter.io.reset := 0.U
  burstCounter.io.enable := Mux(io.config.scatterGather, ~addrFifo.io.empty, burstVld) & dramReady & ~issued
  burstCounter.io.saturate := 0.U

  // Burst Tag counter
  val burstTagCounter = Module(new Counter(log2Up(numOutstandingBursts+1)))
  burstTagCounter.io.max := numOutstandingBursts.U
  burstTagCounter.io.stride := 1.U
  burstTagCounter.io.reset := 0.U
  burstTagCounter.io.enable := Mux(io.config.scatterGather, ~addrFifo.io.empty, burstVld) & dramReady & ~issued
  burstCounter.io.saturate := 0.U
  val elementID = burstTagCounter.io.out(log2Up(v)-1, 0)

  // Coalescing cache
//  val ccache = Module(new CoalescingCache(w, d, v))
//  ccache.io.raddr := Cat(io.dram.tagIn, UInt(0, width=log2Up(burstSizeBytes)))
//  ccache.io.readEn := config.scatterGather & io.dram.vldIn
//  ccache.io.waddr := addrFifo.io.deq(0)
//  ccache.io.wen := config.scatterGather & ~addrFifo.io.empty
//  ccache.io.position := elementID
//  ccache.io.wdata := wdataFifo.io.deq(0)
//  ccache.io.isScatter := Bool(false) // TODO: Remove this restriction once ready

  addrFifo.io.deqVld := burstCounter.io.done
  isWrFifo.io.deqVld := burstCounter.io.done
  sizeFifo.io.deqVld := burstCounter.io.done
  wdataFifo.io.deqVld := burstVld & isWrFifo.io.deq(0) & dramReady & ~issued // io.config.isWr & burstVld
//  addrFifo.io.deqVld := burstCounter.io.done & ~ccache.io.full
//  wdataFifo.io.deqVld := Mux(io.config.scatterGather, burstCounter.io.done & ~ccache.io.full, io.config.isWr & burstVld)


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
  io.dram.cmd.bits.wdata := wdataFifo.io.deq
//  io.dram.cmd.valid := Mux(config.scatterGather, ccache.io.miss, burstVld)
  io.dram.cmd.bits.isWr := isWrFifo.io.deq(0)
  wrPhase.io.input.set := (~isWrFifo.io.empty & isWrFifo.io.deq(0))
  wrPhase.io.input.reset := templates.Utils.delay(burstVld,1)
  io.dram.cmd.valid := burstVld & ~issued

  val issuedTag = Wire(UInt(w.W))
  if (blockingDRAMIssue) {
    val issuedTagFF = Module(new FF(w))
    issuedTagFF.io.init := 0.U
    issuedTagFF.io.in := Cat(tagOut.streamTag, tagOut.burstTag)
    issuedTagFF.io.enable := burstVld & ~issued
    issuedTag := issuedTagFF.io.out
  } else {
    issuedTag := 0.U
  }

  val streamTagFromDRAM = if (blockingDRAMIssue) getStreamTag(issuedTag) else getStreamTag(io.dram.resp.bits.tag)

  val rdataFifos = List.tabulate(loadStreamInfo.size) { i =>
    val m = Module(new WidthConverterFIFO(32, io.dram.resp.bits.rdata.size, loadStreamInfo(i).w, loadStreamInfo(i).v, d))
    m.io.enq := io.dram.resp.bits.rdata
    m.io.enqVld := io.dram.resp.valid & streamTagFromDRAM === i.U
    m
  }

  io.app.loads.map{_.rdata}.zip(rdataFifos) foreach { case (rdata, fifo) =>
    rdata.bits := fifo.io.deq
    rdata.valid := ~fifo.io.empty
    fifo.io.deqVld := rdata.ready & ~fifo.io.empty
  }

  cmds.zipWithIndex.foreach { case (cmd, i) =>
    cmd.ready := ~addrFifo.io.full(i)
  }

  io.app.stores.map{_.wdata}.zipWithIndex.foreach { case (wdata, i) =>
    wdata.ready := ~wdataFifo.io.full(i)
  }

  val wrespFifos = List.tabulate(storeStreamInfo.size) { i =>
    val m = Module(new FIFOCounter(d, 1))
    m.io.enq(0) := io.dram.resp.valid
    m.io.enqVld := io.dram.resp.valid & streamTagFromDRAM === (i + loadStreamInfo.size).U
    m
  }

  io.app.stores.map{_.wresp}.zip(wrespFifos) foreach { case (wresp, fifo) =>
    wresp.bits  := fifo.io.deq(0)
    wresp.valid := ~fifo.io.empty
    fifo.io.deqVld := wresp.ready
  }

  io.dram.resp.ready := ~(rdataFifos.map { fifo => fifo.io.full | fifo.io.almostFull }.reduce{_|_})

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


