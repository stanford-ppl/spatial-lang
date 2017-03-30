package accel
import templates._
import types._
import chisel3._
trait x4468_UnitPipe extends x4469_unrForeach {
  // Controller Stack: Stack(x4469, x4470)
  val x4446_$anonfun_wdata = Wire(Vec(1, UInt(32.W)))
  val x4446_$anonfun_readEn = Wire(Bool())
  val x4446_$anonfun_writeEn = Wire(Bool())
  val x4446_$anonfun_rdata = x4446_$anonfun.io.out
  x4446_$anonfun.io.in := x4446_$anonfun_wdata
  x4446_$anonfun.io.pop := x4446_$anonfun_readEn
  x4446_$anonfun.io.push := x4446_$anonfun_writeEn
  // New stream out x4447
  //  ---- OUTER: Begin Metapipe x4463_UnitPipe Controller ----
  val x4463_UnitPipe_offset = 0 // TODO: Compute real delays
  val x4463_UnitPipe_sm = Module(new Metapipe(2, false))
  x4463_UnitPipe_sm.io.input.enable := x4463_UnitPipe_en;
  x4463_UnitPipe_done := Utils.delay(x4463_UnitPipe_sm.io.output.done, x4463_UnitPipe_offset)
  val x4463_UnitPipe_rst_en = x4463_UnitPipe_sm.io.output.rst_en // Generally used in inner pipes
  x4463_UnitPipe_sm.io.input.numIter := (1.U)
  x4463_UnitPipe_sm.io.input.rst := x4463_UnitPipe_resetter // generally set by parent
  val x4463_UnitPipe_datapath_en = x4463_UnitPipe_en
  // ---- Single Iteration for Metapipe x4463_UnitPipe ----
  // How to emit for non-innerpipe unit counter?
  x4463_UnitPipe_sm.io.input.forever := false.B
  // ---- Begin Metapipe x4463_UnitPipe Children Signals ----
  x4463_UnitPipe_sm.io.input.stageDone(0) := x4454_UnitPipe_done;
  x4454_UnitPipe_en := x4463_UnitPipe_sm.io.output.stageEnable(0) & x4445_ready /*not sure if this sig exists*/ & ~x4446_$anonfun.io.full  
  x4454_UnitPipe_resetter := x4463_UnitPipe_sm.io.output.rst_en
  x4463_UnitPipe_sm.io.input.stageDone(1) := x4462_unrForeach_done;
  x4462_unrForeach_en := x4463_UnitPipe_sm.io.output.stageEnable(1) & x4447_ready /*not sure if this sig exists*/  
  x4462_unrForeach_resetter := x4463_UnitPipe_sm.io.output.rst_en
  // Creating sub kernel x4463_UnitPipe
  // Connect streams to ports on mem controller
  // HACK: Assume store is par=16
  io.memStreams(1).wdata.bits.zip(x4447_data).foreach{case (wport, wdata) => wport := wdata(31,1) /*LSB is status bit*/}
  io.memStreams(1).wdata.valid := x4447_en
  io.memStreams(1).cmd.bits.addr(0) := x4445_data(64, 33) // Bits 33 to 64 (AND BEYOND???) are addr
  io.memStreams(1).cmd.bits.size := x4445_data(32,1) // Bits 1 to 32 are size command
  io.memStreams(1).cmd.valid :=  x4445_valid// LSB is enable, instead of pulser?? Reg(UInt(1.W), pulser.io.out)
  io.memStreams(1).cmd.bits.isWr := ~x4445_data(0)
  //  ---- INNER: Begin Streaminner x4467_UnitPipe Controller ----
  val x4467_UnitPipe_offset = 0 // TODO: Compute real delays
  val x4467_UnitPipe_sm = Module(new Streaminner(1 /*TODO: don't need*/, false))
  x4467_UnitPipe_sm.io.input.enable := x4467_UnitPipe_en;
  x4467_UnitPipe_done := Utils.delay(x4467_UnitPipe_sm.io.output.done, x4467_UnitPipe_offset)
  val x4467_UnitPipe_rst_en = x4467_UnitPipe_sm.io.output.rst_en // Generally used in inner pipes
  val x4467_UnitPipe_datapath_en = x4467_UnitPipe_en
  // ---- Single Iteration for Streaminner x4467_UnitPipe ----
  x4467_UnitPipe_sm.io.input.ctr_done := Utils.delay(x4467_UnitPipe_en, 1 + x4467_UnitPipe_offset) // stream kiddo
  val x4467_UnitPipe_ctr_en = x4467_UnitPipe_done // stream kiddo
  x4467_UnitPipe_sm.io.input.hasStreamIns := true.B
  x4467_UnitPipe_sm.io.input.forever := false.B
  // Creating sub kernel x4467_UnitPipe
  // results in ()
}
