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
  val blockingDRAMIssue: Boolean = false,
  val isDebugChannel: Boolean = false
) extends Module { // AbstractMAG(w, d, v, numOutstandingBursts, burstSizeBytes) {

  val numRdataDebug = 2
  val numRdataWordsDebug = 16
  val numWdataDebug = 0
  val numWdataWordsDebug = 16
  val numDebugs = 432

  // The data bus width to DRAM is 1-burst wide
  // While it should be possible in the future to add a write combining buffer
  // and decouple Plasticine's data bus width from the DRAM bus width,
  // this is currently left out as a wishful todo.
  // Check and error out if the data bus width does not match DRAM burst size
  // Predef.assert(w/8*v == 64,
  // s"Unsupported combination of w=$w and v=$v; data bus width must equal DRAM burst size ($burstSizeBytes bytes)")

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

    // val dbg = new DebugSignals
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

  val addrWidth = io.app.loads(0).cmd.bits.addrWidth
  val sizeWidth = io.app.loads(0).cmd.bits.sizeWidth

  val commandWidth =  addrWidth + 1 + 1 + sizeWidth
  val commandFifo = Module(new FIFOArbiter(commandWidth, d, 1, numStreams))
  val commandFifoConfig = Wire(new FIFOOpcode(d, 1))
  commandFifoConfig.chainRead := 1.U
  commandFifoConfig.chainWrite := 1.U
  commandFifo.io.config := commandFifoConfig
  commandFifo.io.forceTag.valid := 0.U

  val cmds = io.app.loads.map { _.cmd} ++ io.app.stores.map {_.cmd}

  def packCmd(c: Command): UInt = Cat(c.addr, c.isWr, c.isSparse, c.size)
  def unpackCmd(c: UInt): Command = {
    val cmd = Wire(new Command(addrWidth, sizeWidth, 0))
    val addrStart = c.getWidth - 1
    val isWrStart = addrStart - addrWidth
    val isSparseStart = isWrStart - 1
    val sizeStart = isSparseStart - 1
    cmd.addr := c(addrStart, isWrStart + 1)
    cmd.isWr := c(isWrStart)
    cmd.isSparse := c(isSparseStart)
    cmd.size := c(sizeStart, 0)
    cmd
  }

  val headCommand = Wire(new Command(addrWidth, sizeWidth, 0))
  headCommand := unpackCmd(commandFifo.io.deq(0))

  commandFifo.io.enq.zip(cmds) foreach {case (enq, cmd) => enq(0) := packCmd(cmd.bits) }
  commandFifo.io.enqVld.zip(cmds) foreach {case (enqVld, cmd) => enqVld := cmd.valid }

  // Debug assertions
  if (enableHwAsserts) {
    for (i <- 0 until numStreams) {
      val str3 = s"ERROR: addrFifo $i enqVld is high when not enabled!"
      // if (FringeGlobals.target != "verilator") assert((io.enable & cmds(i).valid) | (io.enable & ~cmds(i).valid) | (~io.enable & ~cmds(i).valid), str3)
    }
  }

  val burstAddr = extractBurstAddr(headCommand.addr)
  val wordOffsets = extractWordOffset(headCommand.addr)

  val isSparse = headCommand.isSparse & ~commandFifo.io.empty
  io.dram.cmd.bits.isSparse := isSparse

  val scatterGather = Wire(Bool())
  scatterGather := io.config.scatterGather
//  val sgPulse = Wire(Bool())
//  scatterGather := isSparseFifo.io.deq(0)(0) & ~isSparseFifo.io.empty
//  val sgPulser = Module(new Pulser())
//  sgPulser.io.in := scatterGather
//  sgPulse := sgPulser.io.out

  val sizeTop = headCommand.size
  val sizeInBursts = extractBurstAddr(sizeTop) + (extractBurstOffset(sizeTop) != 0.U)
  io.dram.cmd.bits.size := sizeInBursts

  // WData FIFO
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

  val writeCmd = ~commandFifo.io.empty & (wrPhase.io.output.data | headCommand.isWr) // & ~issued
  val wdataValid = writeCmd & ~wdataFifo.io.empty

  val dramReady = io.dram.cmd.ready
  val wdataReady = io.dram.wdata.ready

  val respValid = io.dram.rresp.valid | io.dram.wresp.valid // & io.enable

  wdataFifo.io.forceTag.bits := commandFifo.io.tag - loadStreamInfo.size.U
  wdataFifo.io.forceTag.valid := commandFifo.io.tag >= loadStreamInfo.size.U

  wdataFifo.io.enq.zip(io.app.stores.map{_.wdata}) foreach { case (enq, wdata) => enq := wdata.bits }
  wdataFifo.io.enqVld.zip(io.app.stores.map{_.wdata}) foreach {case (enqVld, wdata) => enqVld := wdata.valid }
  val wdataFifoSize = wdataFifo.io.fifoSize

  val sparseWriteEnable = Wire(Bool())
  sparseWriteEnable := (isSparse & headCommand.isWr & ~commandFifo.io.empty)

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
  burstCounter.io.enable := Mux(writeCmd, wdataValid & wdataReady, burstVld & dramReady & ~issued) // & ~issued
  burstCounter.io.saturate := 0.U

  // Set a register when DRAMReady goes high, reset it when burstCounter is done (end of a command)
  val dramReadySeen = Wire(Bool())
  val dramReadyFF = Module(new FF(1))
  dramReadyFF.io.init := 0.U
  dramReadyFF.io.enable := burstCounter.io.done | (burstVld & headCommand.isWr)
  dramReadyFF.io.in := Mux(burstCounter.io.done, 0.U, dramReady | dramReadySeen) & ~issued
  dramReadySeen := dramReadyFF.io.out
  burstVld := io.enable & ~commandFifo.io.empty &
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
  burstTagCounter.io.enable := Mux(scatterGather, ~commandFifo.io.empty & ~sgWaitForDRAM, burstVld & dramReady) & ~issued
  burstCounter.io.saturate := 0.U
  val elementID = burstTagCounter.io.out(log2Up(v)-1, 0)

  // Counter to pick correct wdataFifo for a sparse write
  val wdataSelectCounter = Module(new Counter(log2Up(v+1)))
  wdataSelectCounter.io.max := v.U
  wdataSelectCounter.io.stride := 1.U
  wdataSelectCounter.io.reset := ~(isSparse & headCommand.isWr)
  wdataSelectCounter.io.enable := sparseWriteEnable & wdataValid & wdataReady
  wdataSelectCounter.io.saturate := 0.U

  // Coalescing cache
  val ccache = Module(new CoalescingCache(w, d, v))
  ccache.io.raddr := Cat(io.dram.cmd.bits.tag, 0.U(log2Up(burstSizeBytes).W))
  ccache.io.readEn := scatterGather & respValid
  ccache.io.waddr := headCommand.addr
  ccache.io.wen := scatterGather & ~commandFifo.io.empty
  ccache.io.position := elementID
  ccache.io.wdata := wdataFifo.io.deq(0)
  ccache.io.isScatter := false.B // TODO: Remove this restriction once ready

  val sgValidDepulser = Module(new Depulser())
  sgValidDepulser.io.in := ccache.io.miss
  sgValidDepulser.io.rst := dramReady
  sgWaitForDRAM := sgValidDepulser.io.out

  commandFifo.io.deqVld := Mux(scatterGather, ~ccache.io.full & ~sgWaitForDRAM, burstCounter.io.done)

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
  tagOut.streamTag := commandFifo.io.tag
  tagOut.burstTag := Mux(scatterGather, burstAddr, burstTagCounter.io.out)

  val wdata = Vec(List.tabulate(v) { i =>
    if (i == 0) {
      val mux = Module(new MuxNType(UInt(w.W), v))
      mux.io.ins := wdataFifo.io.deq
      mux.io.sel := wdataSelectCounter.io.out
      mux.io.out
    } else wdataFifo.io.deq(i)
  })

  io.dram.cmd.bits.addr := Cat(burstAddr, 0.U(log2Up(burstSizeBytes).W))
  io.dram.cmd.bits.rawAddr := headCommand.addr
  io.dram.cmd.bits.tag := Cat(tagOut.streamTag, tagOut.burstTag)
  io.dram.cmd.bits.streamId := tagOut.streamTag
  io.dram.cmd.bits.isWr := headCommand.isWr

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

  wrPhase.io.input.set := (~commandFifo.io.empty & headCommand.isWr)
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
    cmd.ready := ~commandFifo.io.full(i)
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
  if (enableHwAsserts & FringeGlobals.target != "verilator") {
    // assert((dramCmdValid & io.enable) | (~io.enable & ~dramCmdValid) | (io.enable & ~dramCmdValid), "DRAM command is valid when enable == 0!")
  }

  // All debug counters
  def getFF[T<:Data](sig: T, en: UInt) = {
    val in = sig match {
      case v: Vec[UInt] => v.reverse.reduce { Cat(_,_) }
      case u: UInt => u
    }

    val ff = Module(new FF(sig.getWidth))
    ff.io.init := Cat("hBADF".U, dbgCount.U)
    ff.io.in := in
    ff.io.enable := en
    ff.io.out
  }

  def getCounter(en: UInt, width: Int = 32) = {
    val ctr = Module(new Counter(width))
    ctr.io.reset := 0.U
    ctr.io.saturate := 0.U
    ctr.io.max := ((1.toLong << width) - 1).U
    ctr.io.stride := 1.U
    ctr.io.enable := en
    ctr.io.out
  }

  def addWhen(en: UInt, value: UInt) = {
    val ff = Module(new FF(32))
    //ff.io.init := 15162342.U
    ff.io.init := 0.U
    ff.io.enable := en
    ff.io.in := ff.io.out + value
    ff.io.out
  }

  var dbgCount = 0
  val signalLabels = ListBuffer[String]()
  def connectDbgSignal(sig: UInt, label: String = "") {
    io.debugSignals(dbgCount) := sig
    signalLabels.append(label + s" ($dbgCount)")
    dbgCount += 1
  }

  val cycleCount = getCounter(io.enable, 40)
  connectDbgSignal(cycleCount, "Cycles")
  val rdataEnqCount = getCounter(io.dram.rresp.valid & io.dram.rresp.ready)

  // rdata enq values
  for (i <- 0 until numRdataDebug) {
    for (j <- 0 until numRdataWordsDebug) {
      connectDbgSignal(getFF(io.dram.rresp.bits.rdata(j), io.dram.rresp.ready & io.dram.rresp.valid & (rdataEnqCount === i.U)), s"""rdata_from_dram${i}_$j""")
    }
  }

  val wdataCount = getCounter(io.enable & wdataValid & wdataReady)
  if (io.app.stores.size > 0) {
    // wdata enq values
    val appWdata0EnqCtr = getCounter(io.enable & io.app.stores(0).wdata.valid)
    for (i <- 0 until numWdataDebug) {
      for (j <- 0 until math.min(io.app.stores(0).wdata.bits.size, numWdataWordsDebug)) {
        connectDbgSignal(getFF(io.app.stores(0).wdata.bits(j), io.enable & (appWdata0EnqCtr === i.U)), s"""wdata_from_accel${i}_$j""")
      }
    }

    // wdata values
    for (i <- 0 until numWdataDebug) {
      for (j <- 0 until numWdataWordsDebug) {
        connectDbgSignal(getFF(Cat(wdataFifoSize(15, 0), io.dram.wdata.bits.wdata(j)(15, 0)), io.enable & wdataValid & wdataReady & (wdataCount === i.U)), s"""wdata_to_dram${i}_$j""")
      }
    }
  }

  connectDbgSignal(getCounter(io.enable & dramReady & dramCmdValid), "Num DRAM Commands")
  connectDbgSignal(getCounter(io.enable & ~commandFifo.io.empty & ~(dramReady & dramCmdValid)), "Total gaps in issue")
  connectDbgSignal(getCounter(io.enable & dramReady & dramCmdValid & ~headCommand.isWr), "Read Commands")
  connectDbgSignal(getCounter(io.enable & dramReady & dramCmdValid & headCommand.isWr), "Write Commands")

  // Count number of commands issued per stream
  (0 until numStreams) foreach { case i =>
    val signal = "cmd" + (if (i < loadStreamInfo.size) "load" else "store") + s"stream $i"
    connectDbgSignal(getCounter(dramCmdValid & dramReady & (tagOut.streamTag === i.U)), signal)
  }

  // Count number of load commands issued from accel per p
  io.app.loads.zipWithIndex.foreach { case (load, i) =>
    val loadCounter = getCounter(io.enable & load.cmd.valid)
    val loadCounterHandshake = getCounter(io.enable & load.cmd.valid & load.cmd.ready)
    connectDbgSignal(loadCounter, s"LoadCmds from Accel (valid) $i")
    connectDbgSignal(loadCounterHandshake, s"LoadCmds from Accel (valid & ready) $i")
  }

  // Count number of store commands issued from accel per stream
  io.app.stores.zipWithIndex.foreach { case (store, i) =>
    val storeCounter = getCounter(io.enable & store.cmd.valid)
    val storeCounterHandshake = getCounter(io.enable & store.cmd.valid & store.cmd.ready)
    val lastWaddr = getFF(headCommand.addr, io.enable & store.cmd.valid & store.cmd.ready)
    val firstSent = RegInit(true.B)
    firstSent := Mux(io.enable & store.cmd.valid & store.cmd.ready, false.B, firstSent)
    val firstWaddr = getFF(headCommand.addr, firstSent)
    connectDbgSignal(storeCounter, s"StoreCmds from Accel (valid) $i")
    connectDbgSignal(storeCounterHandshake, s"StoreCmds from Accel (valid & ready) $i")
    connectDbgSignal(firstWaddr, s"First store cmd addr $i")    
    connectDbgSignal(lastWaddr, s"Last store cmd addr $i")    
  }

  connectDbgSignal(getCounter(respValid), "Num DRAM Responses")
  connectDbgSignal(getCounter(io.enable & ~(io.dram.rresp.valid & io.dram.rresp.ready)), "Total gaps in read responses")
  connectDbgSignal(getCounter(io.enable & io.dram.rresp.valid & ~io.dram.rresp.ready), "Total gaps in read responses (rresp.valid & ~rresp.ready)")
  connectDbgSignal(getCounter(io.enable & ~io.dram.rresp.valid & io.dram.rresp.ready), "Total gaps in read responses (~rresp.valid & rresp.ready)")
  connectDbgSignal(getCounter(io.enable & ~io.dram.rresp.valid & ~io.dram.rresp.ready), "Total gaps in read responses (~rresp.valid & ~rresp.ready)")

  // Count number of responses issued per stream
  (0 until numStreams) foreach { case i =>
    val signal = "resp " + (if (i < loadStreamInfo.size) "load" else "store") + s"stream $i"
    val respValidSignal = (if (i < loadStreamInfo.size) io.dram.rresp.valid else io.dram.wresp.valid)
    val respReadySignal = (if (i < loadStreamInfo.size) io.dram.rresp.ready else io.dram.wresp.ready)
    val respTagSignal = (if (i < loadStreamInfo.size) readStreamTagFromDRAM else writeStreamTagFromDRAM)
    connectDbgSignal(getCounter(respValidSignal & respReadySignal & (respTagSignal === i.U)), signal)
  }

  connectDbgSignal(getCounter(io.dram.rresp.valid & rdataFifos.map {_.io.enqVld}.reduce{_|_}), "RResp valid enqueued somewhere")
  connectDbgSignal(getCounter(io.dram.rresp.valid & io.dram.rresp.ready), "Rresp valid and ready")
  connectDbgSignal(getCounter(io.dram.rresp.valid & io.dram.rresp.ready & rdataFifos.map {_.io.enqVld}.reduce{_|_}), "Resp valid and ready and enqueued somewhere")
  connectDbgSignal(getCounter(io.dram.rresp.valid & ~io.dram.rresp.ready), "Resp valid and not ready")

  // Responses enqueued into appropriate places
  (0 until loadStreamInfo.size) foreach { case i =>
    val signal = s"rdataFifo $i enq"
    connectDbgSignal(getCounter(rdataFifos(i).io.enqVld), signal)
  }

  // Responses enqueued into appropriate places
  (0 until storeStreamInfo.size) foreach { case i =>
    val signal = s"wrespFifo $i enq"
    connectDbgSignal(getCounter(wrespFifos(i).io.enqVld), signal)
  }

  connectDbgSignal(wdataCount, "num wdata transferred (wvalid & wready)")

//  connectDbgSignal(getCounter(io.dram.rresp.valid & io.dram.wresp.valid), "Rvalid and Bvalid")

//  connectDbgSignal(numWdataValidCtr.io.out, "wdata_valid")
//  connectDbgSignal(numWdataReadyCtr.io.out, "wdata_ready")
//  connectDbgSignal(dramReadySeen, "dramReadySeen")
//  connectDbgSignal(writeCmd, "writeCmd")
//  connectDbgSignal(wrPhase.io.output.data, "wrPhase")
//  connectDbgSignal(~isWrFifo.io.empty & isWrFifo.io.deq(0)(0), "~isWrFifo.io.empty & isWrFifo.io.deq(0)(0)")
//  connectDbgSignal(getCounter(wdataValid & wdataReady & ~issued), "wvalid & wready & ~issued")
//  connectDbgSignal(getCounter(wdataValid & wdataReady & issued), "wvalid & wready & issued")

  if (isDebugChannel) {
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
  }
}
