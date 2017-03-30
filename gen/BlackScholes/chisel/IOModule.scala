package accel
import chisel3._
import templates._
import chisel3.util._
import fringe._
import types._
trait IOModule extends Module {
  val target = "" // TODO: Get this info from command line args (aws, de1, etc)
  val io_w = 32 // TODO: How to generate these properly?
  val io_v = 16 // TODO: How to generate these properly?
  // Tile Load
  val io_numMemoryStreams = 6 + 1
  val io_numArgIns_mem = 6 + 1
  // Scalars
  val io_numArgIns_reg = 1
  val io_numArgOuts_reg = 0
  val io_numArgIns = io_numArgIns_reg + io_numArgIns_mem
  val io_numArgOuts = io_numArgOuts_reg
  val io = IO(new Bundle {
    // Control
    val enable = Input(Bool())
    val done = Output(Bool())
    
    // Tile Load
    val memStreams = Vec(io_numMemoryStreams, Flipped(new MemoryStream(io_w, io_v)))
    
    // Scalars
    val argIns = Input(Vec(io_numArgIns, UInt(io_w.W)))
    val argOuts = Vec(io_numArgOuts, Decoupled((UInt(io_w.W))))
    
  })
}
