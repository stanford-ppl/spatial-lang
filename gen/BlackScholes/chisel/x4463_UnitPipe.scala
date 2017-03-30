package accel
import templates._
import types._
import chisel3._
trait x4463_UnitPipe extends x4468_UnitPipe {
  // Controller Stack: Stack(x4468, x4469, x4470)
  //  ---- INNER: Begin Streaminner x4454_UnitPipe Controller ----
  val x4454_UnitPipe_offset = 0 // TODO: Compute real delays
  val x4454_UnitPipe_sm = Module(new Streaminner(1 /*TODO: don't need*/, false))
  x4454_UnitPipe_sm.io.input.enable := x4454_UnitPipe_en;
  x4454_UnitPipe_done := Utils.delay(x4454_UnitPipe_sm.io.output.done, x4454_UnitPipe_offset)
  val x4454_UnitPipe_rst_en = x4454_UnitPipe_sm.io.output.rst_en // Generally used in inner pipes
  val x4454_UnitPipe_datapath_en = x4454_UnitPipe_en
  // ---- Single Iteration for Streaminner x4454_UnitPipe ----
  x4454_UnitPipe_sm.io.input.ctr_done := Utils.delay(x4454_UnitPipe_en, 1 + x4454_UnitPipe_offset) // stream kiddo
  val x4454_UnitPipe_ctr_en = x4454_UnitPipe_done // stream kiddo
  x4454_UnitPipe_sm.io.input.forever := false.B
  // Creating sub kernel x4454_UnitPipe
  // x4455 = (0 to 640 by 1 par 1
  val x4456_ctrchain_strides = List(1.U(32.W)) // TODO: Safe to get rid of this and connect directly?
  val x4456_ctrchain_maxes = List(640.U(32.W)) // TODO: Safe to get rid of this and connect directly?
  val x4456_ctrchain = Module(new templates.Counter(List(1))) // Par of 0 creates forever counter
  x4456_ctrchain.io.input.maxes.zip(x4456_ctrchain_maxes).foreach { case (port,max) => port := max }
  x4456_ctrchain.io.input.strides.zip(x4456_ctrchain_strides).foreach { case (port,stride) => port := stride }
  x4456_ctrchain.io.input.enable := x4456_ctrchain_en
  x4456_ctrchain_done := x4456_ctrchain.io.output.done
  x4456_ctrchain.io.input.reset := x4456_ctrchain_resetter
  val x4456_ctrchain_maxed = x4456_ctrchain.io.output.saturated
  val x4455_ctr = (0 until 1).map{ j => x4456_ctrchain.io.output.counts(0 + j) }
  //  ---- INNER: Begin Streaminner x4462_unrForeach Controller ----
  val x4462_unrForeach_level0_iters = (640.U(32.W) - 0.U(32.W)) / (1.U(32.W) * 1.U(32.W)) + Mux(((640.U(32.W) - 0.U(32.W)) % (1.U(32.W) * 1.U(32.W)) === 0.U), 0.U, 1.U)
  val x4462_unrForeach_offset = 0 // TODO: Compute real delays
  val x4462_unrForeach_sm = Module(new Streaminner(1 /*TODO: don't need*/, false))
  x4462_unrForeach_sm.io.input.enable := x4462_unrForeach_en;
  x4462_unrForeach_done := Utils.delay(x4462_unrForeach_sm.io.output.done, x4462_unrForeach_offset)
  val x4462_unrForeach_rst_en = x4462_unrForeach_sm.io.output.rst_en // Generally used in inner pipes
  val x4462_unrForeach_datapath_en = x4462_unrForeach_en
  x4456_ctrchain_en := x4462_unrForeach_datapath_en // Stream kiddo, so only inc when _enq is ready (may be wrong)
  x4462_unrForeach_sm.io.input.hasStreamIns := true.B
  // ---- Counter Connections for Streaminner x4462_unrForeach (x4456_ctrchain) ----
  x4456_ctrchain_resetter := x4462_unrForeach_done // Do not use rst_en for stream kiddo
  x4462_unrForeach_sm.io.input.ctr_done := Utils.delay(x4456_ctrchain_done, 1 + x4462_unrForeach_offset)
  x4462_unrForeach_sm.io.input.forever := false.B
  val b3225 = b3224 < x4456_ctrchain_maxes(0)
  // Creating sub kernel x4462_unrForeach
  // results in ()
}
