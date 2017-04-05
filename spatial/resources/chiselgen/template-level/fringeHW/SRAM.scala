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

///**
// * SRAM with byte enable: TO BE USED IN SIMULATION ONLY
// * @param mask: Vec of Bool mask in little-to-big-byte order
// * I.e., mask(0) corresponds to byte enable of least significant byte
// */
//class SRAMByteEnable(val w: Int, val d: Int) extends Module {
//  val addrWidth = log2Up(d)
//  val numBytes = w/8
//  Predef.assert(w%8 == 0, s"Invalid word width $w in SRAMByteEnable: Must be a multiple of 8")
//  val io = new Bundle {
//    val raddr = UInt(INPUT, width = addrWidth)
//    val wen = Bool(INPUT)
//    val mask = Vec.fill(numBytes) { Bool(INPUT) }
//    val waddr = UInt(INPUT, width = addrWidth)
//    val wdata = Bits(INPUT, width = w)
//    val rdata = Bits(OUTPUT, width = w)
//  }
//  val mem = Mem(Bits(width = w), d, seqRead = true)
//  io.rdata := Bits(0)
//  val raddr_reg = Reg(Bits(width = addrWidth))
//  when (io.wen) {
//    val tmp = mem(io.waddr)
//    val tmpVec = Vec.tabulate(numBytes) { i => tmp(i*8+8-1, i*8) }
//    val newVec = Vec.tabulate(numBytes) { i => io.wdata(i*8+8-1, i*8) }
//    val wdata = Vec.tabulate(numBytes) { i =>
//      val byteEn = io.mask(i)
//      Mux(byteEn, newVec(i), tmpVec(i))
//    }.reverse.reduce { Cat(_,_) }
//    mem(io.waddr) := wdata
//  }
//  raddr_reg := io.raddr
//
//  io.rdata := mem(raddr_reg)
//}


