package fringe

import chisel3._
import chisel3.util._
import templates.Utils.log2Up

class MuxN[T<:Data](val t: T, val numInputs: Int) extends Module {
  val numSelectBits = log2Up(numInputs)
  val io = IO(new Bundle {
    val ins = Input(Vec(numInputs, t.cloneType))
    val sel = Input(Bits(numSelectBits.W))
    val out = Output(t.cloneType)
  })

  io.out := io.ins(io.sel)
}

class MuxVec[T<:Data](val t: T, val numInputs: Int, v: Int) extends Module {
  val numSelectBits = log2Up(numInputs)
  val io = IO(new Bundle {
    val ins = Input(Vec(numInputs, Vec(v, t.cloneType)))
    val sel = Input(Bits(numSelectBits.W))
    val out = Output(Vec(v, t.cloneType))
  })

  io.out := io.ins(io.sel)
}

class MuxNReg(val numInputs: Int, w: Int) extends Module {
  val numSelectBits = log2Up(numInputs)
  val io = IO(new Bundle {
    val ins = Input(Vec(numInputs, Bits(w.W)))
    val sel = Input(Bits(numSelectBits.W))
    val out = Output(Bits(w.W))
  })

  // Register the inputs
  val ffins = List.tabulate(numInputs) { i =>
    val ff = Module(new FF(w))
    ff.io.enable := true.B
    ff.io.in := io.ins(i)
    ff
  }

  val ffsel = Module(new FF(numSelectBits))
  ffsel.io.enable := true.B
  ffsel.io.in := io.sel
  val sel = ffsel.io.out

  val mux = Module(new MuxN(UInt(w.W), numInputs))
  mux.io.ins := Vec.tabulate(numInputs) { i => ffins(i).io.out }
  mux.io.sel := sel

  // Register the output
  val ff = Module(new FF(w))
  ff.io.enable := true.B
  ff.io.in := mux.io.out
  io.out := ff.io.out
}

