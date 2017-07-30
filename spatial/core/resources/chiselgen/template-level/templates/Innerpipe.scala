package templates

import chisel3._

import scala.collection.mutable.HashMap

// Inner pipe
class Innerpipe(val isFSM: Boolean = false, val stateWidth: Int = 32, val retime: Int = 0) extends Module {

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
      val numIter = Input(UInt(32.W))
      val ctr_done = Input(Bool())
      val rst = Input(Bool())
      val forever = Input(Bool())
      val hasStreamIns = Input(Bool()) // Not used, here for codegen compatibility

      // FSM signals
      val nextState = Input(SInt(stateWidth.W))
      val initState = Input(SInt(stateWidth.W))
      val doneCondition = Input(Bool())
    }
    val output = new Bundle {
      val done = Output(Bool())
      val ctr_inc = Output(Bool())
      val rst_en = Output(Bool())
      // FSM signals
      val state = Output(SInt(stateWidth.W))
    }
  })

  if (!isFSM) {
    val state = RegInit(pipeInit.U(32.W))

    // Initialize state and maxFF
    val rstCtr = Module(new SingleCounter(1))
    rstCtr.io.input.enable := state === pipeReset.U
    rstCtr.io.input.reset := (state != pipeReset.U) | io.input.rst
    rstCtr.io.input.saturate := true.B
    rstCtr.io.input.stop := 2.S
    rstCtr.io.input.gap := 0.S
    rstCtr.io.input.start := 0.S
    rstCtr.io.input.stride := 1.S

    // Only start the state machine when the enable signal is set
    when (io.input.enable) {
      // Change states
      when( state === pipeInit.U ) {
        io.output.done := false.B
        io.output.ctr_inc := false.B
        io.output.rst_en := false.B
        state := pipeReset.U
      }.elsewhen( state === pipeReset.U ) {
        io.output.done := false.B
        io.output.rst_en := true.B;
        state := Mux(io.input.ctr_done, pipeDone.U, pipeReset.U) // Shortcut to done state, for tile store
        when (rstCtr.io.output.done) {
          io.output.rst_en := false.B
          state := Mux(io.input.ctr_done, pipeDone.U, pipeRun.U) // Shortcut to done state, for tile store
        }
      }.elsewhen( state === pipeRun.U ) {
        io.output.done := false.B
        io.output.ctr_inc := true.B
        when (io.input.ctr_done) {
          io.output.ctr_inc := false.B
          state := pipeDone.U
        }.otherwise {
          state := pipeRun.U
        }
      }.elsewhen( state === pipeDone.U ) {
        io.output.done := Mux(io.input.forever, false.B, true.B)
        state := pipeSpinWait.U
      }.elsewhen( state >= pipeSpinWait.U ) {
        io.output.done := false.B
        state := Mux(state >= (pipeSpinWait + retime).U, pipeInit.U, state + 1.U);
      } 
    }.otherwise {
      io.output.done := Mux(io.input.ctr_done | state === pipeDone.U, true.B, false.B)
      io.output.ctr_inc := false.B
      io.output.rst_en := false.B
      io.output.state := state.asSInt
      state := Mux(state === pipeDone.U, pipeSpinWait.U, state) // Move along if enable turns on just as we reach done state
      // Line below broke tile stores when there is a stall of some kind.  Why was it there to begin with?
      // state := pipeInit.U 
    }
  } else { // FSM inner
    val stateFSM = Module(new FF(stateWidth))
    val doneReg = Module(new SRFF())

    stateFSM.io.input(0).data := io.input.nextState.asUInt
    stateFSM.io.input(0).init := io.input.initState.asUInt
    stateFSM.io.input(0).enable := io.input.enable
    stateFSM.io.input(0).reset := reset | ~io.input.enable
    io.output.state := stateFSM.io.output.data.asSInt

    doneReg.io.input.set := io.input.doneCondition & io.input.enable
    doneReg.io.input.reset := ~io.input.enable
    doneReg.io.input.asyn_reset := false.B
    io.output.done := doneReg.io.output.data | (io.input.doneCondition & io.input.enable)

  }
}
