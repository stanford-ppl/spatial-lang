package accel
import templates._
import types._
import chisel3._
trait x4244_UnitPipe extends x4350 {
  // Controller Stack: Stack(x4350, x4469, x4470)
  val x4225_$anonfun_wdata = Wire(Vec(1, UInt(32.W)))
  val x4225_$anonfun_readEn = Wire(Bool())
  val x4225_$anonfun_writeEn = Wire(Bool())
  val x4225_$anonfun_rdata = x4225_$anonfun.io.out
  x4225_$anonfun.io.in := x4225_$anonfun_wdata
  x4225_$anonfun.io.pop := x4225_$anonfun_readEn
  x4225_$anonfun.io.push := x4225_$anonfun_writeEn
  // New stream in x4226
  //  ---- INNER: Begin Streaminner x4232_UnitPipe Controller ----
  val x4232_UnitPipe_offset = 0 // TODO: Compute real delays
  val x4232_UnitPipe_sm = Module(new Streaminner(1 /*TODO: don't need*/, false))
  x4232_UnitPipe_sm.io.input.enable := x4232_UnitPipe_en;
  x4232_UnitPipe_done := Utils.delay(x4232_UnitPipe_sm.io.output.done, x4232_UnitPipe_offset)
  val x4232_UnitPipe_rst_en = x4232_UnitPipe_sm.io.output.rst_en // Generally used in inner pipes
  val x4232_UnitPipe_datapath_en = x4232_UnitPipe_en
  // ---- Single Iteration for Streaminner x4232_UnitPipe ----
  x4232_UnitPipe_sm.io.input.ctr_done := Utils.delay(x4232_UnitPipe_en, 1 + x4232_UnitPipe_offset) // stream kiddo
  val x4232_UnitPipe_ctr_en = x4232_UnitPipe_done // stream kiddo
  x4232_UnitPipe_sm.io.input.forever := false.B
  // Creating sub kernel x4232_UnitPipe
  // Connect streams to ports on mem controller
  // HACK: Assume load is par=16
  val x4226_data = Vec(List(Utils.FixedPoint(true,32,0,io.memStreams(6).rdata.bits(0)),Utils.FixedPoint(true,32,0,io.memStreams(6).rdata.bits(1)),Utils.FixedPoint(true,32,0,io.memStreams(6).rdata.bits(2)),Utils.FixedPoint(true,32,0,io.memStreams(6).rdata.bits(3)),Utils.FixedPoint(true,32,0,io.memStreams(6).rdata.bits(4)),Utils.FixedPoint(true,32,0,io.memStreams(6).rdata.bits(5)),Utils.FixedPoint(true,32,0,io.memStreams(6).rdata.bits(6)),Utils.FixedPoint(true,32,0,io.memStreams(6).rdata.bits(7)),Utils.FixedPoint(true,32,0,io.memStreams(6).rdata.bits(8)),Utils.FixedPoint(true,32,0,io.memStreams(6).rdata.bits(9)),Utils.FixedPoint(true,32,0,io.memStreams(6).rdata.bits(10)),Utils.FixedPoint(true,32,0,io.memStreams(6).rdata.bits(11)),Utils.FixedPoint(true,32,0,io.memStreams(6).rdata.bits(12)),Utils.FixedPoint(true,32,0,io.memStreams(6).rdata.bits(13)),Utils.FixedPoint(true,32,0,io.memStreams(6).rdata.bits(14)),Utils.FixedPoint(true,32,0,io.memStreams(6).rdata.bits(15))))
  io.memStreams(6).cmd.bits.addr(0) := x4224_data(64, 33) // Bits 33 to 64 are addr
  io.memStreams(6).cmd.bits.size := x4224_data(32,1) // Bits 1 to 32 are size command
  io.memStreams(6).cmd.valid :=  x4224_valid// LSB is enable, instead of pulser?? Reg(UInt(1.W), pulser.io.out)
  io.memStreams(6).cmd.bits.isWr := ~x4224_data(0)
  //  ---- OUTER: Begin Metapipe x4243_UnitPipe Controller ----
  val x4243_UnitPipe_offset = 0 // TODO: Compute real delays
  val x4243_UnitPipe_sm = Module(new Metapipe(2, false))
  x4243_UnitPipe_sm.io.input.enable := x4243_UnitPipe_en;
  x4243_UnitPipe_done := Utils.delay(x4243_UnitPipe_sm.io.output.done, x4243_UnitPipe_offset)
  val x4243_UnitPipe_rst_en = x4243_UnitPipe_sm.io.output.rst_en // Generally used in inner pipes
  x4243_UnitPipe_sm.io.input.numIter := (1.U)
  x4243_UnitPipe_sm.io.input.rst := x4243_UnitPipe_resetter // generally set by parent
  val x4243_UnitPipe_datapath_en = x4243_UnitPipe_en
  // ---- Single Iteration for Metapipe x4243_UnitPipe ----
  // How to emit for non-innerpipe unit counter?
  x4243_UnitPipe_sm.io.input.forever := false.B
  // ---- Begin Metapipe x4243_UnitPipe Children Signals ----
  x4243_UnitPipe_sm.io.input.stageDone(0) := x4235_UnitPipe_done;
  x4235_UnitPipe_en := x4243_UnitPipe_sm.io.output.stageEnable(0)   & ~x4225_$anonfun.io.empty
  x4235_UnitPipe_resetter := x4243_UnitPipe_sm.io.output.rst_en
  x4243_UnitPipe_sm.io.input.stageDone(1) := x4242_unrForeach_done;
  x4242_unrForeach_en := x4243_UnitPipe_sm.io.output.stageEnable(1)   & x4226_ready
  x4242_unrForeach_resetter := x4243_UnitPipe_sm.io.output.rst_en
  // Creating sub kernel x4243_UnitPipe
  // results in ()
}
