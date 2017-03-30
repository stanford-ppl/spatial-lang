package accel
import templates._
import types._
import chisel3._
trait x4285_UnitPipe extends x4286_UnitPipe {
  // Controller Stack: Stack(x4286, x4350, x4469, x4470)
  //  ---- INNER: Begin Streaminner x4277_UnitPipe Controller ----
  val x4277_UnitPipe_offset = 0 // TODO: Compute real delays
  val x4277_UnitPipe_sm = Module(new Streaminner(1 /*TODO: don't need*/, false))
  x4277_UnitPipe_sm.io.input.enable := x4277_UnitPipe_en;
  x4277_UnitPipe_done := Utils.delay(x4277_UnitPipe_sm.io.output.done, x4277_UnitPipe_offset)
  val x4277_UnitPipe_rst_en = x4277_UnitPipe_sm.io.output.rst_en // Generally used in inner pipes
  val x4277_UnitPipe_datapath_en = x4277_UnitPipe_en
  // ---- Single Iteration for Streaminner x4277_UnitPipe ----
  x4277_UnitPipe_sm.io.input.ctr_done := Utils.delay(x4277_UnitPipe_en, 1 + x4277_UnitPipe_offset) // stream kiddo
  val x4277_UnitPipe_ctr_en = x4277_UnitPipe_done // stream kiddo
  x4277_UnitPipe_sm.io.input.forever := false.B
  // Creating sub kernel x4277_UnitPipe
  // x4278 = (0 to 640 by 1 par 1
  val x4279_ctrchain_strides = List(1.U(32.W)) // TODO: Safe to get rid of this and connect directly?
  val x4279_ctrchain_maxes = List(640.U(32.W)) // TODO: Safe to get rid of this and connect directly?
  val x4279_ctrchain = Module(new templates.Counter(List(1))) // Par of 0 creates forever counter
  x4279_ctrchain.io.input.maxes.zip(x4279_ctrchain_maxes).foreach { case (port,max) => port := max }
  x4279_ctrchain.io.input.strides.zip(x4279_ctrchain_strides).foreach { case (port,stride) => port := stride }
  x4279_ctrchain.io.input.enable := x4279_ctrchain_en
  x4279_ctrchain_done := x4279_ctrchain.io.output.done
  x4279_ctrchain.io.input.reset := x4279_ctrchain_resetter
  val x4279_ctrchain_maxed = x4279_ctrchain.io.output.saturated
  val x4278_ctr = (0 until 1).map{ j => x4279_ctrchain.io.output.counts(0 + j) }
  //  ---- INNER: Begin Streaminner x4284_unrForeach Controller ----
  val x4284_unrForeach_level0_iters = (640.U(32.W) - 0.U(32.W)) / (1.U(32.W) * 1.U(32.W)) + Mux(((640.U(32.W) - 0.U(32.W)) % (1.U(32.W) * 1.U(32.W)) === 0.U), 0.U, 1.U)
  val x4284_unrForeach_offset = 0 // TODO: Compute real delays
  val x4284_unrForeach_sm = Module(new Streaminner(1 /*TODO: don't need*/, false))
  x4284_unrForeach_sm.io.input.enable := x4284_unrForeach_en;
  x4284_unrForeach_done := Utils.delay(x4284_unrForeach_sm.io.output.done, x4284_unrForeach_offset)
  val x4284_unrForeach_rst_en = x4284_unrForeach_sm.io.output.rst_en // Generally used in inner pipes
  val x4284_unrForeach_datapath_en = x4284_unrForeach_en
  x4279_ctrchain_en := x4284_unrForeach_datapath_en // Stream kiddo, so only inc when _enq is ready (may be wrong)
  x4284_unrForeach_sm.io.input.hasStreamIns := true.B
  // ---- Counter Connections for Streaminner x4284_unrForeach (x4279_ctrchain) ----
  x4279_ctrchain_resetter := x4284_unrForeach_done // Do not use rst_en for stream kiddo
  x4284_unrForeach_sm.io.input.ctr_done := Utils.delay(x4279_ctrchain_done, 1 + x4284_unrForeach_offset)
  x4284_unrForeach_sm.io.input.forever := false.B
  val b3033 = b3032 < x4279_ctrchain_maxes(0)
  // Creating sub kernel x4284_unrForeach
  // results in ()
}
