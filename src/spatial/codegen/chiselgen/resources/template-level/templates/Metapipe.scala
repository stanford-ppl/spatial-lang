// See LICENSE.txt for license details.
package templates

import chisel3._
import Utils._

import scala.collection.mutable.HashMap

class Metapipe(val n: Int, val isFSM: Boolean = false) extends Module {
  val io = IO(new Bundle {
    val input = new Bundle {
      val enable = Input(Bool())
      val numIter = Input(UInt(32.W))
      val stageDone = Vec(n, Input(Bool()))
      val rst = Input(Bool())
      val forever = Input(Bool())
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

  def bitsToAddress(k:Int) = {(scala.math.log(k)/scala.math.log(2)).toInt + 1}

  // 0: INIT, 1: RESET, 2..2+n-1: stages, n: DONE
  val initState = 0
  val resetState = 1
  val fillState = resetState + 1
  val steadyState = fillState + n - 1
  val drainState = steadyState + 1
  val doneState = drainState+n-1


  val stateFF = Module(new FF(32))
  stateFF.io.input.enable := true.B // TODO: Do we need this line?
  stateFF.io.input.init := 0.U
  stateFF.io.input.reset := io.input.rst
  val state = stateFF.io.output.data

  // Counter for num iterations
  val maxFF = Module(new FF(32))
  maxFF.io.input.enable := io.input.enable
  maxFF.io.input.data := io.input.numIter
  maxFF.io.input.init := 0.U
  maxFF.io.input.reset := io.input.rst
  val max = maxFF.io.output.data

  val doneClear = RegInit(0.U)
  val doneFF = List.tabulate(n) { i =>
    val ff = Module(new SRFF())
    ff.io.input.set := io.input.stageDone(i)
    ff.io.input.asyn_reset := doneClear
    ff.io.input.reset := false.B
    ff
  }
  val doneMask = doneFF.zipWithIndex.map { case (ff, i) => ff.io.output.data }

  val ctr = Module(new SingleCounter(1))
  ctr.io.input.enable := doneClear
  ctr.io.input.saturate := true.B
  ctr.io.input.max := max
  ctr.io.input.stride := 1.U
  ctr.io.input.start := 0.U
  ctr.io.input.gap := 0.U
  ctr.io.input.reset := io.input.rst | (state === doneState.U)
  io.output.rst_en := (state === resetState.U)


  // Counter for handling drainage while in fill state
  val cycsSinceDone = Module(new FF(bitsToAddress(n)))
  cycsSinceDone.io.input.init := 0.U
  cycsSinceDone.io.input.reset := (state === doneState.U)

  // // Provide default value for enable and doneClear
  // io.output.stageEnable.foreach { _ := UInt(0) }
  // doneClear := UInt(0)

  when(io.input.enable) {
    when(state === initState.U) {   // INIT -> RESET
      stateFF.io.input.data := resetState.U
      io.output.stageEnable.foreach { s => s := false.B}
    }.elsewhen (state === resetState.U) {  // RESET -> FILL
      stateFF.io.input.data := Mux(io.input.numIter === 0.U, Mux(io.input.forever, steadyState.U, doneState.U), fillState.U) // Go directly to done if niters = 0
      io.output.stageEnable.foreach { s => s := false.B}
    }.elsewhen (state < steadyState.U) {  // FILL -> STEADY
      for ( i <- fillState until steadyState) {
        val fillStateID = i - fillState
        when((state === i.U)) {
          io.output.stageEnable.zip(doneMask).zipWithIndex.take(fillStateID+1).foreach { 
            case ((en, done), ii) => 
              en := ~done & (ii.U >= cycsSinceDone.io.output.data) & (io.input.numIter != 0.U)
          }
          io.output.stageEnable.drop(fillStateID+1).foreach { en => en := false.B }
          val doneMaskInts = doneMask.take(fillStateID+1).map {Mux(_, 1.U(bitsToAddress(n).W), 0.U(bitsToAddress(n).W))}
          val doneTree = doneMaskInts.reduce {_ + _} + cycsSinceDone.io.output.data === (fillStateID+1).U
          // val doneTree = doneMask.take(fillStateID+1).reduce {_ & _}
          doneClear := doneTree

          when (doneTree === 1.U) {
            if (i+1 == steadyState) { // If moving to steady state
              stateFF.io.input.data := Mux(cycsSinceDone.io.output.data === 0.U & ctr.io.output.count(0) + 1.U < max , 
                          steadyState.U, 
                          Mux(io.input.forever, steadyState.U, cycsSinceDone.io.output.data + 1.U + stateFF.io.output.data + {if (n == 2) 1.U else 0.U}) 
                        ) // If already in drain step, bypass steady state
            } else {
              cycsSinceDone.io.input.data := cycsSinceDone.io.output.data + 1.U
              cycsSinceDone.io.input.enable := ctr.io.output.count(0) + 1.U === max 
              stateFF.io.input.data := (i+1).U
            }
          }.otherwise {
            cycsSinceDone.io.input.enable := false.B
            stateFF.io.input.data := state
          }
        }
      }
    }.elsewhen (state === steadyState.U) {  // STEADY
      io.output.stageEnable.zip(doneMask).foreach { case (en, done) => en := ~done }

      val doneTree = doneMask.reduce {_&_}
      doneClear := doneTree
      when (doneTree === 1.U) {
        when(ctr.io.output.count(0) === (max - 1.U)) {
          stateFF.io.input.data := Mux(io.input.forever, steadyState.U, drainState.U)
        }.otherwise {
          stateFF.io.input.data := state
        }
      }.otherwise {
        stateFF.io.input.data := state
      }
    }.elsewhen (state < doneState.U) {   // DRAIN
      for ( i <- drainState until doneState) {
        val drainStateID = i - drainState
        when (state === i.U) {
          io.output.stageEnable.zip(doneMask).takeRight(n - drainStateID - 1).foreach { case (en, done) => en := ~done }
          io.output.stageEnable.dropRight(n - drainStateID - 1).foreach { en => en := 0.U }

          val doneTree = doneMask.takeRight(n - drainStateID - 1).reduce {_&_}
          doneClear := doneTree
          when (doneTree === 1.U) {
            stateFF.io.input.data := (i+1).U
          }.otherwise {
            stateFF.io.input.data := state
          }
        }
      }
    }.elsewhen (state === doneState.U) {  // DONE
      doneClear := false.B
      stateFF.io.input.data := initState.U
    }.otherwise {
      stateFF.io.input.data := state
    }
  }.otherwise {
    (0 until n).foreach { i => io.output.stageEnable(i) := false.B }
    doneClear := false.B
    stateFF.io.input.data := initState.U
  }

  // Output logic
  io.output.ctr_inc := io.input.stageDone(0) & Utils.delay(~io.input.stageDone(0), 1) // on rising edge
  io.output.done := state === doneState.U
}




class Streampipe(override val n: Int, override val isFSM: Boolean = false) extends Parallel(n) {
}