package fringe

import chisel3._
import chisel3.util._
import scala.language.reflectiveCalls
import templates.Utils.log2Up

/**
 * CoalescingCache memory that supports various banking modes
 * and double buffering
 * @param w: Word width in bits
 * @param d: Depth
 * @param v: Vector width
 */
class CoalescingCache(val w: Int, val d: Int, val v: Int) extends Module {
  val addrWidth = log2Up(d)
  val wordSizeBytes = w/8
  val burstSizeBytes = 64
  val burstSizeWords = burstSizeBytes / wordSizeBytes
  val taglen = w - log2Up(burstSizeBytes)
  val metadataWidth = 8

  val io = IO(new Bundle {
    val raddr = Input(UInt(w.W))
    val readEn = Input(Bool())
    val rdata = Output(Vec(burstSizeWords, UInt(w.W)))
    val rmetaData = Output(Vec(burstSizeWords, UInt(metadataWidth.W)))
    val waddr = Input(UInt(w.W))
    val wen = Input(Bool())
    val wdata = Input(Bits(w.W))
    val position = Input(UInt(log2Up(burstSizeWords).W))
    val isScatter = Input(Bool())
    val miss = Output(Bool())
    val full = Output(Bool())
  })

  // Check for sizes and v
  Predef.assert(isPow2(d), s"Unsupported scratchpad size $d; must be a power-of-2")
  Predef.assert(wordSizeBytes * v == burstSizeBytes, s"wordSize * parallelism must be equal to burst size in bytes")
  Predef.assert(isPow2(v), s"Unsupported banking number $v; must be a power-of-2")

  // Split raddr and waddr into (burstAddr, wordOffset)
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
    val wordOffset = log2Up(wordSizeBytes)
    addr(burstOffset-1, wordOffset)
  }

  val writeIdx = Wire(UInt(log2Up(d).W))
  val readHit = Wire(Vec(d, Bool()))
  val writeHit = Wire(Vec(d, Bool()))
  val full = Wire(Bool())
  val miss = Wire(Bool())

  // Create valid array
  val valid = List.tabulate(d) { i =>
    val vld = Module(new FF(1))
    vld.io.init := 0.U // Initialize to invalid (0) state
    vld.io.in := Mux(readHit(i), 0.U, 1.U) // Read + evict
    vld.io.enable := (io.wen & ~full & writeIdx === i.U) | readHit(i) // Read + evict
    vld
  }

  val wburstAddr = extractBurstAddr(io.waddr)
  val rburstAddr = extractBurstAddr(io.raddr)

  // Create tag array, populate readHit table
  val tags = List.tabulate(d) { i =>
    val ff = Module(new FF(taglen))
    ff.io.init := 0.U
    ff.io.in := wburstAddr
    ff.io.enable := (io.wen & writeIdx === i.U)
    readHit(i) := io.readEn & valid(i).io.out & (rburstAddr === ff.io.out)
    writeHit(i) := io.wen & valid(i).io.out & (wburstAddr === ff.io.out)
    ff
  }

  val freeBitVector = valid.map { ~_.io.out }.reverse.reduce { Cat(_,_) }
  val writeHitIdx = PriorityEncoder(writeHit.map { _.asUInt }.reverse.reduce { Cat(_,_) })
  val writeMissIdx = PriorityEncoder(freeBitVector)
  writeIdx := Mux(miss, writeMissIdx, writeHitIdx)
  val readIdx = PriorityEncoder(readHit.map { _.asUInt }.reverse.reduce { Cat(_,_) })

  miss := io.wen & ~(writeHit.reduce {_|_})
  full := valid.map { _.io.out }.reduce {_&_}
  io.miss := miss
  io.full := full

  // Metadata SRAM: Stores an index corresponding to each word in the incoming burst
  // This index is used to reorder the required words as required by the address pattern
  // Word size: 16 bytes (1 byte per burst)
  val metadata = Module(new SRAMByteEnable(metadataWidth, burstSizeWords, d))
  metadata.io.raddr := readIdx
  io.rmetaData := metadata.io.rdata
  metadata.io.waddr := writeIdx
  metadata.io.wen := io.wen & ~full
  // If this is a write miss, wipe out entire line to avoid stale entries
  metadata.io.mask := Vec(List.tabulate(burstSizeWords) { i => miss | UIntToOH(io.position)(i) })
  val woffset = Wire(UInt(log2Up(burstSizeWords).W))
  woffset := extractWordOffset(io.waddr)
  val wvalid = 1.U
  val md = Vec.fill(burstSizeWords){ 0.U(metadataWidth.W) }
  val windex = io.position
  md(windex) := Cat(wvalid, woffset)
  metadata.io.wdata := md

  // Scatter data SRAM. Word size: 64 bytes
  val scatterData = Module(new SRAMByteEnable(w, burstSizeWords, d))
  scatterData.io.raddr := readIdx
  io.rdata := scatterData.io.rdata
  scatterData.io.waddr := writeIdx
  scatterData.io.wen := io.wen & ~full & io.isScatter
  scatterData.io.mask := Vec(List.tabulate(burstSizeWords) { i => UIntToOH(io.position)(i) })
  val sd = Vec(List.fill(burstSizeWords){ 0.U })
  sd(windex) := io.wdata
  scatterData.io.wdata := sd
}

