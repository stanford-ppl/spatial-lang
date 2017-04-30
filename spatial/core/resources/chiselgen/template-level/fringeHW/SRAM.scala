package fringe

import chisel3._
import chisel3.util._
import templates.Utils.log2Up

class SRAM(val w: Int, val d: Int) extends Module {
  val addrWidth = log2Up(d)
  val io = IO(new Bundle {
    val raddr = Input(UInt(addrWidth.W))
    val wen = Input(Bool())
    val waddr = Input(UInt(addrWidth.W))
    val wdata = Input(UInt(w.W))
    val rdata = Output(UInt(w.W))
  })

  // Note: Cannot use SeqMem here because the 'pokeAt' method used in
  // simulation to load the Mem is defined only on 'Mem'.
  val mem = Mem(d, UInt(w.W))
  io.rdata := 0.U
  val raddr_reg = Reg(UInt(addrWidth.W))

  when (io.wen) {
    mem(io.waddr) := io.wdata
  }
  raddr_reg := io.raddr
  io.rdata := mem(raddr_reg)
}

/**
 * SRAM with byte enable: TO BE USED IN SIMULATION ONLY
 * @param mask: Vec of Bool mask in little-to-big-byte order
 * I.e., mask(0) corresponds to byte enable of least significant byte
 */
class SRAMByteEnable(val w: Int, val n: Int, val d: Int) extends Module {
  val addrWidth = log2Up(d)

  val io = IO(new Bundle {
    val raddr = Input(UInt(addrWidth.W))
    val wen = Input(Bool())
    val mask = Input(Vec(n, Bool()))
    val waddr = Input(UInt(addrWidth.W))
    val wdata = Input(Vec(n, UInt(w.W)))
    val rdata = Output(Vec(n, UInt(w.W)))
  })

  val mem = SeqMem(d, Vec(n, Bits(w.W)))
  io.rdata := Vec.fill(n) { 0.U }
  when (io.wen) { mem.write(io.waddr, io.wdata, io.mask) }

  io.rdata := mem.read(io.raddr)
}

