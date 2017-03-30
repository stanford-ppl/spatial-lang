package accel
import templates._
import fringe._
import types._
import chisel3._
trait RootController extends GlobalWires {
  // Root controller for app: ArgInOut
  val AccelController_en = io.enable & !io.done;
  val AccelController_resetter = false.B // TODO: top level reset
  //  ---- INNER: Begin Innerpipe AccelController Controller ----
  val AccelController_offset = 0 // TODO: Compute real delays
  val AccelController_sm = Module(new Innerpipe(1 /*TODO: don't need*/, false))
  AccelController_sm.io.input.enable := AccelController_en;
  AccelController_done := Utils.delay(AccelController_sm.io.output.done, AccelController_offset)
  val AccelController_rst_en = AccelController_sm.io.output.rst_en // Generally used in inner pipes
  val AccelController_datapath_en = AccelController_sm.io.output.ctr_inc // TODO: Make sure this is a safe assignment
  // ---- Single Iteration for Innerpipe AccelController ----
  AccelController_sm.io.input.ctr_done := Utils.delay(AccelController_sm.io.output.ctr_en, 1 + AccelController_offset)
  val AccelController_ctr_en = AccelController_sm.io.output.ctr_inc
  AccelController_sm.io.input.forever := false.B
  val done_latch = Module(new SRFF())
  done_latch.io.input.set := AccelController_sm.io.output.done
  done_latch.io.input.reset := AccelController_resetter
  io.done := done_latch.io.output.data
  val x154_sumx153_unk = x153_readx150 + 4.U(32.W)
  val x151_argout = RegInit(0.U) // HW-accessible register
  x151_argout := Mux(true.B & AccelController_en, x154_sumx153_unk.number, x151_argout)
  io.argOuts(0).bits := x151_argout // y
  io.argOuts(0).valid := true.B & AccelController_en
  // results in x155_writex151
}
