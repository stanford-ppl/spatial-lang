package accel
import templates._
import types._
import chisel3._
trait x1265_UnitPipe extends x1266_UnitPipe {
  // Controller Stack: Stack(x1266, x1303, x1323, x1327)
  //  ---- INNER: Begin Streaminner x1242_UnitPipe Controller ----
  val x1242_UnitPipe_offset = 0 // TODO: Compute real delays
  val x1242_UnitPipe_sm = Module(new Streaminner(1 /*TODO: don't need*/, false))
  x1242_UnitPipe_sm.io.input.enable := x1242_UnitPipe_en;
  x1242_UnitPipe_done := Utils.delay(x1242_UnitPipe_sm.io.output.done, x1242_UnitPipe_offset)
  val x1242_UnitPipe_rst_en = x1242_UnitPipe_sm.io.output.rst_en // Generally used in inner pipes
  val x1242_UnitPipe_datapath_en = x1242_UnitPipe_en
  // ---- Single Iteration for Streaminner x1242_UnitPipe ----
  x1242_UnitPipe_sm.io.input.ctr_done := Utils.delay(x1242_UnitPipe_en, 1 + x1242_UnitPipe_offset) // stream kiddo
  val x1242_UnitPipe_ctr_en = x1242_UnitPipe_done // stream kiddo
  x1242_UnitPipe_sm.io.input.forever := false.B
  // Creating sub kernel x1242_UnitPipe
  // x1243 = (0 to 640 by 1 par 16
  val x1244_ctrchain_strides = List(1.U(32.W)) // TODO: Safe to get rid of this and connect directly?
  val x1244_ctrchain_maxes = List(640.U(32.W)) // TODO: Safe to get rid of this and connect directly?
  val x1244_ctrchain = Module(new templates.Counter(List(16))) // Par of 0 creates forever counter
  x1244_ctrchain.io.input.maxes.zip(x1244_ctrchain_maxes).foreach { case (port,max) => port := max }
  x1244_ctrchain.io.input.strides.zip(x1244_ctrchain_strides).foreach { case (port,stride) => port := stride }
  x1244_ctrchain.io.input.enable := x1244_ctrchain_en
  x1244_ctrchain_done := x1244_ctrchain.io.output.done
  x1244_ctrchain.io.input.reset := x1244_ctrchain_resetter
  val x1244_ctrchain_maxed = x1244_ctrchain.io.output.saturated
  val x1243_ctr = (0 until 16).map{ j => x1244_ctrchain.io.output.counts(0 + j) }
  //  ---- INNER: Begin Streaminner x1264_unrForeach Controller ----
  val x1264_unrForeach_level0_iters = (640.U(32.W) - 0.U(32.W)) / (1.U(32.W) * 16.U(32.W)) + Mux(((640.U(32.W) - 0.U(32.W)) % (1.U(32.W) * 16.U(32.W)) === 0.U), 0.U, 1.U)
  val x1264_unrForeach_offset = 0 // TODO: Compute real delays
  val x1264_unrForeach_sm = Module(new Streaminner(1 /*TODO: don't need*/, false))
  x1264_unrForeach_sm.io.input.enable := x1264_unrForeach_en;
  x1264_unrForeach_done := Utils.delay(x1264_unrForeach_sm.io.output.done, x1264_unrForeach_offset)
  val x1264_unrForeach_rst_en = x1264_unrForeach_sm.io.output.rst_en // Generally used in inner pipes
  val x1264_unrForeach_datapath_en = x1264_unrForeach_en
  x1244_ctrchain_en := x1264_unrForeach_datapath_en // Stream kiddo, so only inc when _enq is ready (may be wrong)
  x1264_unrForeach_sm.io.input.hasStreamIns := true.B
  // ---- Counter Connections for Streaminner x1264_unrForeach (x1244_ctrchain) ----
  x1244_ctrchain_resetter := x1264_unrForeach_done // Do not use rst_en for stream kiddo
  x1264_unrForeach_sm.io.input.ctr_done := Utils.delay(x1244_ctrchain_done, 1 + x1264_unrForeach_offset)
  x1264_unrForeach_sm.io.input.forever := false.B
  val b749 = b733 < x1244_ctrchain_maxes(0)
  val b750 = b734 < x1244_ctrchain_maxes(0)
  val b751 = b735 < x1244_ctrchain_maxes(0)
  val b752 = b736 < x1244_ctrchain_maxes(0)
  val b753 = b737 < x1244_ctrchain_maxes(0)
  val b754 = b738 < x1244_ctrchain_maxes(0)
  val b755 = b739 < x1244_ctrchain_maxes(0)
  val b756 = b740 < x1244_ctrchain_maxes(0)
  val b757 = b741 < x1244_ctrchain_maxes(0)
  val b758 = b742 < x1244_ctrchain_maxes(0)
  val b759 = b743 < x1244_ctrchain_maxes(0)
  val b760 = b744 < x1244_ctrchain_maxes(0)
  val b761 = b745 < x1244_ctrchain_maxes(0)
  val b762 = b746 < x1244_ctrchain_maxes(0)
  val b763 = b747 < x1244_ctrchain_maxes(0)
  val b764 = b748 < x1244_ctrchain_maxes(0)
  // Creating sub kernel x1264_unrForeach
  // results in ()
}
