// See LICENSE.txt for license details.
package templates

import chisel3._

//A n-stage Parallel controller
class Parallel(val n: Int, val isFSM: Boolean = false, val retime: Int = 0) extends Module {
  val io = IO(new Bundle {
    val input = new Bundle {
      val enable = Input(Bool())
      val numIter = Input(UInt(32.W))
      val stageDone = Vec(n, Input(Bool()))
      val stageMask = Vec(n, Input(Bool()))
      val forever = Input(Bool())
      val rst = Input(Bool())
      val hasStreamIns = Input(Bool()) // Not used, here for codegen compatibility

      // FSM signals
      val nextState = Input(UInt(32.W))
    }
    val output = new Bundle {
      val done = Output(Bool())
      val stageEnable = Vec(n, Output(Bool()))
      val rst_en = Output(Bool())
      val ctr_inc = Output(Bool())
      // FSM signals
      val state = Output(UInt(32.W))
    }
  })

  // 0: INIT, 1: Separate reset from enables, 2 stages enabled, 3: DONE
  // Name the universal states
  val initState = 0
  val bufferState = initState + 1
  val runningState = bufferState + 1
  val doneState = runningState + 1

  // Create FF for holding state
  val stateFF = Module(new FF(2))
  stateFF.io.input(0).enable := true.B
  stateFF.io.input(0).init := 0.U
  stateFF.io.input(0).reset := io.input.rst
  val state = stateFF.io.output.data

  // Create vector of registers for holding stage dones
  val doneFF = List.tabulate(n) { i =>
    val ff = Module(new SRFF())
    ff.io.input.set := io.input.stageDone(i) | ~io.input.stageMask(i)
    ff.io.input.asyn_reset := false.B
    ff.io.input.reset := state === doneState.U | io.input.rst
    ff
  }
  val doneMask = doneFF.map { _.io.output.data }

  // // Provide default value for enable and doneClear
  // io.output.stageEnable.foreach { _ := Bool(false) }

  io.output.rst_en := false.B

  io.output.stageEnable.foreach { s => s := false.B}
  // State Machine
  when(io.input.enable) {
    when(state === initState.U) {   // INIT -> RESET
      stateFF.io.input(0).data := bufferState.U
      io.output.rst_en := true.B
    }.elsewhen (state === bufferState.U) { // Not sure if this state is needed for stream
      io.output.rst_en := false.B
      stateFF.io.input(0).data := runningState.U
    }.elsewhen (state === runningState.U) {  // STEADY
      (0 until n).foreach { i => io.output.stageEnable(i) := Mux(io.input.forever, true.B, ~doneMask(i)) }

      val doneTree = doneMask.reduce { _ & _ }
      when(doneTree === 1.U) {
        stateFF.io.input(0).data := Mux(io.input.forever, runningState.U, doneState.U)
      }.otherwise {
        stateFF.io.input(0).data := state
      }
    }.elsewhen (state === doneState.U) {  // DONE
      stateFF.io.input(0).data := initState.U
    }.otherwise {
      stateFF.io.input(0).data := state
    }
  }.otherwise {
    stateFF.io.input(0).data := initState.U
    (0 until n).foreach { i => io.output.stageEnable(i) := false.B }
  }

  // Output logic
  io.output.done := state === doneState.U
  io.output.ctr_inc := false.B // No counters for parallels (BUT MAYBE NEEDED FOR STREAMPIPES)
}





