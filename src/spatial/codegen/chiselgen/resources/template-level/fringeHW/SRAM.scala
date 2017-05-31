package fringe

import chisel3._
import chisel3.core.IntParam
import chisel3.util._
import templates.Utils.log2Up


class SRAMVerilog(val w: Int, val d: Int) extends BlackBox(
  Map("DWIDTH" -> IntParam(w), "WORDS" -> IntParam(d), "AWIDTH" -> IntParam(log2Up(d))))
{
  val addrWidth = log2Up(d)
  val io = IO(new Bundle {
    val clk = Input(Clock())
    val raddr = Input(UInt(addrWidth.W))
    val waddr = Input(UInt(addrWidth.W))
    val raddrEn = Input(Bool())
    val waddrEn = Input(Bool())
    val wen = Input(Bool())
    val wdata = Input(UInt(w.W))
    val rdata = Output(UInt(w.W))
  })
}

class FFRAM(val w: Int, val d: Int) extends Module {
  val addrWidth = log2Up(d)
  val io = IO(new Bundle {
    val raddr = Input(UInt(addrWidth.W))
    val wen = Input(Bool())
    val waddr = Input(UInt(addrWidth.W))
    val wdata = Input(UInt(w.W))
    val rdata = Output(UInt(w.W))
  })

  val rf = Module(new RegFilePure(w, d))
  rf.io.raddr := RegNext(io.raddr, 0.U)
  rf.io.wen := io.wen
  rf.io.waddr := io.waddr
  rf.io.wdata := io.wdata
  io.rdata := rf.io.rdata
}

class SRAM(val w: Int, val d: Int) extends Module {
  val addrWidth = log2Up(d)
  val io = IO(new Bundle {
    val raddr = Input(UInt(addrWidth.W))
    val wen = Input(Bool())
    val waddr = Input(UInt(addrWidth.W))
    val wdata = Input(UInt(w.W))
    val rdata = Output(UInt(w.W))
  })

  val mem = Module(new FFRAM(w, d))
  mem.io.raddr := io.raddr
  mem.io.wen := io.wen
  mem.io.waddr := io.waddr
  mem.io.wdata := io.wdata
  io.rdata := mem.io.rdata
}


//class SRAM(val w: Int, val d: Int) extends Module {
//  val addrWidth = log2Up(d)
//  val io = IO(new Bundle {
//    val raddr = Input(UInt(addrWidth.W))
//    val wen = Input(Bool())
//    val waddr = Input(UInt(addrWidth.W))
//    val wdata = Input(UInt(w.W))
//    val rdata = Output(UInt(w.W))
//  })
//
//  val mem = Module(new SRAMVerilog(w, d))
//  mem.io.clk := clock
//  mem.io.raddr := io.raddr
//  mem.io.wen := io.wen
//  mem.io.waddr := io.waddr
//  mem.io.wdata := io.wdata
//  mem.io.raddrEn := true.B
//  mem.io.waddrEn := true.B
//  io.rdata := mem.io.rdata
//}

//class SRAM(val w: Int, val d: Int) extends Module {
//  val addrWidth = log2Up(d)
//  val io = IO(new Bundle {
//    val raddr = Input(UInt(addrWidth.W))
//    val wen = Input(Bool())
//    val waddr = Input(UInt(addrWidth.W))
//    val wdata = Input(UInt(w.W))
//    val rdata = Output(UInt(w.W))
//  })
//
//  // Note: Cannot use SeqMem here because the 'pokeAt' method used in
//  // simulation to load the Mem is defined only on 'Mem'.
//  val mem = Mem(d, UInt(w.W))
//  io.rdata := 0.U
//  val raddr_reg = Reg(UInt(addrWidth.W))
//  val raddr_reg2 = Reg(UInt(addrWidth.W))
//
//  when (io.wen) {
//    mem(io.waddr) := io.wdata
//  }
//  raddr_reg := io.raddr
//  raddr_reg2 := raddr_reg
//  io.rdata := mem(raddr_reg2)
//}

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


