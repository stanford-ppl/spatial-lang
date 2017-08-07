package fringe

import util._
import chisel3._
import chisel3.util._
import templates.SRFF
import templates.Utils.log2Up
import scala.language.reflectiveCalls
import scala.collection.mutable.ListBuffer
import java.io.{File, PrintWriter}

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

  val numRdataDebug = 3
  val numRdataWordsDebug = 16
  val numWdataDebug = 3
  val numWdataWordsDebug = 16
  val numDebugs = 224

  // The data bus width to DRAM is 1-burst wide
  // While it should be possible in the future to add a write combining buffer
  // and decouple Plasticine's data bus width from the DRAM bus width,
  // this is currently left out as a wishful todo.
  // Check and error out if the data bus width does not match DRAM burst size
  Predef.assert(w/8*v == 64,
  s"Unsupported combination of w=$w and v=$v; data bus width must equal DRAM burst size ($burstSizeBytes bytes)")

  // Enabling hardware asserts on aws-sim is causing a segfault in xsim
  // We can safely disable them for aws-sim, as we should be able to catch violations with vcs simulation
  val enableHwAsserts = FringeGlobals.target match {
    case "aws" | "aws-sim" => false
    case _ => true
  }

  val numStreams = loadStreamInfo.size + storeStreamInfo.size
  val wordSizeBytes = w/8
  val burstSizeWords = burstSizeBytes / wordSizeBytes
  val tagWidth = log2Up(numStreams)

  val io = IO(new Bundle {
    // Debug
    val enable = Input(Bool())

    val dbg = new DebugSignals
    val debugSignals = Output(Vec(numDebugs, (UInt(w.W))))

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
  val addrFifo = Module(new FIFOArbiter(addrWidth, d, 1, numStreams))
  val addrFifoConfig = Wire(new FIFOOpcode(d, 1))
  addrFifoConfig.chainRead := 1.U
  addrFifoConfig.chainWrite := 1.U
  addrFifo.io.config := addrFifoConfig

  addrFifo.io.forceTag.valid := 0.U
  val cmds = io.app.loads.map { _.cmd} ++ io.app.stores.map {_.cmd}
  addrFifo.io.enq.zip(cmds) foreach {case (enq, cmd) => enq(0) := cmd.bits.addr }
  addrFifo.io.enqVld.zip(cmds) foreach {case (enqVld, cmd) => enqVld := cmd.valid }


  // Debug assertions
  if (enableHwAsserts) {
    for (i <- 0 until numStreams) {
      val str3 = s"ERROR: addrFifo $i enqVld is high when not enabled!"
      assert((io.enable & cmds(i).valid) | (io.enable & ~cmds(i).valid) | (~io.enable & ~cmds(i).valid), str3)
    }
  }

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
  val sizeFifo = Module(new FIFOArbiter(w, d, 1, numStreams))
  val sizeFifoConfig = Wire(new FIFOOpcode(d, 1))
  sizeFifoConfig.chainRead := 1.U
  sizeFifoConfig.chainWrite := 1.U
  sizeFifo.io.config := sizeFifoConfig
  sizeFifo.io.forceTag.valid := 0.U
  sizeFifo.io.enq.zip(cmds) foreach { case (enq, cmd) => enq(0) := cmd.bits.size }
  sizeFifo.io.enqVld.zip(cmds) foreach {case (enqVld, cmd) => enqVld := cmd.valid & ~scatterGather }

  val sizeTop = sizeFifo.io.deq(0)
  val sizeInBursts = extractBurstAddr(sizeTop) + (extractBurstOffset(sizeTop) != 0.U)
  io.dram.cmd.bits.size := sizeInBursts

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

  // For blocking calls, create a new 'issued' signal.
  // This wire is high if we have a request in flight
  val issued = Wire(Bool())

  // burstVld is high when the output DRAM command is valid
  val burstVld = Wire(Bool())

  val writeCmd = ~sizeFifo.io.empty & (wrPhase.io.output.data | (~isWrFifo.io.empty & isWrFifo.io.deq(0)(0))) // & ~issued
  val wdataValid = writeCmd & ~wdataFifo.io.empty

//  val burstVld = io.enable & ~sizeFifo.io.empty & Mux(wrPhase.io.output.data | (~isWrFifo.io.empty & isWrFifo.io.deq(0)(0)), ~wdataFifo.io.empty, true.B)

  val dramReady = io.dram.cmd.ready
  val wdataReady = io.dram.wdata.ready

  val respValid = io.dram.rresp.valid | io.dram.wresp.valid // & io.enable

  wdataFifo.io.forceTag.bits := addrFifo.io.tag - loadStreamInfo.size.U
  wdataFifo.io.forceTag.valid := addrFifo.io.tag >= loadStreamInfo.size.U
  wdataFifo.io.enq.zip(io.app.stores.map{_.wdata}) foreach { case (enq, wdata) => enq := wdata.bits }
  wdataFifo.io.enqVld.zip(io.app.stores.map{_.wdata}) foreach {case (enqVld, wdata) => enqVld := wdata.valid }
  val wdataFifoSize = wdataFifo.io.fifoSize

  val sparseWriteEnable = Wire(Bool())
  sparseWriteEnable := (isSparse & isWrFifo.io.deq(0) & ~addrFifo.io.empty)

  val issuedFF = Module(new FF(1))
  issuedFF.io.init := 0.U
  issuedFF.io.in := Mux(issued, ~respValid, burstVld & dramReady)
  issuedFF.io.enable := 1.U
  if (blockingDRAMIssue) {
    issued := issuedFF.io.out
  } else { // Non-blocking, this should be a no-op
    issued := false.B
  }

  // Burst offset counter
  val burstCounter = Module(new Counter(w))
  burstCounter.io.max := Mux(writeCmd, sizeInBursts, 1.U) // Mux(scatterGather, 1.U, sizeInBursts)
  burstCounter.io.stride := 1.U
  burstCounter.io.reset := 0.U
//  burstCounter.io.enable := Mux(sparseWriteEnable,
//                              ~addrFifo.io.empty & dramReady,
//                              Mux(writeCmd, wdataValid & wdataReady, burstVld & dramReady & ~issued)) // & ~issued

  burstCounter.io.enable := Mux(writeCmd, wdataValid & wdataReady, burstVld & dramReady & ~issued) // & ~issued

//  burstCounter.io.enable := Mux(scatterGather, ~addrFifo.io.empty, burstVld) & dramReady & ~issued
  burstCounter.io.saturate := 0.U

  // Set a register when DRAMReady goes high, reset it when burstCounter is done (end of a command)
  val dramReadySeen = Wire(Bool())
  val dramReadyFF = Module(new FF(1))
  dramReadyFF.io.init := 0.U
  dramReadyFF.io.enable := burstCounter.io.done | (burstVld & isWrFifo.io.deq(0)(0))
  dramReadyFF.io.in := Mux(burstCounter.io.done, 0.U, dramReady | dramReadySeen) & ~issued
  dramReadySeen := dramReadyFF.io.out
//  dramReadySeen := RegNext(Mux(burstCounter.io.done, false.B, dramReady | dramReadySeen), false.B)
  burstVld := io.enable & ~sizeFifo.io.empty &
    Mux(writeCmd,
      wdataValid & ~dramReadySeen,
      true.B
    )
  io.dram.cmd.bits.dramReadySeen := dramReadySeen

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
  wdataSelectCounter.io.enable := sparseWriteEnable & wdataValid & wdataReady
  wdataSelectCounter.io.saturate := 0.U

  // Coalescing cache
  val ccache = Module(new CoalescingCache(w, d, v))
  ccache.io.raddr := Cat(io.dram.cmd.bits.tag, 0.U(log2Up(burstSizeBytes).W))
  ccache.io.readEn := scatterGather & respValid
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
                          Mux(isSparse, wdataSelectCounter.io.done, true.B) & wdataValid & wdataReady)

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

  val registeredData = Vec(io.dram.rresp.bits.rdata.map { d => RegNext(d, 0.U) })
  val registeredVld = RegNext(respValid, 0.U)

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

//  io.dram.cmd.bits.addr := Cat((burstAddrs(0) + burstCounter.io.out), 0.U(log2Up(burstSizeBytes).W))
  io.dram.cmd.bits.addr := Cat(burstAddrs(0), 0.U(log2Up(burstSizeBytes).W))
  io.dram.cmd.bits.rawAddr := addrFifo.io.deq(0)
  io.dram.cmd.bits.tag := Cat(tagOut.streamTag, tagOut.burstTag)
  io.dram.cmd.bits.streamId := tagOut.streamTag
  io.dram.cmd.bits.isWr := isWrFifo.io.deq(0)

  // wdata assignment
  io.dram.wdata.bits.wdata := wdata
  io.dram.wdata.bits.streamId := wdataFifo.io.tag + loadStreamInfo.size.U
  io.dram.wdata.valid := wdataValid
  val wlast = wdataValid & (burstCounter.io.out === (sizeInBursts-1.U))
  io.dram.wdata.bits.wlast := wlast


  val wasSparseWren = Module(new SRFF()) // Hacky way to allow wrPhase to die after sparse write
  wasSparseWren.io.input.set := sparseWriteEnable
  wasSparseWren.io.input.reset := ~sparseWriteEnable
  wasSparseWren.io.input.asyn_reset := false.B
  
  wrPhase.io.input.set := (~isWrFifo.io.empty & isWrFifo.io.deq(0))
  wrPhase.io.input.reset := templates.Utils.delay(wlast | wasSparseWren.io.output.data,1)
  val dramCmdValid = Mux(scatterGather, ccache.io.miss, burstVld & ~issued)
  io.dram.cmd.valid := dramCmdValid

  val issuedTag = Wire(UInt(w.W))
  if (blockingDRAMIssue) {
    val issuedTagFF = Module(new FF(w))
    issuedTagFF.io.init := 0.U
    issuedTagFF.io.in := Cat(tagOut.streamTag, tagOut.burstTag)
    issuedTagFF.io.enable := burstVld & dramReady & ~issued
    issuedTag := issuedTagFF.io.out
  } else {
    issuedTag := 0.U
  }

  val readStreamTagFromDRAM = if (blockingDRAMIssue) getStreamTag(issuedTag) else getStreamTag(io.dram.rresp.bits.tag)
  val writeStreamTagFromDRAM = if (blockingDRAMIssue) getStreamTag(issuedTag) else getStreamTag(io.dram.wresp.bits.tag)

  val rdataFifos = List.tabulate(loadStreamInfo.size) { i =>
    val m = Module(new WidthConverterFIFO(32, io.dram.rresp.bits.rdata.size, loadStreamInfo(i).w, loadStreamInfo(i).v, d))
    m.io.enq := io.dram.rresp.bits.rdata
    m.io.enqVld := io.dram.rresp.valid & (readStreamTagFromDRAM === i.U) & ~(m.io.full | m.io.almostFull)
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
    m.io.enq(0) := io.dram.wresp.valid
    m.io.enqVld := io.dram.wresp.valid & (writeStreamTagFromDRAM === (i + loadStreamInfo.size).U)
    m
  }

  io.app.stores.map{_.wresp}.zip(wrespFifos) foreach { case (wresp, fifo) =>
    wresp.bits  := fifo.io.deq(0)
    wresp.valid := ~fifo.io.empty
    fifo.io.deqVld := wresp.ready
  }

  if (rdataFifos.length > 0) {
    val readyMux = Module(new MuxNType(Bool(), rdataFifos.length))
    readyMux.io.ins := Vec(rdataFifos.map { f => ~(f.io.full | f.io.almostFull) })
    readyMux.io.sel := readStreamTagFromDRAM
    io.dram.rresp.ready := readyMux.io.out
  } else {
    io.dram.rresp.ready := true.B
  }
  if (wrespFifos.length > 0) {
    val readyMux = Module(new MuxNType(Bool(), wrespFifos.length))
    readyMux.io.ins := Vec(wrespFifos.map { f => ~(f.io.full | f.io.almostFull) })
    readyMux.io.sel := writeStreamTagFromDRAM - loadStreamInfo.size.U
    io.dram.wresp.ready := readyMux.io.out
  } else {
    io.dram.wresp.ready := true.B
  }

  // Some assertions
  if (enableHwAsserts) {
    assert((dramCmdValid & io.enable) | (~io.enable & ~dramCmdValid) | (io.enable & ~dramCmdValid), "DRAM command is valid when enable == 0!")
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
  numRespHighCtr.io.enable := respValid
  io.dbg.num_resp_valid := numRespHighCtr.io.out

  val numRespAndEnableHighCtr = Module(new Counter(32))
  numRespAndEnableHighCtr.io.reset := 0.U
  numRespAndEnableHighCtr.io.saturate := 0.U
  numRespAndEnableHighCtr.io.max := 100000000.U
  numRespAndEnableHighCtr.io.stride := 1.U
  numRespAndEnableHighCtr.io.enable := respValid & io.enable
  io.dbg.num_resp_valid_enable := numRespAndEnableHighCtr.io.out

  val rdataEnqCtr = Module(new Counter(32))
  rdataEnqCtr.io.reset := 0.U
  rdataEnqCtr.io.saturate := 0.U
  rdataEnqCtr.io.max := 100000000.U
  rdataEnqCtr.io.stride := 1.U
  rdataEnqCtr.io.enable := respValid // & (streamTagFromDRAM === 0.U)
  io.dbg.num_rdata_enq := rdataEnqCtr.io.out

  val rdataFifoEnqCtrs = List.tabulate(rdataFifos.size) { i =>
    val fifo = rdataFifos(i)
    val enqCtr = Module(new Counter(32))
    enqCtr.io.reset := 0.U
    enqCtr.io.saturate := 0.U
    enqCtr.io.max := 100000000.U
    enqCtr.io.stride := 1.U
    enqCtr.io.enable := fifo.io.enqVld
    enqCtr.io.out
  }

  val numCommandsCtr = Module(new Counter(32))
  numCommandsCtr.io.reset := 0.U
  numCommandsCtr.io.saturate := 0.U
  numCommandsCtr.io.max := 100000000.U
  numCommandsCtr.io.stride := 1.U
  numCommandsCtr.io.enable := io.enable & dramReady & dramCmdValid

  val numReadCommandsCtr = Module(new Counter(32))
  numReadCommandsCtr.io.reset := 0.U
  numReadCommandsCtr.io.saturate := 0.U
  numReadCommandsCtr.io.max := 100000000.U
  numReadCommandsCtr.io.stride := 1.U
  numReadCommandsCtr.io.enable := io.enable & dramReady & dramCmdValid & ~isWrFifo.io.deq(0)

  val numWriteCommandsCtr = Module(new Counter(32))
  numWriteCommandsCtr.io.reset := 0.U
  numWriteCommandsCtr.io.saturate := 0.U
  numWriteCommandsCtr.io.max := 100000000.U
  numWriteCommandsCtr.io.stride := 1.U
  numWriteCommandsCtr.io.enable := io.enable & dramReady & dramCmdValid & isWrFifo.io.deq(0)

  val numWdataReadyCtr = Module(new Counter(32))
  numWdataReadyCtr.io.reset := 0.U
  numWdataReadyCtr.io.saturate := 0.U
  numWdataReadyCtr.io.max := 100000000.U
  numWdataReadyCtr.io.stride := 1.U
  numWdataReadyCtr.io.enable := io.enable & wdataReady

  val numWdataValidCtr = Module(new Counter(32))
  numWdataValidCtr.io.reset := 0.U
  numWdataValidCtr.io.saturate := 0.U
  numWdataValidCtr.io.max := 100000000.U
  numWdataValidCtr.io.stride := 1.U
  numWdataValidCtr.io.enable := io.enable & wdataValid

  val numWdataCtr = Module(new Counter(32))
  numWdataCtr.io.reset := 0.U
  numWdataCtr.io.saturate := 0.U
  numWdataCtr.io.max := 100000000.U
  numWdataCtr.io.stride := 1.U
  numWdataCtr.io.enable := io.enable & wdataValid & wdataReady

  val addrEnqCtr = Module(new Counter(32))
  addrEnqCtr.io.reset := 0.U
  addrEnqCtr.io.saturate := 0.U
  addrEnqCtr.io.max := 100000000.U
  addrEnqCtr.io.stride := 1.U
  addrEnqCtr.io.enable := io.enable & addrFifo.io.enqVld(0)

  val addrDeqCtr = Module(new Counter(32))
  addrDeqCtr.io.reset := 0.U
  addrDeqCtr.io.saturate := 0.U
  addrDeqCtr.io.max := 100000000.U
  addrDeqCtr.io.stride := 1.U
  addrDeqCtr.io.enable := io.enable & addrFifo.io.deqVld

  val sizeNotEmptyCtr = Module(new Counter(32))
  sizeNotEmptyCtr.io.reset := 0.U
  sizeNotEmptyCtr.io.saturate := 0.U
  sizeNotEmptyCtr.io.max := 100000000.U
  sizeNotEmptyCtr.io.stride := 1.U
  sizeNotEmptyCtr.io.enable := io.enable & ~sizeFifo.io.empty

  val rdataFullCtrs = List.tabulate(rdataFifos.size) { i =>
    val fifo = rdataFifos(i)
    val fullCtr = Module(new Counter(32))
    fullCtr.io.reset := 0.U
    fullCtr.io.saturate := 0.U
    fullCtr.io.max := 100000000.U
    fullCtr.io.stride := 1.U
    fullCtr.io.enable := io.enable & fifo.io.full
    fullCtr.io.out
  }


//  val rdataDeqCtr = Module(new Counter(32))
//  rdataDeqCtr.io.reset := 0.U
//  rdataDeqCtr.io.saturate := 0.U
//  rdataDeqCtr.io.max := 100000000.U
//  rdataDeqCtr.io.stride := 1.U
//  rdataDeqCtr.io.enable := io.app.loads(0).rdata.ready & ~rdataFifos(0).io.empty
//  io.dbg.num_rdata_deq := rdataDeqCtr.io.out
//
//  val wdataEnqCtr = Module(new Counter(32))
//  wdataEnqCtr.io.reset := 0.U
//  wdataEnqCtr.io.saturate := 0.U
//  wdataEnqCtr.io.max := 100000000.U
//  wdataEnqCtr.io.stride := 1.U
//  wdataEnqCtr.io.enable := io.app.stores(0).wdata.valid
//
//  val wdataDeqCtr = Module(new Counter(32))
//  wdataDeqCtr.io.reset := 0.U
//  wdataDeqCtr.io.saturate := 0.U
//  wdataDeqCtr.io.max := 100000000.U
//  wdataDeqCtr.io.stride := 1.U
//  wdataDeqCtr.io.enable := burstVld & isWrFifo.io.deq(0) & dramReady & ~issued
//
//  val appRdataReadyCtr = Module(new Counter(32))
//  appRdataReadyCtr.io.reset := 0.U
//  appRdataReadyCtr.io.saturate := 0.U
//  appRdataReadyCtr.io.max := 100000000.U
//  appRdataReadyCtr.io.stride := 1.U
//  appRdataReadyCtr.io.enable := io.app.loads(0).rdata.ready
//  io.dbg.num_app_rdata_ready := appRdataReadyCtr.io.out

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

  def getCounter(en: UInt) = {
    val ctr = Module(new Counter(32))
    ctr.io.reset := 0.U
    ctr.io.saturate := 0.U
    ctr.io.max := 100000000.U
    ctr.io.stride := 1.U
    ctr.io.enable := en
    ctr.io.out
  }

  var dbgCount = 0
  val signalLabels = ListBuffer[String]()
  def connectDbgSignal(sig: UInt, label: String = "") {
    io.debugSignals(dbgCount) := sig
    dbgCount += 1
    signalLabels.append(label)
  }

  connectDbgSignal(getCounter(io.enable), "Cycles")
  // rdata enq values
//  for (i <- 0 until numRdataDebug) {
//    for (j <- 0 until numRdataWordsDebug) {
//      connectDbgSignal(getFF(io.dram.rresp.bits.rdata(j), respValid & (rdataEnqCtr.io.out === i.U)), s"""rdata_from_dram${i}_$j""")
//    }
//  }
//
//  if (io.app.stores.size > 0) {
//    // wdata enq values
//    val appWdata0EnqCtr = getCounter(io.enable & io.app.stores(0).wdata.valid)
//    for (i <- 0 until numWdataDebug) {
//      for (j <- 0 until math.min(io.app.stores(0).wdata.bits.size, numWdataWordsDebug)) {
//        connectDbgSignal(getFF(io.app.stores(0).wdata.bits(j), io.enable & (appWdata0EnqCtr === i.U)), s"""wdata_from_accel${i}_$j""")
//      }
//    }
//
//    // wdata values
//    for (i <- 0 until numWdataDebug) {
//      for (j <- 0 until numWdataWordsDebug) {
//        connectDbgSignal(getFF(Cat(wdataFifoSize(15, 0), io.dram.wdata.bits.wdata(j)(15, 0)), io.enable & wdataValid & wdataReady & (numWdataCtr.io.out === i.U)), s"""wdata_to_dram${i}_$j""")
//      }
//    }
//  }

//  connectDbgSignal(numCommandsCtr.io.out, "Num DRAM Commands")
//  connectDbgSignal(numReadCommandsCtr.io.out, "Read Commands")
//  connectDbgSignal(numWriteCommandsCtr.io.out, "Write Commands")
//
//  // Count number of commands issued per stream
//  (0 until numStreams) foreach { case i =>
//    val signal = "cmd" + (if (i < loadStreamInfo.size) "load" else "store") + s"stream $i"
//    connectDbgSignal(getCounter(dramCmdValid & dramReady & (tagOut.streamTag === i.U)), signal)
//  }
//
//  connectDbgSignal(getCounter(respValid), "Num DRAM Responses")
//
//  // Count number of responses issued per stream
//  (0 until numStreams) foreach { case i =>
//    val signal = "resp " + (if (i < loadStreamInfo.size) "load" else "store") + s"stream $i"
//    val respValidSignal = (if (i < loadStreamInfo.size) io.dram.rresp.valid else io.dram.wresp.valid)
//    val respReadySignal = (if (i < loadStreamInfo.size) io.dram.rresp.ready else io.dram.wresp.ready)
//    val respTagSignal = (if (i < loadStreamInfo.size) readStreamTagFromDRAM else writeStreamTagFromDRAM)
//    connectDbgSignal(getCounter(respValidSignal & respReadySignal & (respTagSignal === i.U)), signal)
//  }
//
//  connectDbgSignal(getCounter(io.dram.rresp.valid & rdataFifos.map {_.io.enqVld}.reduce{_|_}), "RResp valid enqueued somewhere")
//  connectDbgSignal(getCounter(io.dram.rresp.valid & io.dram.rresp.ready), "Rresp valid and ready")
//  connectDbgSignal(getCounter(io.dram.rresp.valid & io.dram.rresp.ready & rdataFifos.map {_.io.enqVld}.reduce{_|_}), "Resp valid and ready and enqueued somewhere")
//  connectDbgSignal(getCounter(io.dram.rresp.valid & ~io.dram.rresp.ready), "Resp valid and not ready")
//
//  // Responses enqueued into appropriate places
//  (0 until loadStreamInfo.size) foreach { case i =>
//    val signal = s"rdataFifo $i enq"
//    connectDbgSignal(getCounter(rdataFifos(i).io.enqVld), signal)
//  }
//
//  // Responses enqueued into appropriate places
//  (0 until storeStreamInfo.size) foreach { case i =>
//    val signal = s"wrespFifo $i enq"
//    connectDbgSignal(getCounter(wrespFifos(i).io.enqVld), signal)
//  }
//
//  connectDbgSignal(numWdataCtr.io.out, "num wdata transferred (wvalid & wready)")
//
//
//  connectDbgSignal(getCounter(io.dram.rresp.valid & io.dram.wresp.valid), "Rvalid and Bvalid")

//  connectDbgSignal(numWdataValidCtr.io.out, "wdata_valid")
//  connectDbgSignal(numWdataReadyCtr.io.out, "wdata_ready")
//  connectDbgSignal(dramReadySeen, "dramReadySeen")
//  connectDbgSignal(writeCmd, "writeCmd")
//  connectDbgSignal(wrPhase.io.output.data, "wrPhase")
//  connectDbgSignal(~isWrFifo.io.empty & isWrFifo.io.deq(0)(0), "~isWrFifo.io.empty & isWrFifo.io.deq(0)(0)")
//  connectDbgSignal(getCounter(wdataValid & wdataReady & ~issued), "wvalid & wready & ~issued")
//  connectDbgSignal(getCounter(wdataValid & wdataReady & issued), "wvalid & wready & issued")


  // Print all debugging signals into a header file
  val debugFileName = "cpp/generated_debugRegs.h"
  val debugPW = new PrintWriter(new File(debugFileName))
  debugPW.println(s"""
#ifndef __DEBUG_REGS_H__
#define __DEBUG_REGS_H__

#define NUM_DEBUG_SIGNALS ${signalLabels.size}

const char *signalLabels[] = {
""")

  debugPW.println(signalLabels.map { l => s"""\"${l}\"""" }.mkString(", "))
  debugPW.println("};")
  debugPW.println("#endif // __DEBUG_REGS_H__")
  debugPW.close

//  io.debugSignals(48) := enableCounter.io.out
//  io.debugSignals(49) := cmdValidEnableCtr.io.out
//  io.debugSignals(50) := numReadyAndEnableHighCtr.io.out
//  io.debugSignals(51) := numRespAndEnableHighCtr.io.out
//  io.debugSignals(52) := numCommandsCtr.io.out
//  io.debugSignals(53) := numReadCommandsCtr.io.out
//  io.debugSignals(54) := numWriteCommandsCtr.io.out
//  io.debugSignals(55) := getFF(io.dram.cmd.bits.addr, io.enable & dramCmdValid & (cmdValidEnableCtr.io.out === 0.U))
//  io.debugSignals(56) := getFF(io.dram.cmd.bits.size, io.enable & dramCmdValid & (cmdValidEnableCtr.io.out === 0.U))
//  io.debugSignals(57) := addrEnqCtr.io.out
//  io.debugSignals(58) := addrDeqCtr.io.out
//connectDbgSignal(enableCounter.io.out)
//connectDbgSignal(cmdValidEnableCtr.io.out)
//connectDbgSignal(numReadyAndEnableHighCtr.io.out)
//connectDbgSignal(numRespAndEnableHighCtr.io.out)

//connectDbgSignal(getFF(io.dram.cmd.bits.addr, io.enable & dramCmdValid & (cmdValidEnableCtr.io.out === 0.U)))
//connectDbgSignal(getFF(io.dram.cmd.bits.size, io.enable & dramCmdValid & (cmdValidEnableCtr.io.out === 0.U)))
//connectDbgSignal(addrEnqCtr.io.out)
//connectDbgSignal(addrDeqCtr.io.out)


//  io.debugSignals(59) := rdataFifoEnqCtrs(0)
//  io.debugSignals(60) := rdataFifoEnqCtrs(1)
//  io.debugSignals(61) := rdataFifoEnqCtrs(2)
//  io.debugSignals(62) := rdataFullCtrs(0)
//  io.debugSignals(63) := rdataFullCtrs(1)
//  io.debugSignals(64) := rdataFullCtrs(2)

//  io.debugSignals(65) := getFF(io.dram.cmd.bits.tag, io.enable & dramCmdValid & dramReady & (numCommandsCtr.io.out === 0.U))
//  io.debugSignals(66) := getFF(io.dram.cmd.bits.tag, io.enable & dramCmdValid & dramReady & (numCommandsCtr.io.out === 1.U))
//  io.debugSignals(67) := getFF(io.dram.cmd.bits.tag, io.enable & dramCmdValid & dramReady & (numCommandsCtr.io.out === 2.U))
//  io.debugSignals(68) := getFF(io.dram.resp.bits.tag, io.enable & respValid & (numRespAndEnableHighCtr.io.out === 0.U))
//  io.debugSignals(69) := getFF(io.dram.resp.bits.tag, io.enable & respValid & (numRespAndEnableHighCtr.io.out === 1.U))
//  io.debugSignals(70) := getFF(io.dram.resp.bits.tag, io.enable & respValid & (numRespAndEnableHighCtr.io.out === 2.U))
//  io.debugSignals(71) := getFF(io.dram.resp.bits.streamId, io.enable & respValid & (numRespAndEnableHighCtr.io.out === 0.U))
//  io.debugSignals(72) := getFF(io.dram.resp.bits.streamId, io.enable & respValid & (numRespAndEnableHighCtr.io.out === 1.U))
//  io.debugSignals(73) := getFF(io.dram.resp.bits.streamId, io.enable & respValid & (numRespAndEnableHighCtr.io.out === 2.U))
//  io.debugSignals(74) := numWdataValidCtr.io.out
//  io.debugSignals(75) := numWdataReadyCtr.io.out
//  io.debugSignals(76) := numWdataCtr.io.out
//  io.debugSignals(77) := issued
//  io.debugSignals(78) := issuedTag
//  io.debugSignals(79) := wrespFifoEnqCtrs(0)
//  io.debugSignals(80) := dramReadySeen
//  io.debugSignals(81) := writeCmd
//  io.debugSignals(82) := wrPhase.io.output.data
//  io.debugSignals(83) := ~isWrFifo.io.empty & isWrFifo.io.deq(0)(0)
//  io.debugSignals(84) := getCounter(wdataValid & wdataReady & ~issued)
//  io.debugSignals(85) := getCounter(wdataValid & wdataReady & issued)
//  connectDbgSignal(getFF(io.dram.cmd.bits.tag, io.enable & dramCmdValid & dramReady & (numCommandsCtr.io.out === 0.U)))
//  connectDbgSignal(getFF(io.dram.cmd.bits.tag, io.enable & dramCmdValid & dramReady & (numCommandsCtr.io.out === 1.U)))
//  connectDbgSignal(getFF(io.dram.cmd.bits.tag, io.enable & dramCmdValid & dramReady & (numCommandsCtr.io.out === 2.U)))
//  connectDbgSignal(getFF(io.dram.resp.bits.tag, io.enable & respValid & (numRespAndEnableHighCtr.io.out === 0.U)))
//  connectDbgSignal(getFF(io.dram.resp.bits.tag, io.enable & respValid & (numRespAndEnableHighCtr.io.out === 1.U)))
//  connectDbgSignal(getFF(io.dram.resp.bits.tag, io.enable & respValid & (numRespAndEnableHighCtr.io.out === 2.U)))
//  connectDbgSignal(getFF(io.dram.resp.bits.streamId, io.enable & respValid & (numRespAndEnableHighCtr.io.out === 0.U)))
//  connectDbgSignal(getFF(io.dram.resp.bits.streamId, io.enable & respValid & (numRespAndEnableHighCtr.io.out === 1.U)))
//  connectDbgSignal(getFF(io.dram.resp.bits.streamId, io.enable & respValid & (numRespAndEnableHighCtr.io.out === 2.U)))


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
