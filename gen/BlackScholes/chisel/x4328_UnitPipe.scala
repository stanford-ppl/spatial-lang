package accel
import templates._
import types._
import chisel3._
trait x4328_UnitPipe extends x4350 {
  // Controller Stack: Stack(x4350, x4469, x4470)
  val x4309_$anonfun_wdata = Wire(Vec(1, UInt(32.W)))
  val x4309_$anonfun_readEn = Wire(Bool())
  val x4309_$anonfun_writeEn = Wire(Bool())
  val x4309_$anonfun_rdata = x4309_$anonfun.io.out
  x4309_$anonfun.io.in := x4309_$anonfun_wdata
  x4309_$anonfun.io.pop := x4309_$anonfun_readEn
  x4309_$anonfun.io.push := x4309_$anonfun_writeEn
  // New stream in x4310
  //  ---- INNER: Begin Streaminner x4316_UnitPipe Controller ----
  val x4316_UnitPipe_offset = 0 // TODO: Compute real delays
  val x4316_UnitPipe_sm = Module(new Streaminner(1 /*TODO: don't need*/, false))
  x4316_UnitPipe_sm.io.input.enable := x4316_UnitPipe_en;
  x4316_UnitPipe_done := Utils.delay(x4316_UnitPipe_sm.io.output.done, x4316_UnitPipe_offset)
  val x4316_UnitPipe_rst_en = x4316_UnitPipe_sm.io.output.rst_en // Generally used in inner pipes
  val x4316_UnitPipe_datapath_en = x4316_UnitPipe_en
  // ---- Single Iteration for Streaminner x4316_UnitPipe ----
  x4316_UnitPipe_sm.io.input.ctr_done := Utils.delay(x4316_UnitPipe_en, 1 + x4316_UnitPipe_offset) // stream kiddo
  val x4316_UnitPipe_ctr_en = x4316_UnitPipe_done // stream kiddo
  x4316_UnitPipe_sm.io.input.forever := false.B
  // Creating sub kernel x4316_UnitPipe
  // Connect streams to ports on mem controller
  // HACK: Assume load is par=16
  val x4310_data = Vec(List(Utils.FixedPoint(true,16,16,io.memStreams(0).rdata.bits(0)),Utils.FixedPoint(true,16,16,io.memStreams(0).rdata.bits(1)),Utils.FixedPoint(true,16,16,io.memStreams(0).rdata.bits(2)),Utils.FixedPoint(true,16,16,io.memStreams(0).rdata.bits(3)),Utils.FixedPoint(true,16,16,io.memStreams(0).rdata.bits(4)),Utils.FixedPoint(true,16,16,io.memStreams(0).rdata.bits(5)),Utils.FixedPoint(true,16,16,io.memStreams(0).rdata.bits(6)),Utils.FixedPoint(true,16,16,io.memStreams(0).rdata.bits(7)),Utils.FixedPoint(true,16,16,io.memStreams(0).rdata.bits(8)),Utils.FixedPoint(true,16,16,io.memStreams(0).rdata.bits(9)),Utils.FixedPoint(true,16,16,io.memStreams(0).rdata.bits(10)),Utils.FixedPoint(true,16,16,io.memStreams(0).rdata.bits(11)),Utils.FixedPoint(true,16,16,io.memStreams(0).rdata.bits(12)),Utils.FixedPoint(true,16,16,io.memStreams(0).rdata.bits(13)),Utils.FixedPoint(true,16,16,io.memStreams(0).rdata.bits(14)),Utils.FixedPoint(true,16,16,io.memStreams(0).rdata.bits(15))))
  io.memStreams(0).cmd.bits.addr(0) := x4308_data(64, 33) // Bits 33 to 64 are addr
  io.memStreams(0).cmd.bits.size := x4308_data(32,1) // Bits 1 to 32 are size command
  io.memStreams(0).cmd.valid :=  x4308_valid// LSB is enable, instead of pulser?? Reg(UInt(1.W), pulser.io.out)
  io.memStreams(0).cmd.bits.isWr := ~x4308_data(0)
  //  ---- OUTER: Begin Metapipe x4327_UnitPipe Controller ----
  val x4327_UnitPipe_offset = 0 // TODO: Compute real delays
  val x4327_UnitPipe_sm = Module(new Metapipe(2, false))
  x4327_UnitPipe_sm.io.input.enable := x4327_UnitPipe_en;
  x4327_UnitPipe_done := Utils.delay(x4327_UnitPipe_sm.io.output.done, x4327_UnitPipe_offset)
  val x4327_UnitPipe_rst_en = x4327_UnitPipe_sm.io.output.rst_en // Generally used in inner pipes
  x4327_UnitPipe_sm.io.input.numIter := (1.U)
  x4327_UnitPipe_sm.io.input.rst := x4327_UnitPipe_resetter // generally set by parent
  val x4327_UnitPipe_datapath_en = x4327_UnitPipe_en
  // ---- Single Iteration for Metapipe x4327_UnitPipe ----
  // How to emit for non-innerpipe unit counter?
  x4327_UnitPipe_sm.io.input.forever := false.B
  // ---- Begin Metapipe x4327_UnitPipe Children Signals ----
  x4327_UnitPipe_sm.io.input.stageDone(0) := x4319_UnitPipe_done;
  x4319_UnitPipe_en := x4327_UnitPipe_sm.io.output.stageEnable(0)   & ~x4309_$anonfun.io.empty
  x4319_UnitPipe_resetter := x4327_UnitPipe_sm.io.output.rst_en
  x4327_UnitPipe_sm.io.input.stageDone(1) := x4326_unrForeach_done;
  x4326_unrForeach_en := x4327_UnitPipe_sm.io.output.stageEnable(1)   & x4310_ready
  x4326_unrForeach_resetter := x4327_UnitPipe_sm.io.output.rst_en
  // Creating sub kernel x4327_UnitPipe
  // results in ()
}
