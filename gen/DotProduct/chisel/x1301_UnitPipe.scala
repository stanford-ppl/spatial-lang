package accel
import templates._
import types._
import chisel3._
trait x1301_UnitPipe extends x1302_UnitPipe {
  // Controller Stack: Stack(x1302, x1303, x1323, x1327)
  //  ---- INNER: Begin Streaminner x1278_UnitPipe Controller ----
  val x1278_UnitPipe_offset = 0 // TODO: Compute real delays
  val x1278_UnitPipe_sm = Module(new Streaminner(1 /*TODO: don't need*/, false))
  x1278_UnitPipe_sm.io.input.enable := x1278_UnitPipe_en;
  x1278_UnitPipe_done := Utils.delay(x1278_UnitPipe_sm.io.output.done, x1278_UnitPipe_offset)
  val x1278_UnitPipe_rst_en = x1278_UnitPipe_sm.io.output.rst_en // Generally used in inner pipes
  val x1278_UnitPipe_datapath_en = x1278_UnitPipe_en
  // ---- Single Iteration for Streaminner x1278_UnitPipe ----
  x1278_UnitPipe_sm.io.input.ctr_done := Utils.delay(x1278_UnitPipe_en, 1 + x1278_UnitPipe_offset) // stream kiddo
  val x1278_UnitPipe_ctr_en = x1278_UnitPipe_done // stream kiddo
  x1278_UnitPipe_sm.io.input.forever := false.B
  // Creating sub kernel x1278_UnitPipe
  // x1279 = (0 to 640 by 1 par 16
  val x1280_ctrchain_strides = List(1.U(32.W)) // TODO: Safe to get rid of this and connect directly?
  val x1280_ctrchain_maxes = List(640.U(32.W)) // TODO: Safe to get rid of this and connect directly?
  val x1280_ctrchain = Module(new templates.Counter(List(16))) // Par of 0 creates forever counter
  x1280_ctrchain.io.input.maxes.zip(x1280_ctrchain_maxes).foreach { case (port,max) => port := max }
  x1280_ctrchain.io.input.strides.zip(x1280_ctrchain_strides).foreach { case (port,stride) => port := stride }
  x1280_ctrchain.io.input.enable := x1280_ctrchain_en
  x1280_ctrchain_done := x1280_ctrchain.io.output.done
  x1280_ctrchain.io.input.reset := x1280_ctrchain_resetter
  val x1280_ctrchain_maxed = x1280_ctrchain.io.output.saturated
  val x1279_ctr = (0 until 16).map{ j => x1280_ctrchain.io.output.counts(0 + j) }
  //  ---- INNER: Begin Streaminner x1300_unrForeach Controller ----
  val x1300_unrForeach_level0_iters = (640.U(32.W) - 0.U(32.W)) / (1.U(32.W) * 16.U(32.W)) + Mux(((640.U(32.W) - 0.U(32.W)) % (1.U(32.W) * 16.U(32.W)) === 0.U), 0.U, 1.U)
  val x1300_unrForeach_offset = 0 // TODO: Compute real delays
  val x1300_unrForeach_sm = Module(new Streaminner(1 /*TODO: don't need*/, false))
  x1300_unrForeach_sm.io.input.enable := x1300_unrForeach_en;
  x1300_unrForeach_done := Utils.delay(x1300_unrForeach_sm.io.output.done, x1300_unrForeach_offset)
  val x1300_unrForeach_rst_en = x1300_unrForeach_sm.io.output.rst_en // Generally used in inner pipes
  val x1300_unrForeach_datapath_en = x1300_unrForeach_en
  x1280_ctrchain_en := x1300_unrForeach_datapath_en // Stream kiddo, so only inc when _enq is ready (may be wrong)
  x1300_unrForeach_sm.io.input.hasStreamIns := true.B
  // ---- Counter Connections for Streaminner x1300_unrForeach (x1280_ctrchain) ----
  x1280_ctrchain_resetter := x1300_unrForeach_done // Do not use rst_en for stream kiddo
  x1300_unrForeach_sm.io.input.ctr_done := Utils.delay(x1280_ctrchain_done, 1 + x1300_unrForeach_offset)
  x1300_unrForeach_sm.io.input.forever := false.B
  val b833 = b817 < x1280_ctrchain_maxes(0)
  val b834 = b818 < x1280_ctrchain_maxes(0)
  val b835 = b819 < x1280_ctrchain_maxes(0)
  val b836 = b820 < x1280_ctrchain_maxes(0)
  val b837 = b821 < x1280_ctrchain_maxes(0)
  val b838 = b822 < x1280_ctrchain_maxes(0)
  val b839 = b823 < x1280_ctrchain_maxes(0)
  val b840 = b824 < x1280_ctrchain_maxes(0)
  val b841 = b825 < x1280_ctrchain_maxes(0)
  val b842 = b826 < x1280_ctrchain_maxes(0)
  val b843 = b827 < x1280_ctrchain_maxes(0)
  val b844 = b828 < x1280_ctrchain_maxes(0)
  val b845 = b829 < x1280_ctrchain_maxes(0)
  val b846 = b830 < x1280_ctrchain_maxes(0)
  val b847 = b831 < x1280_ctrchain_maxes(0)
  val b848 = b832 < x1280_ctrchain_maxes(0)
  // Creating sub kernel x1300_unrForeach
  // results in ()
}
