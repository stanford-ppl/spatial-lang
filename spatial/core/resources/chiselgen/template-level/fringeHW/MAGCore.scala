package fringe

import util._
import chisel3._
import chisel3.util._
import templates.SRFF
import templates.Utils.log2Up
import scala.language.reflectiveCalls

import scala.collection.mutable.ListBuffer
import java.io.{File, PrintWriter}

class BurstAddr(addrWidth: Int, w: Int, burstSizeBytes: Int) extends Bundle {
  val bits = UInt(addrWidth.W)
  def burstTag = bits(bits.getWidth - 1, log2Up(burstSizeBytes))
  def burstOffset = bits(log2Up(burstSizeBytes) - 1, 0)
  def burstAddr = Cat(burstTag, 0.U(log2Up(burstSizeBytes).W))
  def wordOffset = bits(log2Up(burstSizeBytes) - 1, log2Up(w/8))

  override def cloneType(): this.type = {
    new BurstAddr(addrWidth, w, burstSizeBytes).asInstanceOf[this.type]
  }
}

class DRAMCmdTag(w: Int) extends Bundle {
  val uid = UInt(6.W)
  val addr = UInt((w - 6).W)

  override def cloneType(): this.type = {
    new DRAMCmdTag(w).asInstanceOf[this.type]
  }
}

class MAGCore(
  val w: Int,
  val d: Int,
  val v: Int,
  val loadStreamInfo: List[StreamParInfo],
  val storeStreamInfo: List[StreamParInfo],
  val numOutstandingBursts: Int,
  val burstSizeBytes: Int,
  val blockingDRAMIssue: Boolean = false,
  val debug: Boolean = true
) extends Module {

  val numStreams = loadStreamInfo.size + storeStreamInfo.size
  val streamTagWidth = log2Up(numStreams)

  def storeStreamIndex(id: UInt) = id - loadStreamInfo.size.U
  def storeStreamId(index: Int) = index + loadStreamInfo.size
  
  val io = IO(new Bundle {
    val enable = Input(Bool())
    val app = new AppStreams(loadStreamInfo, storeStreamInfo)
    val dram = new DRAMStream(w, v)
    val config = Input(new MAGOpcode())
  })

  val addrWidth = io.app.loads(0).cmd.bits.addrWidth
  val sizeWidth = io.app.loads(0).cmd.bits.sizeWidth

  val cmd = new Command(addrWidth, sizeWidth, 0)
  val cmdArbiter = Module(new FIFOArbiter(cmd, d, v, numStreams))
  val cmdFifos = List.fill(numStreams) { Module(new FIFOCore(cmd, d, v)) }
  cmdArbiter.io.fifo.zip(cmdFifos).foreach { case (io, f) => io <> f.io }

  val cmdFifoConfig = Wire(new FIFOOpcode(d, 1))
  cmdFifoConfig.chainRead := true.B
  cmdFifoConfig.chainWrite := true.B
  cmdArbiter.io.config := cmdFifoConfig
  cmdArbiter.io.forceTag.valid := false.B

  val cmds = io.app.loads.map { _.cmd } ++ io.app.stores.map {_.cmd }
  cmdArbiter.io.enq.zip(cmds) foreach {case (enq, cmd) => enq(0) := cmd.bits }
  cmdArbiter.io.enqVld.zip(cmds) foreach {case (enqVld, cmd) => enqVld := cmd.valid }
  cmdArbiter.io.full.zip(cmds) foreach { case (full, cmd) => cmd.ready := ~full }

  val cmdHead = cmdArbiter.io.deq(0)
  val cmdAddr = Wire(new BurstAddr(addrWidth, w, burstSizeBytes))
  cmdAddr.bits := cmdHead.addr

  val cmdRead = io.enable & ~cmdArbiter.io.empty & ~cmdHead.isWr
  val cmdWrite = io.enable & ~cmdArbiter.io.empty & cmdHead.isWr

  val wdataReady = io.dram.wdata.ready
  val burstCounter = Module(new Counter(w))
  val burstTagCounter = Module(new Counter(log2Up(numOutstandingBursts)))
  val dramReadySeen = Wire(Bool())

  val rrespReadyMux = Module(new MuxN(Bool(), loadStreamInfo.size))
  rrespReadyMux.io.sel := io.dram.rresp.bits.streamId
  io.dram.rresp.ready := rrespReadyMux.io.out

  val wdataMux = Module(new MuxN(Valid(io.dram.wdata.bits), storeStreamInfo.size))
  wdataMux.io.sel := storeStreamIndex(cmdArbiter.io.tag)
  wdataMux.io.ins.foreach { case i =>
    i.bits.streamId := cmdArbiter.io.tag
    i.bits.wlast := i.valid & (burstCounter.io.out === (io.dram.cmd.bits.size - 1.U))
  }
  val wdataValid = wdataMux.io.out.valid

  val cmdDeqValidMux = Module(new MuxN(Bool(), numStreams))
  cmdDeqValidMux.io.sel := cmdArbiter.io.tag
  
  val dramCmdMux = Module(new MuxN(Valid(io.dram.cmd.bits), numStreams))
  dramCmdMux.io.sel := cmdArbiter.io.tag
  dramCmdMux.io.ins.foreach { case i =>
    i.bits.isSparse := false.B // remove and take out sparse logic in dram.h
    i.bits.streamId := cmdArbiter.io.tag
    i.bits.addr := cmdAddr.burstAddr
    i.bits.rawAddr := cmdAddr.bits
    val tag = Wire(new DRAMCmdTag(w))
    tag.uid := burstTagCounter.io.out
    tag.addr := cmdAddr.burstTag
    i.bits.tag := tag.asUInt()
    val size = Wire(new BurstAddr(cmdHead.size.getWidth, w, burstSizeBytes))
    size.bits := cmdHead.size
    i.bits.size := size.burstTag + (size.burstOffset != 0.U)
    i.bits.isWr := cmdHead.isWr
  }

  val wrespReadyMux = Module(new MuxN(Bool(), storeStreamInfo.size))
  wrespReadyMux.io.sel := io.dram.wresp.bits.streamId
  io.dram.wresp.ready := wrespReadyMux.io.out

  val dramReady = io.dram.cmd.ready

  val sparseLoads = loadStreamInfo.zipWithIndex.filter { case (s, i) => s.isSparse }
  val denseLoads = loadStreamInfo.zipWithIndex.filterNot { case (s, i) => s.isSparse }
  val gatherBuffers = sparseLoads.map { case (s, i) =>
    val w = s.w
    val v = s.v
    val m = Module(new GatherBuffer(w, d, v, burstSizeBytes, addrWidth, cmdHead, io.dram.rresp.bits))
    m.io.rresp.valid := io.dram.rresp.valid & (io.dram.rresp.bits.streamId === i.U)
    m.io.rresp.bits := io.dram.rresp.bits
    m.io.cmd.valid := cmdRead & cmdArbiter.io.tag === i.U & dramReady
    m.io.cmd.bits := cmdHead

    dramCmdMux.io.ins(i).valid := cmdRead & ~m.io.fifo.full & ~m.io.hit

    rrespReadyMux.io.ins(i) := true.B
    cmdDeqValidMux.io.ins(i) := ~m.io.fifo.full & dramReady

    val stream = io.app.loads(i)
    stream.rdata.bits := m.io.fifo.deq
    stream.rdata.valid := m.io.complete
    m.io.fifo.deqVld := stream.rdata.ready
    m
  }

  // TODO: THIS CURRENTLY ASSUMES THE MEMORY CONTROLLER HANDS BACK DATA IN THE ORDER IT WAS REQUESTED
  // IT SHOULD PROBABLY BE SWITCHED TO THE STYLE USED BY THE GATHER BUFFERS
  val denseLoadBuffers = denseLoads.map { case (s, i) =>
    val m = Module(new FIFOWidthConvert(32, io.dram.rresp.bits.rdata.size, s.w, s.v, d))
    m.io.enq := io.dram.rresp.bits.rdata
    m.io.enqVld := io.dram.rresp.valid & (io.dram.rresp.bits.streamId === i.U)

    rrespReadyMux.io.ins(i) := ~m.io.full
    cmdDeqValidMux.io.ins(i) := dramReady

    dramCmdMux.io.ins(i).valid := cmdRead

    val stream = io.app.loads(i)
    stream.rdata.bits := m.io.deq
    stream.rdata.valid := ~m.io.empty
    m.io.deqVld := stream.rdata.ready
    m
  }

  val sparseStores = storeStreamInfo.zipWithIndex.filter { case (s, i) => s.isSparse }
  val denseStores = storeStreamInfo.zipWithIndex.filterNot { case (s, i) => s.isSparse }

  val scatterBuffers = sparseStores.map { case (s, i) =>
    val j = storeStreamId(i)

    val m = Module(new ScatterBuffer(w, d, v, burstSizeBytes, addrWidth, sizeWidth, io.dram.rresp.bits))
    val wdata = Module(new FIFOCore(UInt(s.w.W), d, s.v))
    val stream = io.app.stores(i)
    val write = cmdWrite & (cmdArbiter.io.tag === j.U)
    val issueRead = ~m.io.complete & write & ~m.io.fifo.full & ~wdata.io.empty & ~m.io.hit
    val skipRead = write & m.io.hit & ~wdata.io.empty
    val issueWrite = m.io.complete & (cmdArbiter.io.tag === j.U)

    val deqCmd = skipRead | (issueRead & dramReady)
    wdata.io.config.chainRead := true.B
    wdata.io.config.chainWrite := true.B
    wdata.io.enqVld := stream.wdata.valid
    wdata.io.enq := stream.wdata.bits
    wdata.io.deqVld := deqCmd
    stream.wdata.ready := ~wdata.io.full

    cmdDeqValidMux.io.ins(j) := deqCmd

    val dramCmd = dramCmdMux.io.ins(j)
    val addr = Wire(new BurstAddr(addrWidth, w, burstSizeBytes))
    addr.bits := Mux(issueRead, cmdHead.addr, m.io.fifo.deq(0).cmd.addr)
    dramCmd.bits.addr := addr.burstAddr
    dramCmd.bits.rawAddr := addr.bits
    dramCmd.bits.tag := Mux(issueRead, addr.burstTag, m.io.fifo.deq(0).count)
    dramCmd.bits.isWr := issueWrite
    val size = Wire(new BurstAddr(cmdHead.size.getWidth, w, burstSizeBytes))
    size.bits := Mux(issueRead, cmdHead.size, m.io.fifo.deq(0).cmd.size)
    dramCmd.bits.size := size.burstTag + (size.burstOffset != 0.U)
    dramCmdMux.io.ins(j).valid := issueRead | (issueWrite & wdataValid & ~dramReadySeen)

    m.io.rresp.valid := io.dram.rresp.valid & (io.dram.rresp.bits.streamId === j.U)
    m.io.rresp.bits := io.dram.rresp.bits
    m.io.fifo.enqVld := deqCmd
    m.io.fifo.enq(0).data.foreach { _ := wdata.io.deq(0) }
    m.io.fifo.enq(0).cmd := cmdHead
    m.io.fifo.deqVld := burstCounter.io.done

    wdataMux.io.ins(i).valid := issueWrite
    wdataMux.io.ins(i).bits.wdata := m.io.fifo.deq(0).data

    val wrespFIFO = Module(new FIFOCore(UInt(w.W), d, 1))
    wrespFIFO.io.enq(0) := io.dram.wresp.bits.tag
    wrespFIFO.io.enqVld := io.dram.wresp.valid & (io.dram.wresp.bits.streamId === j.U)
    wrespReadyMux.io.ins(i) := ~wrespFIFO.io.full

    val count = Module(new Counter(w))
    count.io.max := ~(0.U(w.W))
    // send a response after at least 16 sparse writes have completed
    val sendResp = count.io.out >= v.U
    val deqRespCount = ~wrespFIFO.io.empty & ~sendResp
    count.io.stride := Mux(sendResp, v.U(w.W), wrespFIFO.io.deq(0))
    count.io.dec := sendResp
    count.io.enable := (sendResp & stream.wresp.ready) | deqRespCount
    count.io.reset := false.B
    count.io.saturate := false.B

    stream.wresp.bits := sendResp
    stream.wresp.valid := sendResp
    wrespFIFO.io.deqVld := deqRespCount

    m
  }
  // force command arbiter to service scatter buffers when data is waiting to be written back to memory
  sparseStores.headOption match {
    case Some((s, i)) =>
      val scatterReadys = scatterBuffers.map { ~_.io.fifo.empty }
      cmdArbiter.io.forceTag.valid := scatterReadys.reduce { _|_ }
      cmdArbiter.io.forceTag.bits := PriorityEncoder(scatterReadys) + storeStreamId(i).U
    case None =>
  }

  val denseStoreBuffers = denseStores.map { case (s, i) =>
    val j = storeStreamId(i)
    val m = Module(new FIFOWidthConvert(s.w, s.v, 32, 16, d))
    val stream = io.app.stores(i)

    cmdDeqValidMux.io.ins(j) := burstCounter.io.done

    dramCmdMux.io.ins(j).valid := cmdWrite & wdataValid & ~dramReadySeen

    m.io.enqVld := stream.wdata.valid
    m.io.enq := stream.wdata.bits
    m.io.deqVld := cmdWrite & ~m.io.empty & io.dram.wdata.ready & (cmdArbiter.io.tag === j.U)

    wdataMux.io.ins(i).valid := cmdWrite & ~m.io.empty
    wdataMux.io.ins(i).bits.wdata := m.io.deq
    stream.wdata.ready := ~m.io.full

    val wrespFIFO = Module(new FIFOCounter(d, 1))
    wrespFIFO.io.enq(0) := io.dram.wresp.valid
    wrespFIFO.io.enqVld := io.dram.wresp.valid & (io.dram.wresp.bits.streamId === j.U)
    wrespReadyMux.io.ins(i) := ~wrespFIFO.io.full
    stream.wresp.bits  := wrespFIFO.io.deq(0)
    stream.wresp.valid := ~wrespFIFO.io.empty
    wrespFIFO.io.deqVld := stream.wresp.ready

    m
  }

  val dramValid = io.dram.cmd.valid
  burstCounter.io.max := Mux(io.dram.cmd.bits.isWr, io.dram.cmd.bits.size, 1.U)
  burstCounter.io.stride := 1.U
  burstCounter.io.reset := false.B
  burstCounter.io.enable := Mux(io.dram.cmd.bits.isWr, wdataValid & wdataReady, dramValid  & dramReady)
  burstCounter.io.saturate := false.B

  // strictly speaking this isn't necessary, but the DRAM part of the test bench expects unique tags
  // and sometimes apps make requests to the same address so tagging with the address isn't enough to guarantee uniqueness
  burstTagCounter.io.max := (numOutstandingBursts - 1).U
  burstTagCounter.io.stride := 1.U
  burstTagCounter.io.reset := false.B
  burstTagCounter.io.enable := dramValid  & dramReady
  burstTagCounter.io.saturate := false.B


  val dramReadyFF = Module(new FFType(UInt(1.W)))
  dramReadyFF.io.init := 0.U
  dramReadyFF.io.enable := burstCounter.io.done | (dramValid  & io.dram.cmd.bits.isWr)
  dramReadyFF.io.in := Mux(burstCounter.io.done, 0.U, dramReady | dramReadySeen)
  dramReadySeen := dramReadyFF.io.out
  cmdArbiter.io.deqVld := cmdDeqValidMux.io.out

  io.dram.wdata.bits := wdataMux.io.out.bits
  io.dram.wdata.valid := wdataMux.io.out.valid

  io.dram.cmd.bits := dramCmdMux.io.out.bits
  io.dram.cmd.valid := dramCmdMux.io.out.valid

  if (debug) {
  val signalLabels = ListBuffer[String]()
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
