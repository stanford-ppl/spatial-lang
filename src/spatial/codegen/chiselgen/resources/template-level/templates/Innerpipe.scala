package templates

import chisel3._

import scala.collection.mutable.HashMap

// Inner pipe
class Innerpipe(val ctrDepth : Int) extends Module {

  // States
  val pipeInit = 0
  val pipeReset = 1
  val pipeRun = 2
  val pipeDone = 3
  val pipeSpinWait = 4

  // Module IO
  val io = IO(new Bundle {
    val input = new Bundle {
      val enable = Input(Bool())
      val ctr_done = Input(Bool())
      val ctr_maxIn = Vec(ctrDepth, Input(UInt(32.W))) // TODO: Deprecate this maxIn/maxOut business if all is well without it
      val forever = Input(Bool())
    }
    val output = new Bundle {
      val done = Output(Bool())
      val ctr_en = Output(Bool())
      val ctr_inc = Output(Bool()) // Same thing as ctr_en
      val rst_en = Output(Bool())
      val ctr_maxOut = Vec(ctrDepth, Output(UInt(32.W)))
    }
  })

  val state = Reg(init = pipeInit.U)
  val maxFF = List.tabulate(ctrDepth) { i => Reg(init = 0.U) }

  // Initialize state and maxFF
  val rstCtr = Module(new SingleCounter(1))
  rstCtr.io.input.enable := state === pipeReset.U
  rstCtr.io.input.reset := (state != pipeReset.U)
  rstCtr.io.input.saturate := true.B
  rstCtr.io.input.max := 10.U
  rstCtr.io.input.stride := 1.U

  // Only start the state machine when the enable signal is set
  when (io.input.enable) {
    // Change states
    when( state === pipeInit.U ) {
      io.output.done := false.B
      io.output.ctr_en := false.B
      io.output.ctr_inc := false.B
      io.output.rst_en := false.B
      (0 until ctrDepth) foreach { i => maxFF(i) := io.input.ctr_maxIn(i) }
      state := pipeReset.U
    }.elsewhen( state === pipeReset.U ) {
      io.output.rst_en := true.B;
      (0 until ctrDepth) foreach { i => io.output.ctr_maxOut(i) := maxFF(i) }
      when (rstCtr.io.output.done) {
        io.output.rst_en := false.B
        state := pipeRun.U
      }
    }.elsewhen( state === pipeRun.U ) {
      io.output.ctr_en := true.B;
      io.output.ctr_inc := true.B
      when (io.input.ctr_done) {
        io.output.ctr_inc := false.B
        (0 until ctrDepth) foreach { i => maxFF(0) := 0.U } // TODO: Why do we reset these instead of leaving them?
        state := pipeDone.U
      }.otherwise {
        state := pipeRun.U
      }
    }.elsewhen( state === pipeDone.U ) {
      io.output.done := Mux(io.input.forever, false.B, true.B)
      state := pipeReset.U
    }.elsewhen( state === pipeSpinWait.U ) {
      state := pipeSpinWait.U;
    } 
  }.otherwise {
    io.output.done := false.B
    io.output.ctr_en := false.B
    io.output.ctr_inc := false.B
    io.output.rst_en := false.B
    state := pipeInit.U
  }

}

