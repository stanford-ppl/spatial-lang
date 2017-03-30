package accel
import templates._
import fringe._
import types._
import chisel3._
trait RootController extends GlobalWires {
  // Root controller for app: DotProduct
  val AccelController_en = io.enable & !io.done;
  val AccelController_resetter = false.B // TODO: top level reset
  //  ---- OUTER: Begin Metapipe AccelController Controller ----
  val AccelController_offset = 0 // TODO: Compute real delays
  val AccelController_sm = Module(new Metapipe(2, false))
  AccelController_sm.io.input.enable := AccelController_en;
  AccelController_done := Utils.delay(AccelController_sm.io.output.done, AccelController_offset)
  val AccelController_rst_en = AccelController_sm.io.output.rst_en // Generally used in inner pipes
  AccelController_sm.io.input.numIter := (1.U)
  AccelController_sm.io.input.rst := AccelController_resetter // generally set by parent
  val AccelController_datapath_en = AccelController_sm.io.output.ctr_inc // TODO: Make sure this is a safe assignment
  // ---- Single Iteration for Metapipe AccelController ----
  // How to emit for non-innerpipe unit counter?
  AccelController_sm.io.input.forever := false.B
  // ---- Begin Metapipe AccelController Children Signals ----
  AccelController_sm.io.input.stageDone(0) := x1323_unrRed_done;
  x1323_unrRed_en := AccelController_sm.io.output.stageEnable(0)    
  x1323_unrRed_resetter := AccelController_sm.io.output.rst_en
  AccelController_sm.io.input.stageDone(1) := x1326_UnitPipe_done;
  x1326_UnitPipe_en := AccelController_sm.io.output.stageEnable(1)    
  x1326_UnitPipe_resetter := AccelController_sm.io.output.rst_en
  val done_latch = Module(new SRFF())
  done_latch.io.input.set := AccelController_sm.io.output.done
  done_latch.io.input.reset := AccelController_resetter
  io.done := done_latch.io.output.data
  // x1227 = (0 to x1226 by 640 par 1
  val x1228_ctrchain_strides = List(640.U(32.W)) // TODO: Safe to get rid of this and connect directly?
  val x1228_ctrchain_maxes = List(x1226_readx1215) // TODO: Safe to get rid of this and connect directly?
  val x1228_ctrchain = Module(new templates.Counter(List(1))) // Par of 0 creates forever counter
  x1228_ctrchain.io.input.maxes.zip(x1228_ctrchain_maxes).foreach { case (port,max) => port := max }
  x1228_ctrchain.io.input.strides.zip(x1228_ctrchain_strides).foreach { case (port,stride) => port := stride }
  x1228_ctrchain.io.input.enable := x1228_ctrchain_en
  x1228_ctrchain_done := x1228_ctrchain.io.output.done
  x1228_ctrchain.io.input.reset := x1228_ctrchain_resetter
  val x1228_ctrchain_maxed = x1228_ctrchain.io.output.saturated
  val x1227_ctr = (0 until 1).map{ j => x1228_ctrchain.io.output.counts(0 + j) }
  //  ---- OUTER: Begin Metapipe x1323_unrRed Controller ----
  val x1323_unrRed_level0_iters = (x1226_readx1215 - 0.U(32.W)) / (640.U(32.W) * 1.U(32.W)) + Mux(((x1226_readx1215 - 0.U(32.W)) % (640.U(32.W) * 1.U(32.W)) === 0.U), 0.U, 1.U)
  val x1323_unrRed_offset = 0 // TODO: Compute real delays
  val x1323_unrRed_sm = Module(new Metapipe(3, false))
  x1323_unrRed_sm.io.input.enable := x1323_unrRed_en;
  x1323_unrRed_done := Utils.delay(x1323_unrRed_sm.io.output.done, x1323_unrRed_offset)
  val x1323_unrRed_rst_en = x1323_unrRed_sm.io.output.rst_en // Generally used in inner pipes
  x1323_unrRed_sm.io.input.numIter := (x1323_unrRed_level0_iters)
  x1323_unrRed_sm.io.input.rst := x1323_unrRed_resetter // generally set by parent
  val x1323_unrRed_datapath_en = x1323_unrRed_sm.io.output.ctr_inc // TODO: Make sure this is a safe assignment
  // ---- Counter Connections for Metapipe x1323_unrRed (x1228_ctrchain) ----
  x1228_ctrchain_resetter := x1323_unrRed_rst_en
  x1323_unrRed_sm.io.input.forever := false.B
  // ---- Begin Metapipe x1323_unrRed Children Signals ----
  x1323_unrRed_sm.io.input.stageDone(0) := x1303_done;
  x1303_en := x1323_unrRed_sm.io.output.stageEnable(0)    
  x1303_resetter := x1323_unrRed_sm.io.output.rst_en
  x1323_unrRed_sm.io.input.stageDone(1) := x1316_unrRed_done;
  x1316_unrRed_en := x1323_unrRed_sm.io.output.stageEnable(1)    
  x1316_unrRed_resetter := x1323_unrRed_sm.io.output.rst_en
  x1323_unrRed_sm.io.input.stageDone(2) := x1322_UnitPipe_done;
  x1322_UnitPipe_en := x1323_unrRed_sm.io.output.stageEnable(2)    
  x1322_UnitPipe_resetter := x1323_unrRed_sm.io.output.rst_en
  val b714_chain = Module(new NBufFF(3, 32))
  b714_chain.connectStageCtrl(x1303_done, x1303_en, List(0))
  b714_chain.connectStageCtrl(x1316_unrRed_done, x1316_unrRed_en, List(1))
  b714_chain.connectStageCtrl(x1322_UnitPipe_done, x1322_UnitPipe_en, List(2))
  b714_chain.chain_pass(b714, x1323_unrRed_sm.io.output.ctr_inc)
  val b715 = b714 < x1228_ctrchain_maxes(0)
  val x1323_unrRed_redLoopCtr = Module(new RedxnCtr());
  x1323_unrRed_redLoopCtr.io.input.enable := x1323_unrRed_datapath_en
  x1323_unrRed_redLoopCtr.io.input.max := 1.U //TODO: Really calculate this
  val x1323_unrRed_redLoop_done = x1323_unrRed_redLoopCtr.io.output.done;
  x1228_ctrchain_en := x1323_unrRed_sm.io.output.ctr_inc
  val x1225_reg_wren = x1322_UnitPipe_done // TODO: Skeptical these codegen rules are correct
  val x1225_reg_resetter = Utils.delay(AccelController_done, 2)
  val x1225_reg_initval = 0.U // TODO: Get real reset value.. Why is rV a tuple?
  // Creating sub kernel x1323_unrRed
  //  ---- INNER: Begin Innerpipe x1326_UnitPipe Controller ----
  val x1326_UnitPipe_offset = 0 // TODO: Compute real delays
  val x1326_UnitPipe_sm = Module(new Innerpipe(1 /*TODO: don't need*/, false))
  x1326_UnitPipe_sm.io.input.enable := x1326_UnitPipe_en;
  x1326_UnitPipe_done := Utils.delay(x1326_UnitPipe_sm.io.output.done, x1326_UnitPipe_offset)
  val x1326_UnitPipe_rst_en = x1326_UnitPipe_sm.io.output.rst_en // Generally used in inner pipes
  val x1326_UnitPipe_datapath_en = x1326_UnitPipe_sm.io.output.ctr_inc // TODO: Make sure this is a safe assignment
  // ---- Single Iteration for Innerpipe x1326_UnitPipe ----
  x1326_UnitPipe_sm.io.input.ctr_done := Utils.delay(x1326_UnitPipe_sm.io.output.ctr_en, 1 + x1326_UnitPipe_offset)
  val x1326_UnitPipe_ctr_en = x1326_UnitPipe_sm.io.output.ctr_inc
  x1326_UnitPipe_sm.io.input.forever := false.B
  // Creating sub kernel x1326_UnitPipe
  // results in ()
}
