package accel
import templates._
import types._
import chisel3._
trait x1302_UnitPipe extends x1303 {
  // Controller Stack: Stack(x1303, x1323, x1327)
  val x1268_$anonfun_wdata = Wire(Vec(1, UInt(32.W)))
  val x1268_$anonfun_readEn = Wire(Bool())
  val x1268_$anonfun_writeEn = Wire(Bool())
  val x1268_$anonfun_rdata = x1268_$anonfun.io.out
  x1268_$anonfun.io.in := x1268_$anonfun_wdata
  x1268_$anonfun.io.pop := x1268_$anonfun_readEn
  x1268_$anonfun.io.push := x1268_$anonfun_writeEn
  // New stream in x1269
  //  ---- INNER: Begin Streaminner x1275_UnitPipe Controller ----
  val x1275_UnitPipe_offset = 0 // TODO: Compute real delays
  val x1275_UnitPipe_sm = Module(new Streaminner(1 /*TODO: don't need*/, false))
  x1275_UnitPipe_sm.io.input.enable := x1275_UnitPipe_en;
  x1275_UnitPipe_done := Utils.delay(x1275_UnitPipe_sm.io.output.done, x1275_UnitPipe_offset)
  val x1275_UnitPipe_rst_en = x1275_UnitPipe_sm.io.output.rst_en // Generally used in inner pipes
  val x1275_UnitPipe_datapath_en = x1275_UnitPipe_en
  // ---- Single Iteration for Streaminner x1275_UnitPipe ----
  x1275_UnitPipe_sm.io.input.ctr_done := Utils.delay(x1275_UnitPipe_en, 1 + x1275_UnitPipe_offset) // stream kiddo
  val x1275_UnitPipe_ctr_en = x1275_UnitPipe_done // stream kiddo
  x1275_UnitPipe_sm.io.input.forever := false.B
  // Creating sub kernel x1275_UnitPipe
  // Connect streams to ports on mem controller
  // HACK: Assume load is par=16
  val x1269_data = Vec(List(Utils.FixedPoint(true,32,0,io.memStreams(1).rdata.bits(0)),Utils.FixedPoint(true,32,0,io.memStreams(1).rdata.bits(1)),Utils.FixedPoint(true,32,0,io.memStreams(1).rdata.bits(2)),Utils.FixedPoint(true,32,0,io.memStreams(1).rdata.bits(3)),Utils.FixedPoint(true,32,0,io.memStreams(1).rdata.bits(4)),Utils.FixedPoint(true,32,0,io.memStreams(1).rdata.bits(5)),Utils.FixedPoint(true,32,0,io.memStreams(1).rdata.bits(6)),Utils.FixedPoint(true,32,0,io.memStreams(1).rdata.bits(7)),Utils.FixedPoint(true,32,0,io.memStreams(1).rdata.bits(8)),Utils.FixedPoint(true,32,0,io.memStreams(1).rdata.bits(9)),Utils.FixedPoint(true,32,0,io.memStreams(1).rdata.bits(10)),Utils.FixedPoint(true,32,0,io.memStreams(1).rdata.bits(11)),Utils.FixedPoint(true,32,0,io.memStreams(1).rdata.bits(12)),Utils.FixedPoint(true,32,0,io.memStreams(1).rdata.bits(13)),Utils.FixedPoint(true,32,0,io.memStreams(1).rdata.bits(14)),Utils.FixedPoint(true,32,0,io.memStreams(1).rdata.bits(15))))
  io.memStreams(1).cmd.bits.addr(0) := x1267_data(64, 33) // Bits 33 to 64 are addr
  io.memStreams(1).cmd.bits.size := x1267_data(32,1) // Bits 1 to 32 are size command
  io.memStreams(1).cmd.valid :=  x1267_valid// LSB is enable, instead of pulser?? Reg(UInt(1.W), pulser.io.out)
  io.memStreams(1).cmd.bits.isWr := ~x1267_data(0)
  //  ---- OUTER: Begin Metapipe x1301_UnitPipe Controller ----
  val x1301_UnitPipe_offset = 0 // TODO: Compute real delays
  val x1301_UnitPipe_sm = Module(new Metapipe(2, false))
  x1301_UnitPipe_sm.io.input.enable := x1301_UnitPipe_en;
  x1301_UnitPipe_done := Utils.delay(x1301_UnitPipe_sm.io.output.done, x1301_UnitPipe_offset)
  val x1301_UnitPipe_rst_en = x1301_UnitPipe_sm.io.output.rst_en // Generally used in inner pipes
  x1301_UnitPipe_sm.io.input.numIter := (1.U)
  x1301_UnitPipe_sm.io.input.rst := x1301_UnitPipe_resetter // generally set by parent
  val x1301_UnitPipe_datapath_en = x1301_UnitPipe_en
  // ---- Single Iteration for Metapipe x1301_UnitPipe ----
  // How to emit for non-innerpipe unit counter?
  x1301_UnitPipe_sm.io.input.forever := false.B
  // ---- Begin Metapipe x1301_UnitPipe Children Signals ----
  x1301_UnitPipe_sm.io.input.stageDone(0) := x1278_UnitPipe_done;
  x1278_UnitPipe_en := x1301_UnitPipe_sm.io.output.stageEnable(0)   & ~x1268_$anonfun.io.empty
  x1278_UnitPipe_resetter := x1301_UnitPipe_sm.io.output.rst_en
  x1301_UnitPipe_sm.io.input.stageDone(1) := x1300_unrForeach_done;
  x1300_unrForeach_en := x1301_UnitPipe_sm.io.output.stageEnable(1)   & x1269_ready
  x1300_unrForeach_resetter := x1301_UnitPipe_sm.io.output.rst_en
  // Creating sub kernel x1301_UnitPipe
  // results in ()
}
