package chisel_test

import chisel3._
import chisel3.util._
import chisel3.iotesters.{PeekPokeTester, Driver}

class RGBConvertChisel(val idw: Int, val odw: Int, val iew: Int, val oew: Int) extends Module {
  val io = IO(new Bundle {
        // Video Stream Inputs
      val stream_in_data            = Input(UInt(idw.W))
      val stream_in_startofpacket   = Input(Bool())
      val stream_in_endofpacket     = Input(Bool())
      val stream_in_empty           = Input(UInt(iew.W))
      val stream_in_valid           = Input(Bool()) 
      val stream_out_ready          = Input(Bool())
       
      // Video Stream Outputs
      val stream_in_ready           = Output(Bool())
      val stream_out_data           = Output(UInt(odw.W))
      val stream_out_startofpacket  = Output(Bool())
      val stream_out_endofpacket    = Output(Bool())
      val stream_out_empty          = Output(UInt(oew.W))
      val stream_out_valid          = Output(Bool())
  }) 

  io.stream_in_ready := io.stream_out_ready | ~io.stream_out_valid
  val r = Cat(io.stream_in_data(23, 16), io.stream_in_data(23, 22))
  val g = Cat(io.stream_in_data(15, 8), io.stream_in_data(15,14))
  val b = Cat(io.stream_in_data(7, 0), io.stream_in_data(7, 6))
  val converted_data = Cat(r(9, 5), g(9, 4), b(9, 5))

  // reset logic
  when (reset) {
    io.stream_out_data           := 0.U 
    io.stream_out_startofpacket  := 0.U
    io.stream_out_endofpacket    := 0.U
    io.stream_out_empty          := 0.U
    io.stream_out_valid          := 0.U
  } .elsewhen (io.stream_out_ready | ~io.stream_out_valid) {
    io.stream_out_data           := converted_data
    io.stream_out_startofpacket  := io.stream_in_startofpacket
    io.stream_out_endofpacket    := io.stream_in_endofpacket
    io.stream_out_empty          := io.stream_in_empty 
    io.stream_out_valid          := io.stream_in_valid 
  }
}

class RGBConvertChiselTests(c: RGBConvertChisel) extends PeekPokeTester(c) {
  step(1)
} 

 object RGBConvertChisel {
   def main(args: Array[String]): Unit = {
     if (!Driver(() => new RGBConvertChisel(24, 16, 2, 1))(c => new RGBConvertChiselTests(c))) System.exit(1)
   }
 }
