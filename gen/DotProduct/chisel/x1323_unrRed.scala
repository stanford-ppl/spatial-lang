package accel
import templates._
import types._
import chisel3._
trait x1323_unrRed extends RootController {
  // Controller Stack: Stack(x1327)
  b714 := x1227_ctr(0)
  //  ---- OUTER: Begin Parallel x1303 Controller ----
  val x1303_offset = 0 // TODO: Compute real delays
  val x1303_sm = Module(new Parallel(2, false))
  x1303_sm.io.input.enable := x1303_en;
  x1303_done := Utils.delay(x1303_sm.io.output.done, x1303_offset)
  val x1303_rst_en = x1303_sm.io.output.rst_en // Generally used in inner pipes
  val x1303_datapath_en = x1303_sm.io.output.ctr_inc // TODO: Make sure this is a safe assignment
  x1303_sm.io.input.forever := false.B
  // ---- Begin Parallel x1303 Children Signals ----
  x1303_sm.io.input.stageDone(0) := x1266_UnitPipe_done;
  x1266_UnitPipe_en := x1303_sm.io.output.stageEnable(0)    
  x1266_UnitPipe_resetter := x1303_sm.io.output.rst_en
  x1303_sm.io.input.stageDone(1) := x1302_UnitPipe_done;
  x1302_UnitPipe_en := x1303_sm.io.output.stageEnable(1)    
  x1302_UnitPipe_resetter := x1303_sm.io.output.rst_en
  // Creating sub kernel x1303
  // x1305 = (0 to 640 by 1 par 1
  val x1306_ctrchain_strides = List(1.U(32.W)) // TODO: Safe to get rid of this and connect directly?
  val x1306_ctrchain_maxes = List(640.U(32.W)) // TODO: Safe to get rid of this and connect directly?
  val x1306_ctrchain = Module(new templates.Counter(List(1))) // Par of 0 creates forever counter
  x1306_ctrchain.io.input.maxes.zip(x1306_ctrchain_maxes).foreach { case (port,max) => port := max }
  x1306_ctrchain.io.input.strides.zip(x1306_ctrchain_strides).foreach { case (port,stride) => port := stride }
  x1306_ctrchain.io.input.enable := x1306_ctrchain_en
  x1306_ctrchain_done := x1306_ctrchain.io.output.done
  x1306_ctrchain.io.input.reset := x1306_ctrchain_resetter
  val x1306_ctrchain_maxed = x1306_ctrchain.io.output.saturated
  val x1305_ctr = (0 until 1).map{ j => x1306_ctrchain.io.output.counts(0 + j) }
  //  ---- INNER: Begin Innerpipe x1316_unrRed Controller ----
  val x1316_unrRed_level0_iters = (640.U(32.W) - 0.U(32.W)) / (1.U(32.W) * 1.U(32.W)) + Mux(((640.U(32.W) - 0.U(32.W)) % (1.U(32.W) * 1.U(32.W)) === 0.U), 0.U, 1.U)
  val x1316_unrRed_offset = 0 // TODO: Compute real delays
  val x1316_unrRed_sm = Module(new Innerpipe(1 /*TODO: don't need*/, false))
  x1316_unrRed_sm.io.input.enable := x1316_unrRed_en;
  x1316_unrRed_done := Utils.delay(x1316_unrRed_sm.io.output.done, x1316_unrRed_offset)
  val x1316_unrRed_rst_en = x1316_unrRed_sm.io.output.rst_en // Generally used in inner pipes
  val x1316_unrRed_datapath_en = x1316_unrRed_sm.io.output.ctr_inc // TODO: Make sure this is a safe assignment
  // ---- Counter Connections for Innerpipe x1316_unrRed (x1306_ctrchain) ----
  x1306_ctrchain_resetter := x1316_unrRed_rst_en
  x1316_unrRed_sm.io.input.ctr_done := Utils.delay(x1306_ctrchain_done, 1 + x1316_unrRed_offset)
  x1316_unrRed_sm.io.input.forever := false.B
  val b892 = b891 < x1306_ctrchain_maxes(0)
  val x1316_unrRed_redLoopCtr = Module(new RedxnCtr());
  x1316_unrRed_redLoopCtr.io.input.enable := x1316_unrRed_datapath_en
  x1316_unrRed_redLoopCtr.io.input.max := 1.U //TODO: Really calculate this
  val x1316_unrRed_redLoop_done = x1316_unrRed_redLoopCtr.io.output.done;
  x1306_ctrchain_en := x1316_unrRed_sm.io.output.ctr_inc
  val x1304_$anonfun_wren = x1306_ctrchain_en & ~ x1316_unrRed_done // TODO: Skeptical these codegen rules are correct
  val x1304_$anonfun_resetter = x1316_unrRed_rst_en
  val x1304_$anonfun_initval = 0.U // TODO: Get real reset value.. Why is rV a tuple?
  // Creating sub kernel x1316_unrRed
  //  ---- INNER: Begin Innerpipe x1322_UnitPipe Controller ----
  val x1322_UnitPipe_offset = 0 // TODO: Compute real delays
  val x1322_UnitPipe_sm = Module(new Innerpipe(1 /*TODO: don't need*/, false))
  x1322_UnitPipe_sm.io.input.enable := x1322_UnitPipe_en;
  x1322_UnitPipe_done := Utils.delay(x1322_UnitPipe_sm.io.output.done, x1322_UnitPipe_offset)
  val x1322_UnitPipe_rst_en = x1322_UnitPipe_sm.io.output.rst_en // Generally used in inner pipes
  val x1322_UnitPipe_datapath_en = x1322_UnitPipe_sm.io.output.ctr_inc // TODO: Make sure this is a safe assignment
  // ---- Single Iteration for Innerpipe x1322_UnitPipe ----
  x1322_UnitPipe_sm.io.input.ctr_done := Utils.delay(x1322_UnitPipe_sm.io.output.ctr_en, 1 + x1322_UnitPipe_offset)
  val x1322_UnitPipe_ctr_en = x1322_UnitPipe_sm.io.output.ctr_inc
  x1322_UnitPipe_sm.io.input.forever := false.B
  // Creating sub kernel x1322_UnitPipe
  // results in ()
}
