package accel
import templates._
import types._
import chisel3._
trait x4349_UnitPipe extends x4350 {
  // Controller Stack: Stack(x4350, x4469, x4470)
  val x4330_$anonfun_wdata = Wire(Vec(1, UInt(32.W)))
  val x4330_$anonfun_readEn = Wire(Bool())
  val x4330_$anonfun_writeEn = Wire(Bool())
  val x4330_$anonfun_rdata = x4330_$anonfun.io.out
  x4330_$anonfun.io.in := x4330_$anonfun_wdata
  x4330_$anonfun.io.pop := x4330_$anonfun_readEn
  x4330_$anonfun.io.push := x4330_$anonfun_writeEn
  // New stream in x4331
  //  ---- INNER: Begin Streaminner x4337_UnitPipe Controller ----
  val x4337_UnitPipe_offset = 0 // TODO: Compute real delays
  val x4337_UnitPipe_sm = Module(new Streaminner(1 /*TODO: don't need*/, false))
  x4337_UnitPipe_sm.io.input.enable := x4337_UnitPipe_en;
  x4337_UnitPipe_done := Utils.delay(x4337_UnitPipe_sm.io.output.done, x4337_UnitPipe_offset)
  val x4337_UnitPipe_rst_en = x4337_UnitPipe_sm.io.output.rst_en // Generally used in inner pipes
  val x4337_UnitPipe_datapath_en = x4337_UnitPipe_en
  // ---- Single Iteration for Streaminner x4337_UnitPipe ----
  x4337_UnitPipe_sm.io.input.ctr_done := Utils.delay(x4337_UnitPipe_en, 1 + x4337_UnitPipe_offset) // stream kiddo
  val x4337_UnitPipe_ctr_en = x4337_UnitPipe_done // stream kiddo
  x4337_UnitPipe_sm.io.input.forever := false.B
  // Creating sub kernel x4337_UnitPipe
  // Connect streams to ports on mem controller
  // HACK: Assume load is par=16
  val x4331_data = Vec(List(Utils.FixedPoint(true,16,16,io.memStreams(4).rdata.bits(0)),Utils.FixedPoint(true,16,16,io.memStreams(4).rdata.bits(1)),Utils.FixedPoint(true,16,16,io.memStreams(4).rdata.bits(2)),Utils.FixedPoint(true,16,16,io.memStreams(4).rdata.bits(3)),Utils.FixedPoint(true,16,16,io.memStreams(4).rdata.bits(4)),Utils.FixedPoint(true,16,16,io.memStreams(4).rdata.bits(5)),Utils.FixedPoint(true,16,16,io.memStreams(4).rdata.bits(6)),Utils.FixedPoint(true,16,16,io.memStreams(4).rdata.bits(7)),Utils.FixedPoint(true,16,16,io.memStreams(4).rdata.bits(8)),Utils.FixedPoint(true,16,16,io.memStreams(4).rdata.bits(9)),Utils.FixedPoint(true,16,16,io.memStreams(4).rdata.bits(10)),Utils.FixedPoint(true,16,16,io.memStreams(4).rdata.bits(11)),Utils.FixedPoint(true,16,16,io.memStreams(4).rdata.bits(12)),Utils.FixedPoint(true,16,16,io.memStreams(4).rdata.bits(13)),Utils.FixedPoint(true,16,16,io.memStreams(4).rdata.bits(14)),Utils.FixedPoint(true,16,16,io.memStreams(4).rdata.bits(15))))
  io.memStreams(4).cmd.bits.addr(0) := x4329_data(64, 33) // Bits 33 to 64 are addr
  io.memStreams(4).cmd.bits.size := x4329_data(32,1) // Bits 1 to 32 are size command
  io.memStreams(4).cmd.valid :=  x4329_valid// LSB is enable, instead of pulser?? Reg(UInt(1.W), pulser.io.out)
  io.memStreams(4).cmd.bits.isWr := ~x4329_data(0)
  //  ---- OUTER: Begin Metapipe x4348_UnitPipe Controller ----
  val x4348_UnitPipe_offset = 0 // TODO: Compute real delays
  val x4348_UnitPipe_sm = Module(new Metapipe(2, false))
  x4348_UnitPipe_sm.io.input.enable := x4348_UnitPipe_en;
  x4348_UnitPipe_done := Utils.delay(x4348_UnitPipe_sm.io.output.done, x4348_UnitPipe_offset)
  val x4348_UnitPipe_rst_en = x4348_UnitPipe_sm.io.output.rst_en // Generally used in inner pipes
  x4348_UnitPipe_sm.io.input.numIter := (1.U)
  x4348_UnitPipe_sm.io.input.rst := x4348_UnitPipe_resetter // generally set by parent
  val x4348_UnitPipe_datapath_en = x4348_UnitPipe_en
  // ---- Single Iteration for Metapipe x4348_UnitPipe ----
  // How to emit for non-innerpipe unit counter?
  x4348_UnitPipe_sm.io.input.forever := false.B
  // ---- Begin Metapipe x4348_UnitPipe Children Signals ----
  x4348_UnitPipe_sm.io.input.stageDone(0) := x4340_UnitPipe_done;
  x4340_UnitPipe_en := x4348_UnitPipe_sm.io.output.stageEnable(0)   & ~x4330_$anonfun.io.empty
  x4340_UnitPipe_resetter := x4348_UnitPipe_sm.io.output.rst_en
  x4348_UnitPipe_sm.io.input.stageDone(1) := x4347_unrForeach_done;
  x4347_unrForeach_en := x4348_UnitPipe_sm.io.output.stageEnable(1)   & x4331_ready
  x4347_unrForeach_resetter := x4348_UnitPipe_sm.io.output.rst_en
  // Creating sub kernel x4348_UnitPipe
  // results in ()
}
