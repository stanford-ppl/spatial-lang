package fringe

import chisel3._
import chisel3.util._
import templates.Utils.log2Up

/**
 * Regfile: Regfile parameterized by width and height similar to SRAM
 * @param w: Word width
 * @param d: Number of registers
 * @param numArgIns: Number of 'argin' registers that can be read in parallel
 * @param numArgOuts: Number of 'argOut' registers that can be written to in parallel
 */
class RegFile(val w: Int, val d: Int, val numArgIns: Int = 0, val numArgOuts: Int = 0) extends Module {
  val addrWidth = log2Up(d)
  val argInRange = List(0, 1) ++ ((2) until (2 + numArgIns - 2)).toList
  val argOutRange = List(1) ++ ((numArgIns) until (numArgIns + numArgOuts - 1)).toList

  // Helper function to convert an argOut index into
  // register index. Used in the unit test
  def argOut2RegIdx(argOut: Int) = {
    argOutRange(argOut)
  }

  // Helper function to convert reg index to argOut index.
  def regIdx2ArgOut(regIdx: Int) = {
    argOutRange.indexOf(regIdx)
  }

  val io = IO(new Bundle {
    val raddr = Input(UInt(addrWidth.W))
    val wen  = Input(Bool())
    val waddr = Input(UInt(addrWidth.W))
    val wdata = Input(Bits(w.W))
    val rdata = Output(Bits(w.W))
    val argIns = Output(Vec(numArgIns, (UInt(w.W))))
    val argOuts = Vec(numArgOuts, Flipped(Decoupled((UInt(w.W)))))
  })

  // Sanity-check module parameters
  Predef.assert(numArgIns >= 0, s"Invalid numArgIns ($numArgIns): must be >= 0.")
  Predef.assert(numArgOuts >= 0, s"Invalid numArgOuts ($numArgOuts): must be >= 0.")
  Predef.assert(numArgIns <= d, s"numArgIns ($numArgIns) must be less than number of registers ($d)!")
  Predef.assert(numArgOuts <= d, s"numArgOuts ($numArgOuts) must be less than number of registers ($d)!")

  val regs = List.tabulate(d) { i =>
    val ff = Module(new FF(w))
    ff.io.in := (if (argOutRange contains i) Mux(io.argOuts(regIdx2ArgOut(i)).valid, io.argOuts(regIdx2ArgOut(i)).bits, io.wdata) else io.wdata)
    ff.io.enable := (if (argOutRange contains i) io.argOuts(argOutRange.indexOf(i)).valid | (io.wen & (io.waddr === i.U)) else io.wen & (io.waddr === i.U))
    ff.io.init := 0.U
    ff
  }

  val rport = Module(new MuxN(d, w))
  val regOuts = Vec(regs.map{_.io.out})
  rport.io.ins := regOuts
  rport.io.sel := io.raddr
  io.rdata := rport.io.out

  io.argIns := Vec(regOuts.zipWithIndex.filter { case (arg, idx) => argInRange.contains(idx) }.map {_._1})
}
