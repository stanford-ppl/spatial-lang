package templates

import ops._
import chisel3._
import chisel3.util
import chisel3.util.Mux1H
import scala.math._



class SystolicArray(val dims: List[Int], val bitWidth: Int = 32) extends Module {
  def this(tuple: (List[Int], Int)) = this(tuple._1, tuple._2)

  val io = IO( new Bundle {
    val in = Input(UInt(bitWidth.W))
    // val in = Vec(pW*-*numWriters, Input(UInt(bitWidth.W)))
    // val out = Vec(pR*-*numReaders, Output(UInt(bitWidth.W)))
    // val enq = Vec(numWriters, Input(Bool()))
    // val deq = Vec(numReaders, Input(Bool()))
    // val numel = Output(UInt(32.W))
    // val almostEmpty = Output(Bool())
    // val almostFull = Output(Bool())
    // val empty = Output(Bool())
    // val full = Output(Bool())
    // val debug = new Bundle {
    //   val overwrite = Output(Bool())
    //   val overread = Output(Bool())
    //   val error = Output(Bool())
    // }
  })

}
