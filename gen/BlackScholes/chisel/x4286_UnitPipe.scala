package accel
import templates._
import types._
import chisel3._
trait x4286_UnitPipe extends x4350 {
  // Controller Stack: Stack(x4350, x4469, x4470)
  val x4267_$anonfun_wdata = Wire(Vec(1, UInt(32.W)))
  val x4267_$anonfun_readEn = Wire(Bool())
  val x4267_$anonfun_writeEn = Wire(Bool())
  val x4267_$anonfun_rdata = x4267_$anonfun.io.out
  x4267_$anonfun.io.in := x4267_$anonfun_wdata
  x4267_$anonfun.io.pop := x4267_$anonfun_readEn
  x4267_$anonfun.io.push := x4267_$anonfun_writeEn
  // New stream in x4268
  //  ---- INNER: Begin Streaminner x4274_UnitPipe Controller ----
  val x4274_UnitPipe_offset = 0 // TODO: Compute real delays
  val x4274_UnitPipe_sm = Module(new Streaminner(1 /*TODO: don't need*/, false))
  x4274_UnitPipe_sm.io.input.enable := x4274_UnitPipe_en;
  x4274_UnitPipe_done := Utils.delay(x4274_UnitPipe_sm.io.output.done, x4274_UnitPipe_offset)
  val x4274_UnitPipe_rst_en = x4274_UnitPipe_sm.io.output.rst_en // Generally used in inner pipes
  val x4274_UnitPipe_datapath_en = x4274_UnitPipe_en
  // ---- Single Iteration for Streaminner x4274_UnitPipe ----
  x4274_UnitPipe_sm.io.input.ctr_done := Utils.delay(x4274_UnitPipe_en, 1 + x4274_UnitPipe_offset) // stream kiddo
  val x4274_UnitPipe_ctr_en = x4274_UnitPipe_done // stream kiddo
  x4274_UnitPipe_sm.io.input.forever := false.B
  // Creating sub kernel x4274_UnitPipe
  // Connect streams to ports on mem controller
  // HACK: Assume load is par=16
  val x4268_data = Vec(List(Utils.FixedPoint(true,16,16,io.memStreams(5).rdata.bits(0)),Utils.FixedPoint(true,16,16,io.memStreams(5).rdata.bits(1)),Utils.FixedPoint(true,16,16,io.memStreams(5).rdata.bits(2)),Utils.FixedPoint(true,16,16,io.memStreams(5).rdata.bits(3)),Utils.FixedPoint(true,16,16,io.memStreams(5).rdata.bits(4)),Utils.FixedPoint(true,16,16,io.memStreams(5).rdata.bits(5)),Utils.FixedPoint(true,16,16,io.memStreams(5).rdata.bits(6)),Utils.FixedPoint(true,16,16,io.memStreams(5).rdata.bits(7)),Utils.FixedPoint(true,16,16,io.memStreams(5).rdata.bits(8)),Utils.FixedPoint(true,16,16,io.memStreams(5).rdata.bits(9)),Utils.FixedPoint(true,16,16,io.memStreams(5).rdata.bits(10)),Utils.FixedPoint(true,16,16,io.memStreams(5).rdata.bits(11)),Utils.FixedPoint(true,16,16,io.memStreams(5).rdata.bits(12)),Utils.FixedPoint(true,16,16,io.memStreams(5).rdata.bits(13)),Utils.FixedPoint(true,16,16,io.memStreams(5).rdata.bits(14)),Utils.FixedPoint(true,16,16,io.memStreams(5).rdata.bits(15))))
  io.memStreams(5).cmd.bits.addr(0) := x4266_data(64, 33) // Bits 33 to 64 are addr
  io.memStreams(5).cmd.bits.size := x4266_data(32,1) // Bits 1 to 32 are size command
  io.memStreams(5).cmd.valid :=  x4266_valid// LSB is enable, instead of pulser?? Reg(UInt(1.W), pulser.io.out)
  io.memStreams(5).cmd.bits.isWr := ~x4266_data(0)
  //  ---- OUTER: Begin Metapipe x4285_UnitPipe Controller ----
  val x4285_UnitPipe_offset = 0 // TODO: Compute real delays
  val x4285_UnitPipe_sm = Module(new Metapipe(2, false))
  x4285_UnitPipe_sm.io.input.enable := x4285_UnitPipe_en;
  x4285_UnitPipe_done := Utils.delay(x4285_UnitPipe_sm.io.output.done, x4285_UnitPipe_offset)
  val x4285_UnitPipe_rst_en = x4285_UnitPipe_sm.io.output.rst_en // Generally used in inner pipes
  x4285_UnitPipe_sm.io.input.numIter := (1.U)
  x4285_UnitPipe_sm.io.input.rst := x4285_UnitPipe_resetter // generally set by parent
  val x4285_UnitPipe_datapath_en = x4285_UnitPipe_en
  // ---- Single Iteration for Metapipe x4285_UnitPipe ----
  // How to emit for non-innerpipe unit counter?
  x4285_UnitPipe_sm.io.input.forever := false.B
  // ---- Begin Metapipe x4285_UnitPipe Children Signals ----
  x4285_UnitPipe_sm.io.input.stageDone(0) := x4277_UnitPipe_done;
  x4277_UnitPipe_en := x4285_UnitPipe_sm.io.output.stageEnable(0)   & ~x4267_$anonfun.io.empty
  x4277_UnitPipe_resetter := x4285_UnitPipe_sm.io.output.rst_en
  x4285_UnitPipe_sm.io.input.stageDone(1) := x4284_unrForeach_done;
  x4284_unrForeach_en := x4285_UnitPipe_sm.io.output.stageEnable(1)   & x4268_ready
  x4284_unrForeach_resetter := x4285_UnitPipe_sm.io.output.rst_en
  // Creating sub kernel x4285_UnitPipe
  // results in ()
}
