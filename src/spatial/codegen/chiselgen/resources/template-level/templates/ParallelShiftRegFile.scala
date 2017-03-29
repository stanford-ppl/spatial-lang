package templates

import chisel3._
import chisel3.util
import scala.collection.mutable.HashMap
import templates.Utils.log2Up

// This exposes all registers as output ports now

class ParallelShiftRegFile(val height: Int, val width: Int, val stride: Int, val wPar: Int = 1) extends Module {

  def this(tuple: (Int, Int, Int, Int)) = this(tuple._1, tuple._2, tuple._3, tuple._4)
  val io = IO(new Bundle { // TODO: follow io.input and io.output convention
    val data_in  = Vec(wPar*height, Input(UInt(32.W)))
    val w_rowAddr   = Vec(wPar*height, Input(UInt(log2Up(((width+stride-1)/stride)*stride + 1).W)))
    val w_colAddr   = Vec(wPar*height, Input(UInt(log2Up(((width+stride-1)/stride)*stride + 1).W)))
    val w_en     = Vec(wPar*height, Input(Bool()))
    val shift_en = Vec(height, Input(Bool()))
    val reset    = Input(Bool())
    val data_out = Vec(width*height, Output(UInt(32.W)))
  })
  
  val size_rounded_up = ((width+stride-1)/stride)*stride // Unlike shift reg, for shift reg file it is user's problem if width does not match (no functionality guarantee)
  val registers = List.fill(height*size_rounded_up)(Reg(UInt(32.W))) // Note: Can change to use FF template
  for (i <- 0 until width) {
    for (j <- 0 until height) {
      io.data_out(j * width + i) := registers(j * width + i)
    }
  }

  when(io.reset) {
    for (i <- 0 until (height*size_rounded_up)) {
      registers(i) := 0.U(32.W)
    }
  } .elsewhen(io.shift_en.reduce{_|_}) {
    for (i <- 0 until (stride)) {
      for (row <- 0 until height) {
        registers(row*size_rounded_up + i) := Mux(io.shift_en(row), io.data_in(i), registers(row*size_rounded_up + i))
      }
    }
    for (i <- stride until (size_rounded_up)) {
      for (row <- 0 until height) {
        registers(row*size_rounded_up + i) := Mux(io.shift_en(row), registers(row*size_rounded_up + i-stride), registers(row*size_rounded_up + i))
      }
    }
  } .elsewhen(io.w_en.reduce{_|_}) { // TODO: Assume we only write to one place at a time
    val activeEn = io.w_en.reduce{_|_}
    val activeRowAddr = chisel3.util.Mux1H(io.w_en, io.w_rowAddr)
    val activeColAddr = chisel3.util.Mux1H(io.w_en, io.w_colAddr)
    val activeData = chisel3.util.Mux1H(io.w_en, io.data_in)
    for (i <- 0 until width) { // Note here we just use width, i.e. if width doesn't match, user will get unexpected answer
      for (j <- 0 until height) { 
        when(j.U === activeRowAddr & i.U === activeColAddr) {
          registers(j*size_rounded_up + i) := activeData
        }
      }
    }
  }
  
  for (i <- 0 until width) {
    for (j <- 0 until height) {
      io.data_out(j * width + i) := registers((size_rounded_up - width + i) + j*size_rounded_up) // FIXME: not sure about row calcutation  
    }
  }

  // var wIdMap = (0 until numBufs).map{ i => (i -> 0) }.toMap
  var wId = 0
  def connectWPort(data: UInt, row_addr: UInt, col_addr: UInt, en: Bool) {
    io.data_in(wId) := data
    io.w_en(wId) := en
    io.w_rowAddr(wId) := row_addr
    io.w_colAddr(wId) := col_addr
    wId = wId + 1
  }

  def connectShiftPort(data: UInt, row_addr: UInt, en: Bool) {
    io.data_in(wId) := data
    for (j <- 0 until height) {
      io.shift_en(j) := en & row_addr === j.U
    }
  }

  def readValue(row_addr: UInt, col_addr:UInt): UInt = {
    val result = Wire(UInt(32.W))
    val regvals = (0 until width*height).map{ i => 
      (i.U -> io.data_out(i)) 
    }
    result := chisel3.util.MuxLookup(row_addr*width.U + col_addr, 0.U, regvals)
    result
  }


  
}
