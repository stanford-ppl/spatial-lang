package fringe

import chisel3._
import chisel3.core.IntParam
import chisel3.util._
import templates.Utils.log2Up


class SRAMVerilogSim(val w: Int, val d: Int) extends BlackBox(
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

class SRAMVerilogAWS(val w: Int, val d: Int) extends BlackBox(
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

class SRAMVerilogDE1SoC(val w: Int, val d: Int) extends BlackBox(
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

abstract class GenericRAM(val w: Int, val d: Int) extends Module {
  val addrWidth = log2Up(d)
  val io = IO(new Bundle {
    val raddr = Input(UInt(addrWidth.W))
    val wen = Input(Bool())
    val waddr = Input(UInt(addrWidth.W))
    val wdata = Input(UInt(w.W))
    val rdata = Output(UInt(w.W))
  })

}

class FFRAM(override val w: Int, override val d: Int) extends GenericRAM(w, d) {
  val rf = Module(new RegFilePure(w, d))
  rf.io.raddr := RegNext(io.raddr, 0.U)
  rf.io.wen := io.wen
  rf.io.waddr := io.waddr
  rf.io.wdata := io.wdata
  io.rdata := rf.io.rdata
}

class SRAM(override val w: Int, override val d: Int) extends GenericRAM(w, d) {
  // Customize SRAM here
  FringeGlobals.target match {
    case "aws" | "zynq" | "zcu" =>
      val mem = Module(new SRAMVerilogAWS(w, d))
      mem.io.clk := clock
      mem.io.raddr := io.raddr
      mem.io.wen := io.wen
      mem.io.waddr := io.waddr
      mem.io.wdata := io.wdata
      mem.io.raddrEn := true.B
      mem.io.waddrEn := true.B

      // Implement WRITE_FIRST logic here
      // equality register
      val equalReg = RegNext(io.wen & (io.raddr === io.waddr), false.B)
      val wdataReg = RegNext(io.wdata, 0.U)
      io.rdata := Mux(equalReg, wdataReg, mem.io.rdata)

    case "DE1" | "de1soc" =>
      val mem = Module(new SRAMVerilogDE1SoC(w, d))
      mem.io.clk := clock
      mem.io.raddr := io.raddr
      mem.io.wen := io.wen
      mem.io.waddr := io.waddr
      mem.io.wdata := io.wdata
      mem.io.raddrEn := true.B
      mem.io.waddrEn := true.B

    case _ =>
      val mem = Module(new SRAMVerilogSim(w, d))
      mem.io.clk := clock
      mem.io.raddr := io.raddr
      mem.io.wen := io.wen
      mem.io.waddr := io.waddr
      mem.io.wdata := io.wdata
      mem.io.raddrEn := true.B
      mem.io.waddrEn := true.B

      io.rdata := mem.io.rdata
  }
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

