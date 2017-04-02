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
  val io_numMemoryStreams = 2 + 0
  val io_numArgIns_mem = 2 + 0
  // Scalars
  val io_numArgIns_reg = 1
  val io_numArgOuts_reg = 1
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
    // Video Stream Inputs 
    val stream_in_data            = Input(UInt(24.W))
    val stream_in_startofpacket   = Input(Bool())
    val stream_in_endofpacket     = Input(Bool())
    val stream_in_empty           = Input(UInt(2.W))
    val stream_in_valid           = Input(Bool()) 
    val stream_out_ready          = Input(Bool())
     
    // Video Stream Outputs
    val stream_in_ready           = Output(Bool())
    val stream_out_data           = Output(UInt(16.W))
    val stream_out_startofpacket  = Output(Bool())
    val stream_out_endofpacket    = Output(Bool())
    val stream_out_empty          = Output(UInt(1.W))
    val stream_out_valid          = Output(Bool())
    
  })
}
