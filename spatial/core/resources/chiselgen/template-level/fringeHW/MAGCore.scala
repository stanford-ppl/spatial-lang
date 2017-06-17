package fringe

import util._
import chisel3._
import chisel3.util._
import templates.SRFF
import templates.Utils.log2Up
import scala.language.reflectiveCalls

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
    // Debug
    val enable = Input(Bool())

    val dbg = new DebugSignals

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
  addrFifoConfig.chainWrite := 1.U
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

  // isSparse FIFO: Currently a 1-bit FIFO. Ideally we would have a single FIFO
  // for the entire 'cmd' struct, which would require changes to the FIFO and SRAM templates.
  val isSparseFifo = Module(new FIFOArbiter(1, d, v, numStreams))
  val isSparseFifoConfig = Wire(new FIFOOpcode(d, v))
  isSparseFifoConfig.chainRead := 1.U
  isSparseFifoConfig.chainWrite := 1.U
  isSparseFifo.io.config := isSparseFifoConfig

  isSparseFifo.io.forceTag.valid := 0.U
  isSparseFifo.io.enq.zip(cmds) foreach { case (enq, cmd) => enq(0) := cmd.bits.isSparse }
  isSparseFifo.io.enqVld.zip(cmds) foreach {case (enqVld, cmd) => enqVld := cmd.valid }

  val isSparse = isSparseFifo.io.deq(0)(0) & ~isSparseFifo.io.empty
  io.dram.cmd.bits.isSparse := isSparse

  val scatterGather = Wire(Bool())
  scatterGather := io.config.scatterGather
//  val sgPulse = Wire(Bool())
//  scatterGather := isSparseFifo.io.deq(0)(0) & ~isSparseFifo.io.empty
//  val sgPulser = Module(new Pulser())
//  sgPulser.io.in := scatterGather
//  sgPulse := sgPulser.io.out

  // Size FIFO
  val sizeFifo = Module(new FIFOArbiter(w, d, v, numStreams))
  val sizeFifoConfig = Wire(new FIFOOpcode(d, v))
  sizeFifoConfig.chainRead := 1.U
  sizeFifoConfig.chainWrite := 1.U
  sizeFifo.io.config := sizeFifoConfig
  sizeFifo.io.forceTag.valid := 0.U
  sizeFifo.io.enq.zip(cmds) foreach { case (enq, cmd) => enq(0) := cmd.bits.size }
  sizeFifo.io.enqVld.zip(cmds) foreach {case (enqVld, cmd) => enqVld := cmd.valid & ~scatterGather }

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
  wrPhase.io.input.asyn_reset := false.B

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

  val sparseWriteEnable = Wire(Bool())
  sparseWriteEnable := (isSparse & isWrFifo.io.deq(0) & ~addrFifo.io.empty)

  // Burst offset counter
  val burstCounter = Module(new Counter(w))
  burstCounter.io.max := Mux(scatterGather, 1.U, sizeInBursts)
  burstCounter.io.stride := 1.U
  burstCounter.io.reset := 0.U
  burstCounter.io.enable := Mux(sparseWriteEnable, ~addrFifo.io.empty, burstVld) & dramReady & ~issued
//  burstCounter.io.enable := Mux(scatterGather, ~addrFifo.io.empty, burstVld) & dramReady & ~issued
  burstCounter.io.saturate := 0.U

  // Burst Tag counter
  val sgWaitForDRAM = Wire(Bool())
  val burstTagCounter = Module(new Counter(log2Up(numOutstandingBursts+1)))
  burstTagCounter.io.max := Mux(scatterGather, v.U, numOutstandingBursts.U)
  burstTagCounter.io.stride := 1.U
  burstTagCounter.io.reset := 0.U // burstCounter.io.done & ~scatterGather
  burstTagCounter.io.enable := Mux(scatterGather, ~addrFifo.io.empty & ~sgWaitForDRAM, burstVld & dramReady) & ~issued
  burstCounter.io.saturate := 0.U
  val elementID = burstTagCounter.io.out(log2Up(v)-1, 0)

  // Counter to pick correct wdataFifo for a sparse write
  val wdataSelectCounter = Module(new Counter(log2Up(v+1)))
  wdataSelectCounter.io.max := v.U
  wdataSelectCounter.io.stride := 1.U
  wdataSelectCounter.io.reset := ~(isSparse & isWrFifo.io.deq(0))
  wdataSelectCounter.io.enable := sparseWriteEnable & dramReady
  wdataSelectCounter.io.saturate := 0.U

  // Coalescing cache
  val ccache = Module(new CoalescingCache(w, d, v))
  ccache.io.raddr := Cat(io.dram.cmd.bits.tag, 0.U(log2Up(burstSizeBytes).W))
  ccache.io.readEn := scatterGather & io.dram.resp.valid
  ccache.io.waddr := addrFifo.io.deq(0)
  ccache.io.wen := scatterGather & ~addrFifo.io.empty
  ccache.io.position := elementID
  ccache.io.wdata := wdataFifo.io.deq(0)
  ccache.io.isScatter := false.B // TODO: Remove this restriction once ready

  val sgValidDepulser = Module(new Depulser())
  sgValidDepulser.io.in := ccache.io.miss
  sgValidDepulser.io.rst := dramReady
  sgWaitForDRAM := sgValidDepulser.io.out

  isWrFifo.io.deqVld := burstCounter.io.done
  isSparseFifo.io.deqVld := burstCounter.io.done
  sizeFifo.io.deqVld := burstCounter.io.done
  addrFifo.io.deqVld := Mux(scatterGather, ~ccache.io.full & ~sgWaitForDRAM, burstCounter.io.done)
  wdataFifo.io.deqVld := Mux(scatterGather, burstCounter.io.done & ~ccache.io.full,
                             burstVld & isWrFifo.io.deq(0) & dramReady & ~issued)

  // Parse Metadata line
  def parseMetadataLine(m: Vec[UInt]) = {
    // m is burstSizeWords * 8 wide. Each byte 'i' has the following format:
    // | 7 6 5 4     | 3    2   1    0     |
    // | x x x <vld> | <crossbar_config_i> |
    val validAndConfig = List.tabulate(burstSizeWords) { i =>
      parseMetadata(m(i))
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

  val (validMask, crossbarSelect) = parseMetadataLine(ccache.io.rmetaData)

  val registeredData = Vec(io.dram.resp.bits.rdata.map { d => RegNext(d, 0.U) })
  val registeredVld = RegNext(io.dram.resp.valid, 0.U)

  // Gather crossbar
  val switchParams = SwitchParams(v, v)
  val crossbarConfig = Wire(CrossbarConfig(switchParams))
  crossbarConfig.outSelect = Vec(crossbarSelect)
  val gatherCrossbar = Module(new CrossbarCore(UInt(w.W), switchParams))
  gatherCrossbar.io.ins := registeredData
  gatherCrossbar.io.config := crossbarConfig

  // Gather buffer
  val gatherBuffer = List.tabulate(burstSizeWords) { i =>
    val ff = Module(new FF(w))
    ff.io.init := 0.U
    ff.io.enable := registeredVld & validMask(i)
    ff.io.in := gatherCrossbar.io.outs(i)
    ff
  }
  val gatherData = Vec(List.tabulate(burstSizeWords) { gatherBuffer(_).io.out })

  // Completion mask
  val completed = Wire(UInt())
  val completionMask = List.tabulate(burstSizeWords) { i =>
    val ff = Module(new FF(1))
    ff.io.init := 0.U
    ff.io.enable := scatterGather & (completed | (validMask(i) & registeredVld))
    ff.io.in := validMask(i)
    ff
  }
  completed := completionMask.map { _.io.out }.reduce { _ & _ }

  class Tag extends Bundle {
    val streamTag = UInt(tagWidth.W)
    val burstTag = UInt((w-tagWidth).W)
  }

  def getStreamTag(x: UInt) = x(w-1, w-1-tagWidth+1)

  val tagOut = Wire(new Tag())
  tagOut.streamTag := addrFifo.io.tag
  tagOut.burstTag := Mux(scatterGather, burstAddrs(0), burstTagCounter.io.out)

  val wdata = Vec(List.tabulate(v) { i =>
    if (i == 0) {
      val mux = Module(new MuxNType(UInt(w.W), v))
      mux.io.ins := wdataFifo.io.deq
      mux.io.sel := wdataSelectCounter.io.out
      mux.io.out
    } else wdataFifo.io.deq(i)
  })

  io.dram.cmd.bits.addr := Cat((burstAddrs(0) + burstCounter.io.out), 0.U(log2Up(burstSizeBytes).W))
  io.dram.cmd.bits.rawAddr := addrFifo.io.deq(0)
  io.dram.cmd.bits.tag := Cat(tagOut.streamTag, tagOut.burstTag)
  io.dram.cmd.bits.streamId := tagOut.streamTag
  io.dram.cmd.bits.wdata := wdata
  io.dram.cmd.bits.isWr := isWrFifo.io.deq(0)

  val wasSparseWren = Module(new SRFF()) // Hacky way to allow wrPhase to die after sparse write
  wasSparseWren.io.input.set := sparseWriteEnable
  wasSparseWren.io.input.reset := ~sparseWriteEnable
  wasSparseWren.io.input.asyn_reset := false.B
  
  wrPhase.io.input.set := (~isWrFifo.io.empty & isWrFifo.io.deq(0))
  wrPhase.io.input.reset := templates.Utils.delay(burstVld | wasSparseWren.io.output.data,1)
  val dramCmdValid = Mux(scatterGather, ccache.io.miss, burstVld & ~issued)
  io.dram.cmd.valid := dramCmdValid

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

  val respValid = io.dram.resp.valid // & io.enable
  val streamTagFromDRAM = if (blockingDRAMIssue) getStreamTag(issuedTag) else getStreamTag(io.dram.resp.bits.tag)

  val rdataFifos = List.tabulate(loadStreamInfo.size) { i =>
    val m = Module(new WidthConverterFIFO(32, io.dram.resp.bits.rdata.size, loadStreamInfo(i).w, loadStreamInfo(i).v, d))
    m.io.enq := io.dram.resp.bits.rdata
    m.io.enqVld := respValid & (streamTagFromDRAM === i.U)
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
    m.io.enq(0) := respValid
    m.io.enqVld := respValid & (streamTagFromDRAM === (i + loadStreamInfo.size).U)
    m
  }

  io.app.stores.map{_.wresp}.zip(wrespFifos) foreach { case (wresp, fifo) =>
    wresp.bits  := fifo.io.deq(0)
    wresp.valid := ~fifo.io.empty
    fifo.io.deqVld := wresp.ready
  }

  if (rdataFifos.length > 0) {
    io.dram.resp.ready := ~(rdataFifos.map { fifo => fifo.io.full | fifo.io.almostFull }.reduce{_|_})
  } else {
    io.dram.resp.ready := true.B
  }

  // All debug counters
  val enableCounter = Module(new Counter(32))
  enableCounter.io.reset := 0.U
  enableCounter.io.saturate := 0.U
  enableCounter.io.max := 100000000.U
  enableCounter.io.stride := 1.U
  enableCounter.io.enable := io.enable
  io.dbg.num_enable := enableCounter.io.out

  val cmdValidCtr = Module(new Counter(32))
  cmdValidCtr.io.reset := 0.U
  cmdValidCtr.io.saturate := 0.U
  cmdValidCtr.io.max := 100000000.U
  cmdValidCtr.io.stride := 1.U
  cmdValidCtr.io.enable := dramCmdValid
  io.dbg.num_cmd_valid := cmdValidCtr.io.out

  val cmdValidEnableCtr = Module(new Counter(32))
  cmdValidEnableCtr.io.reset := 0.U
  cmdValidEnableCtr.io.saturate := 0.U
  cmdValidEnableCtr.io.max := 100000000.U
  cmdValidEnableCtr.io.stride := 1.U
  cmdValidEnableCtr.io.enable := dramCmdValid & io.enable
  io.dbg.num_cmd_valid_enable := cmdValidEnableCtr.io.out

  val numReadyHighCtr = Module(new Counter(32))
  numReadyHighCtr.io.reset := 0.U
  numReadyHighCtr.io.saturate := 0.U
  numReadyHighCtr.io.max := 100000000.U
  numReadyHighCtr.io.stride := 1.U
  numReadyHighCtr.io.enable := io.dram.cmd.ready
  io.dbg.num_cmd_ready := numReadyHighCtr.io.out

  val numReadyAndEnableHighCtr = Module(new Counter(32))
  numReadyAndEnableHighCtr.io.reset := 0.U
  numReadyAndEnableHighCtr.io.saturate := 0.U
  numReadyAndEnableHighCtr.io.max := 100000000.U
  numReadyAndEnableHighCtr.io.stride := 1.U
  numReadyAndEnableHighCtr.io.enable := io.dram.cmd.ready & io.enable
  io.dbg.num_cmd_ready_enable := numReadyAndEnableHighCtr.io.out

  val numRespHighCtr = Module(new Counter(32))
  numRespHighCtr.io.reset := 0.U
  numRespHighCtr.io.saturate := 0.U
  numRespHighCtr.io.max := 100000000.U
  numRespHighCtr.io.stride := 1.U
  numRespHighCtr.io.enable := io.dram.resp.valid
  io.dbg.num_resp_valid := numRespHighCtr.io.out

  val numRespAndEnableHighCtr = Module(new Counter(32))
  numRespAndEnableHighCtr.io.reset := 0.U
  numRespAndEnableHighCtr.io.saturate := 0.U
  numRespAndEnableHighCtr.io.max := 100000000.U
  numRespAndEnableHighCtr.io.stride := 1.U
  numRespAndEnableHighCtr.io.enable := io.dram.resp.valid & io.enable
  io.dbg.num_resp_valid_enable := numRespAndEnableHighCtr.io.out

  val rdataEnqCtr = Module(new Counter(32))
  rdataEnqCtr.io.reset := 0.U
  rdataEnqCtr.io.saturate := 0.U
  rdataEnqCtr.io.max := 100000000.U
  rdataEnqCtr.io.stride := 1.U
  rdataEnqCtr.io.enable := respValid & (streamTagFromDRAM === 0.U)
  io.dbg.num_rdata_enq := rdataEnqCtr.io.out

  val rdataDeqCtr = Module(new Counter(32))
  rdataDeqCtr.io.reset := 0.U
  rdataDeqCtr.io.saturate := 0.U
  rdataDeqCtr.io.max := 100000000.U
  rdataDeqCtr.io.stride := 1.U
  rdataDeqCtr.io.enable := io.app.loads(0).rdata.ready & ~rdataFifos(0).io.empty
  io.dbg.num_rdata_deq := rdataDeqCtr.io.out

  val wdataEnqCtr = Module(new Counter(32))
  wdataEnqCtr.io.reset := 0.U
  wdataEnqCtr.io.saturate := 0.U
  wdataEnqCtr.io.max := 100000000.U
  wdataEnqCtr.io.stride := 1.U
  wdataEnqCtr.io.enable := io.app.stores(0).wdata.valid

  val wdataDeqCtr = Module(new Counter(32))
  wdataDeqCtr.io.reset := 0.U
  wdataDeqCtr.io.saturate := 0.U
  wdataDeqCtr.io.max := 100000000.U
  wdataDeqCtr.io.stride := 1.U
  wdataDeqCtr.io.enable := burstVld & isWrFifo.io.deq(0) & dramReady & ~issued

  val appRdataReadyCtr = Module(new Counter(32))
  appRdataReadyCtr.io.reset := 0.U
  appRdataReadyCtr.io.saturate := 0.U
  appRdataReadyCtr.io.max := 100000000.U
  appRdataReadyCtr.io.stride := 1.U
  appRdataReadyCtr.io.enable := io.app.loads(0).rdata.ready
  io.dbg.num_app_rdata_ready := appRdataReadyCtr.io.out

  def getFF[T<:Data](sig: T, en: UInt) = {
    val in = sig match {
      case v: Vec[UInt] => v.reverse.reduce { Cat(_,_) }
      case u: UInt => u
    }

    val ff = Module(new FF(sig.getWidth))
    ff.io.init := 15162342.U  // Numbers from "LOST" without first two numbers
    ff.io.in := in
    ff.io.enable := en
    ff.io.out
  }

//  // rdata enq values
//  io.dbg.rdata_enq0_0 := getFF(io.dram.resp.bits.rdata(0), respValid & (streamTagFromDRAM === 0.U) & (rdataEnqCtr.io.out === 0.U))
//  io.dbg.rdata_enq0_1 := getFF(io.dram.resp.bits.rdata(1), respValid & (streamTagFromDRAM === 0.U) & (rdataEnqCtr.io.out === 0.U))
//  io.dbg.rdata_enq0_15 := getFF(io.dram.resp.bits.rdata(15), respValid & (streamTagFromDRAM === 0.U) & (rdataEnqCtr.io.out === 0.U))
//
//  io.dbg.rdata_enq1_0 := getFF(io.dram.resp.bits.rdata(0), respValid & (streamTagFromDRAM === 0.U) & (rdataEnqCtr.io.out === 1.U))
//  io.dbg.rdata_enq1_1 := getFF(io.dram.resp.bits.rdata(1), respValid & (streamTagFromDRAM === 0.U) & (rdataEnqCtr.io.out === 1.U))
//  io.dbg.rdata_enq1_15 := getFF(io.dram.resp.bits.rdata(15), respValid & (streamTagFromDRAM === 0.U) & (rdataEnqCtr.io.out === 1.U))
//
//
//  // rdata deq values
//  io.dbg.rdata_deq0_0   := getFF(rdataFifos(0).io.deq(0), io.app.loads(0).rdata.ready & ~rdataFifos(0).io.empty & rdataDeqCtr.io.out === 0.U)
//  io.dbg.rdata_deq0_1   := getFF(rdataFifos(0).io.deq(1), io.app.loads(0).rdata.ready & ~rdataFifos(0).io.empty & rdataDeqCtr.io.out === 0.U)
//  io.dbg.rdata_deq0_15   := getFF(rdataFifos(0).io.deq(15), io.app.loads(0).rdata.ready & ~rdataFifos(0).io.empty & rdataDeqCtr.io.out === 0.U)
//
//  io.dbg.rdata_deq1_0   := getFF(rdataFifos(0).io.deq(0), io.app.loads(0).rdata.ready & ~rdataFifos(0).io.empty & rdataDeqCtr.io.out === 1.U)
//  io.dbg.rdata_deq1_1   := getFF(rdataFifos(0).io.deq(1), io.app.loads(0).rdata.ready & ~rdataFifos(0).io.empty & rdataDeqCtr.io.out === 1.U)
//  io.dbg.rdata_deq1_15   := getFF(rdataFifos(0).io.deq(15), io.app.loads(0).rdata.ready & ~rdataFifos(0).io.empty & rdataDeqCtr.io.out === 1.U)
//
//
//  // wdata enq values
//  io.dbg.wdata_enq0_0 := getFF(wdataFifo.io.enq(0)(0), io.app.stores(0).wdata.valid & (wdataEnqCtr.io.out === 0.U))
//  io.dbg.wdata_enq0_1 := getFF(wdataFifo.io.enq(0)(1), io.app.stores(0).wdata.valid & (wdataEnqCtr.io.out === 0.U))
//  io.dbg.wdata_enq0_15 := getFF(wdataFifo.io.enq(0)(15), io.app.stores(0).wdata.valid & (wdataEnqCtr.io.out === 0.U))
//  io.dbg.wdata_enq1_0 := getFF(wdataFifo.io.enq(0)(0), io.app.stores(0).wdata.valid & (wdataEnqCtr.io.out === 1.U))
//  io.dbg.wdata_enq1_1 := getFF(wdataFifo.io.enq(0)(1), io.app.stores(0).wdata.valid & (wdataEnqCtr.io.out === 1.U))
//  io.dbg.wdata_enq1_15 := getFF(wdataFifo.io.enq(0)(15), io.app.stores(0).wdata.valid & (wdataEnqCtr.io.out === 1.U))
//
//  // wdata deq values
//  io.dbg.wdata_deq0_0 := getFF(wdataFifo.io.deq(0), burstVld & isWrFifo.io.deq(0) & dramReady & ~issued & (wdataDeqCtr.io.out === 0.U))
//  io.dbg.wdata_deq0_1 := getFF(wdataFifo.io.deq(1), burstVld & isWrFifo.io.deq(0) & dramReady & ~issued & (wdataDeqCtr.io.out === 0.U))
//  io.dbg.wdata_deq0_15 := getFF(wdataFifo.io.deq(15), burstVld & isWrFifo.io.deq(0) & dramReady & ~issued & (wdataDeqCtr.io.out === 0.U))
//  io.dbg.wdata_deq1_0 := getFF(wdataFifo.io.deq(0), burstVld & isWrFifo.io.deq(0) & dramReady & ~issued & (wdataDeqCtr.io.out === 1.U))
//  io.dbg.wdata_deq1_1 := getFF(wdataFifo.io.deq(1), burstVld & isWrFifo.io.deq(0) & dramReady & ~issued & (wdataDeqCtr.io.out === 1.U))
//  io.dbg.wdata_deq1_15 := getFF(wdataFifo.io.deq(15), burstVld & isWrFifo.io.deq(0) & dramReady & ~issued & (wdataDeqCtr.io.out === 1.U))

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


