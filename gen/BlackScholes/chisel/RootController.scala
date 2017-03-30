package accel
import templates._
import fringe._
import types._
import chisel3._
trait RootController extends GlobalWires {
  // Root controller for app: BlackScholes
  val AccelController_en = io.enable & !io.done;
  val AccelController_resetter = false.B // TODO: top level reset
  //  ---- OUTER: Begin Metapipe AccelController Controller ----
  val AccelController_offset = 0 // TODO: Compute real delays
  val AccelController_sm = Module(new Metapipe(1, false))
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
  AccelController_sm.io.input.stageDone(0) := x4469_unrForeach_done;
  x4469_unrForeach_en := AccelController_sm.io.output.stageEnable(0)    
  x4469_unrForeach_resetter := AccelController_sm.io.output.rst_en
  val done_latch = Module(new SRFF())
  done_latch.io.input.set := AccelController_sm.io.output.done
  done_latch.io.input.reset := AccelController_resetter
  io.done := done_latch.io.output.data
  // x4215 = (0 to x4214 by 640 par 1
  val x4216_ctrchain_strides = List(640.U(32.W)) // TODO: Safe to get rid of this and connect directly?
  val x4216_ctrchain_maxes = List(x4214_readx4189) // TODO: Safe to get rid of this and connect directly?
  val x4216_ctrchain = Module(new templates.Counter(List(1))) // Par of 0 creates forever counter
  x4216_ctrchain.io.input.maxes.zip(x4216_ctrchain_maxes).foreach { case (port,max) => port := max }
  x4216_ctrchain.io.input.strides.zip(x4216_ctrchain_strides).foreach { case (port,stride) => port := stride }
  x4216_ctrchain.io.input.enable := x4216_ctrchain_en
  x4216_ctrchain_done := x4216_ctrchain.io.output.done
  x4216_ctrchain.io.input.reset := x4216_ctrchain_resetter
  val x4216_ctrchain_maxed = x4216_ctrchain.io.output.saturated
  val x4215_ctr = (0 until 1).map{ j => x4216_ctrchain.io.output.counts(0 + j) }
  //  ---- OUTER: Begin Metapipe x4469_unrForeach Controller ----
  val x4469_unrForeach_level0_iters = (x4214_readx4189 - 0.U(32.W)) / (640.U(32.W) * 1.U(32.W)) + Mux(((x4214_readx4189 - 0.U(32.W)) % (640.U(32.W) * 1.U(32.W)) === 0.U), 0.U, 1.U)
  val x4469_unrForeach_offset = 0 // TODO: Compute real delays
  val x4469_unrForeach_sm = Module(new Metapipe(3, false))
  x4469_unrForeach_sm.io.input.enable := x4469_unrForeach_en;
  x4469_unrForeach_done := Utils.delay(x4469_unrForeach_sm.io.output.done, x4469_unrForeach_offset)
  val x4469_unrForeach_rst_en = x4469_unrForeach_sm.io.output.rst_en // Generally used in inner pipes
  x4469_unrForeach_sm.io.input.numIter := (x4469_unrForeach_level0_iters)
  x4469_unrForeach_sm.io.input.rst := x4469_unrForeach_resetter // generally set by parent
  val x4469_unrForeach_datapath_en = x4469_unrForeach_sm.io.output.ctr_inc // TODO: Make sure this is a safe assignment
  x4216_ctrchain_en := x4469_unrForeach_sm.io.output.ctr_inc
  // ---- Counter Connections for Metapipe x4469_unrForeach (x4216_ctrchain) ----
  x4216_ctrchain_resetter := x4469_unrForeach_rst_en
  x4469_unrForeach_sm.io.input.forever := false.B
  // ---- Begin Metapipe x4469_unrForeach Children Signals ----
  x4469_unrForeach_sm.io.input.stageDone(0) := x4350_done;
  x4350_en := x4469_unrForeach_sm.io.output.stageEnable(0)    
  x4350_resetter := x4469_unrForeach_sm.io.output.rst_en
  x4469_unrForeach_sm.io.input.stageDone(1) := x4444_unrForeach_done;
  x4444_unrForeach_en := x4469_unrForeach_sm.io.output.stageEnable(1)    
  x4444_unrForeach_resetter := x4469_unrForeach_sm.io.output.rst_en
  x4469_unrForeach_sm.io.input.stageDone(2) := x4468_UnitPipe_done;
  x4468_UnitPipe_en := x4469_unrForeach_sm.io.output.stageEnable(2)    
  x4468_UnitPipe_resetter := x4469_unrForeach_sm.io.output.rst_en
  val b2960_chain = Module(new NBufFF(3, 32))
  b2960_chain.connectStageCtrl(x4350_done, x4350_en, List(0))
  b2960_chain.connectStageCtrl(x4444_unrForeach_done, x4444_unrForeach_en, List(1))
  b2960_chain.connectStageCtrl(x4468_UnitPipe_done, x4468_UnitPipe_en, List(2))
  b2960_chain.chain_pass(b2960, x4469_unrForeach_sm.io.output.ctr_inc)
  val b2961 = b2960 < x4216_ctrchain_maxes(0)
  // Creating sub kernel x4469_unrForeach
  // results in ()
}
