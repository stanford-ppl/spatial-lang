package accel
import templates._
import types._
import chisel3._
trait x4243_UnitPipe extends x4244_UnitPipe {
  // Controller Stack: Stack(x4244, x4350, x4469, x4470)
  //  ---- INNER: Begin Streaminner x4235_UnitPipe Controller ----
  val x4235_UnitPipe_offset = 0 // TODO: Compute real delays
  val x4235_UnitPipe_sm = Module(new Streaminner(1 /*TODO: don't need*/, false))
  x4235_UnitPipe_sm.io.input.enable := x4235_UnitPipe_en;
  x4235_UnitPipe_done := Utils.delay(x4235_UnitPipe_sm.io.output.done, x4235_UnitPipe_offset)
  val x4235_UnitPipe_rst_en = x4235_UnitPipe_sm.io.output.rst_en // Generally used in inner pipes
  val x4235_UnitPipe_datapath_en = x4235_UnitPipe_en
  // ---- Single Iteration for Streaminner x4235_UnitPipe ----
  x4235_UnitPipe_sm.io.input.ctr_done := Utils.delay(x4235_UnitPipe_en, 1 + x4235_UnitPipe_offset) // stream kiddo
  val x4235_UnitPipe_ctr_en = x4235_UnitPipe_done // stream kiddo
  x4235_UnitPipe_sm.io.input.forever := false.B
  // Creating sub kernel x4235_UnitPipe
  // x4236 = (0 to 640 by 1 par 1
  val x4237_ctrchain_strides = List(1.U(32.W)) // TODO: Safe to get rid of this and connect directly?
  val x4237_ctrchain_maxes = List(640.U(32.W)) // TODO: Safe to get rid of this and connect directly?
  val x4237_ctrchain = Module(new templates.Counter(List(1))) // Par of 0 creates forever counter
  x4237_ctrchain.io.input.maxes.zip(x4237_ctrchain_maxes).foreach { case (port,max) => port := max }
  x4237_ctrchain.io.input.strides.zip(x4237_ctrchain_strides).foreach { case (port,stride) => port := stride }
  x4237_ctrchain.io.input.enable := x4237_ctrchain_en
  x4237_ctrchain_done := x4237_ctrchain.io.output.done
  x4237_ctrchain.io.input.reset := x4237_ctrchain_resetter
  val x4237_ctrchain_maxed = x4237_ctrchain.io.output.saturated
  val x4236_ctr = (0 until 1).map{ j => x4237_ctrchain.io.output.counts(0 + j) }
  //  ---- INNER: Begin Streaminner x4242_unrForeach Controller ----
  val x4242_unrForeach_level0_iters = (640.U(32.W) - 0.U(32.W)) / (1.U(32.W) * 1.U(32.W)) + Mux(((640.U(32.W) - 0.U(32.W)) % (1.U(32.W) * 1.U(32.W)) === 0.U), 0.U, 1.U)
  val x4242_unrForeach_offset = 0 // TODO: Compute real delays
  val x4242_unrForeach_sm = Module(new Streaminner(1 /*TODO: don't need*/, false))
  x4242_unrForeach_sm.io.input.enable := x4242_unrForeach_en;
  x4242_unrForeach_done := Utils.delay(x4242_unrForeach_sm.io.output.done, x4242_unrForeach_offset)
  val x4242_unrForeach_rst_en = x4242_unrForeach_sm.io.output.rst_en // Generally used in inner pipes
  val x4242_unrForeach_datapath_en = x4242_unrForeach_en
  x4237_ctrchain_en := x4242_unrForeach_datapath_en // Stream kiddo, so only inc when _enq is ready (may be wrong)
  x4242_unrForeach_sm.io.input.hasStreamIns := true.B
  // ---- Counter Connections for Streaminner x4242_unrForeach (x4237_ctrchain) ----
  x4237_ctrchain_resetter := x4242_unrForeach_done // Do not use rst_en for stream kiddo
  x4242_unrForeach_sm.io.input.ctr_done := Utils.delay(x4237_ctrchain_done, 1 + x4242_unrForeach_offset)
  x4242_unrForeach_sm.io.input.forever := false.B
  val b2985 = b2984 < x4237_ctrchain_maxes(0)
  // Creating sub kernel x4242_unrForeach
  // results in ()
}
