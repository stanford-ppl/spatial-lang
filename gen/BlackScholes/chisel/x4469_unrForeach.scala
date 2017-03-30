package accel
import templates._
import types._
import chisel3._
trait x4469_unrForeach extends RootController {
  // Controller Stack: Stack(x4470)
  b2960 := x4215_ctr(0)
  //  ---- OUTER: Begin Parallel x4350 Controller ----
  val x4350_offset = 0 // TODO: Compute real delays
  val x4350_sm = Module(new Parallel(6, false))
  x4350_sm.io.input.enable := x4350_en;
  x4350_done := Utils.delay(x4350_sm.io.output.done, x4350_offset)
  val x4350_rst_en = x4350_sm.io.output.rst_en // Generally used in inner pipes
  val x4350_datapath_en = x4350_sm.io.output.ctr_inc // TODO: Make sure this is a safe assignment
  x4350_sm.io.input.forever := false.B
  // ---- Begin Parallel x4350 Children Signals ----
  x4350_sm.io.input.stageDone(0) := x4244_UnitPipe_done;
  x4244_UnitPipe_en := x4350_sm.io.output.stageEnable(0)    
  x4244_UnitPipe_resetter := x4350_sm.io.output.rst_en
  x4350_sm.io.input.stageDone(1) := x4265_UnitPipe_done;
  x4265_UnitPipe_en := x4350_sm.io.output.stageEnable(1)    
  x4265_UnitPipe_resetter := x4350_sm.io.output.rst_en
  x4350_sm.io.input.stageDone(2) := x4286_UnitPipe_done;
  x4286_UnitPipe_en := x4350_sm.io.output.stageEnable(2)    
  x4286_UnitPipe_resetter := x4350_sm.io.output.rst_en
  x4350_sm.io.input.stageDone(3) := x4307_UnitPipe_done;
  x4307_UnitPipe_en := x4350_sm.io.output.stageEnable(3)    
  x4307_UnitPipe_resetter := x4350_sm.io.output.rst_en
  x4350_sm.io.input.stageDone(4) := x4328_UnitPipe_done;
  x4328_UnitPipe_en := x4350_sm.io.output.stageEnable(4)    
  x4328_UnitPipe_resetter := x4350_sm.io.output.rst_en
  x4350_sm.io.input.stageDone(5) := x4349_UnitPipe_done;
  x4349_UnitPipe_en := x4350_sm.io.output.stageEnable(5)    
  x4349_UnitPipe_resetter := x4350_sm.io.output.rst_en
  // Creating sub kernel x4350
  // x4351 = (0 to 640 by 1 par 1
  val x4352_ctrchain_strides = List(1.U(32.W)) // TODO: Safe to get rid of this and connect directly?
  val x4352_ctrchain_maxes = List(640.U(32.W)) // TODO: Safe to get rid of this and connect directly?
  val x4352_ctrchain = Module(new templates.Counter(List(1))) // Par of 0 creates forever counter
  x4352_ctrchain.io.input.maxes.zip(x4352_ctrchain_maxes).foreach { case (port,max) => port := max }
  x4352_ctrchain.io.input.strides.zip(x4352_ctrchain_strides).foreach { case (port,stride) => port := stride }
  x4352_ctrchain.io.input.enable := x4352_ctrchain_en
  x4352_ctrchain_done := x4352_ctrchain.io.output.done
  x4352_ctrchain.io.input.reset := x4352_ctrchain_resetter
  val x4352_ctrchain_maxed = x4352_ctrchain.io.output.saturated
  val x4351_ctr = (0 until 1).map{ j => x4352_ctrchain.io.output.counts(0 + j) }
  //  ---- INNER: Begin Innerpipe x4444_unrForeach Controller ----
  val x4444_unrForeach_level0_iters = (640.U(32.W) - 0.U(32.W)) / (1.U(32.W) * 1.U(32.W)) + Mux(((640.U(32.W) - 0.U(32.W)) % (1.U(32.W) * 1.U(32.W)) === 0.U), 0.U, 1.U)
  val x4444_unrForeach_offset = 0 // TODO: Compute real delays
  val x4444_unrForeach_sm = Module(new Innerpipe(1 /*TODO: don't need*/, false))
  x4444_unrForeach_sm.io.input.enable := x4444_unrForeach_en;
  x4444_unrForeach_done := Utils.delay(x4444_unrForeach_sm.io.output.done, x4444_unrForeach_offset)
  val x4444_unrForeach_rst_en = x4444_unrForeach_sm.io.output.rst_en // Generally used in inner pipes
  val x4444_unrForeach_datapath_en = x4444_unrForeach_sm.io.output.ctr_inc // TODO: Make sure this is a safe assignment
  x4352_ctrchain_en := x4444_unrForeach_sm.io.output.ctr_inc
  // ---- Counter Connections for Innerpipe x4444_unrForeach (x4352_ctrchain) ----
  x4352_ctrchain_resetter := x4444_unrForeach_rst_en
  x4444_unrForeach_sm.io.input.ctr_done := Utils.delay(x4352_ctrchain_done, 1 + x4444_unrForeach_offset)
  x4444_unrForeach_sm.io.input.forever := false.B
  val b3118 = b3117 < x4352_ctrchain_maxes(0)
  // Creating sub kernel x4444_unrForeach
  //  ---- OUTER: Begin Streampipe x4468_UnitPipe Controller ----
  val x4468_UnitPipe_offset = 0 // TODO: Compute real delays
  val x4468_UnitPipe_sm = Module(new Streampipe(2, false))
  x4468_UnitPipe_sm.io.input.enable := x4468_UnitPipe_en;
  x4468_UnitPipe_done := Utils.delay(x4468_UnitPipe_sm.io.output.done, x4468_UnitPipe_offset)
  val x4468_UnitPipe_rst_en = x4468_UnitPipe_sm.io.output.rst_en // Generally used in inner pipes
  val x4468_UnitPipe_datapath_en = x4468_UnitPipe_en
  x4468_UnitPipe_sm.io.input.forever := false.B
  // ---- Begin Streampipe x4468_UnitPipe Children Signals ----
  x4468_UnitPipe_sm.io.input.stageDone(0) := x4463_UnitPipe_done;
  x4463_UnitPipe_en := x4468_UnitPipe_sm.io.output.stageEnable(0)    
  x4463_UnitPipe_resetter := x4468_UnitPipe_sm.io.output.rst_en
  x4468_UnitPipe_sm.io.input.stageDone(1) := x4467_UnitPipe_done;
  x4467_UnitPipe_en := x4468_UnitPipe_sm.io.output.stageEnable(1)   & ~x4446_$anonfun.io.empty & x4448_ready
  x4467_UnitPipe_resetter := x4468_UnitPipe_sm.io.output.rst_en
  // Creating sub kernel x4468_UnitPipe
  // results in ()
}
