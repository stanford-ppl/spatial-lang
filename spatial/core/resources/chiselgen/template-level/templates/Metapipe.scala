// See LICENSE.txt for license details.
package templates

import chisel3._
import Utils._

import scala.collection.mutable.HashMap

class Metapipe(val n: Int, val isFSM: Boolean = false, val stateWidth: Int = 32, val retime: Int = 0) extends Module {
  val io = IO(new Bundle {
    val input = new Bundle {
      val enable = Input(Bool())
      val numIter = Input(UInt(32.W))
      val stageDone = Vec(n, Input(Bool()))
      val stageMask = Vec(n, Input(Bool()))
      val rst = Input(Bool())
      val forever = Input(Bool())
      val hasStreamIns = Input(Bool()) // Not used, here for codegen compatibility
      // FSM signals
      val nextState = Input(SInt(stateWidth.W))

    }
    val output = new Bundle {
      val done = Output(Bool())
      val stageEnable = Vec(n, Output(Bool()))
      val rst_en = Output(Bool())
      val ctr_inc = Output(Bool())
      // FSM signals
      val state = Output(SInt(stateWidth.W))
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
  stateFF.io.input(0).enable := true.B // TODO: Do we need this line?
  stateFF.io.input(0).init := 0.U
  stateFF.io.input(0).reset := io.input.rst
  val state = stateFF.io.output.data

  // Counter for num iterations
  val maxFF = Module(new FF(32))
  maxFF.io.input(0).enable := io.input.enable
  maxFF.io.input(0).data := io.input.numIter
  maxFF.io.input(0).init := 0.U
  maxFF.io.input(0).reset := io.input.rst
  val max = maxFF.io.output.data

  val doneClear = RegInit(0.U)
  val doneFF = List.tabulate(n) { i =>
    val ff = Module(new SRFF())
    ff.io.input.set := io.input.stageDone(i)
    ff.io.input.asyn_reset := doneClear | io.input.rst
    ff.io.input.reset := false.B
    ff
  }
  val doneMask = doneFF.zipWithIndex.map { case (ff, i) => ff.io.output.data }

  val ctr = Module(new SingleCounter(1))
  ctr.io.input.enable := doneClear
  ctr.io.input.saturate := true.B
  ctr.io.input.stop := max.asSInt
  ctr.io.input.stride := 1.S
  ctr.io.input.start := 0.S
  ctr.io.input.gap := 0.S
  ctr.io.input.reset := io.input.rst | (state === doneState.U)
  io.output.rst_en := (state === resetState.U)


  // Counter for handling drainage while in fill state
  val cycsSinceDone = Module(new FF(bitsToAddress(n)))
  cycsSinceDone.io.input(0).init := 0.U
  cycsSinceDone.io.input(0).reset := (state === doneState.U)

  // // Provide default value for enable and doneClear
  // io.output.stageEnable.foreach { _ := UInt(0) }
  // doneClear := UInt(0)

  when(io.input.enable) {
    when(state === initState.U) {   // INIT -> RESET
      stateFF.io.input(0).data := resetState.U
      io.output.stageEnable.foreach { s => s := false.B}
      cycsSinceDone.io.input(0).enable := false.B
    }.elsewhen (state === resetState.U) {  // RESET -> FILL
      stateFF.io.input(0).data := Mux(io.input.numIter === 0.U, Mux(io.input.forever, steadyState.U, doneState.U), fillState.U) // Go directly to done if niters = 0
      io.output.stageEnable.foreach { s => s := false.B}
      cycsSinceDone.io.input(0).enable := false.B
    }.elsewhen (state < steadyState.U) {  // FILL -> STEADY
      for ( i <- fillState until steadyState) {
        val fillStateID = i - fillState
        when((state === i.U)) {
          io.output.stageEnable.zip(doneMask).zipWithIndex.take(fillStateID+1).foreach { 
            case ((en, done), ii) => 
              en := ~done & (ii.U >= cycsSinceDone.io.output.data) & (io.input.numIter != 0.U) & io.input.stageMask(ii)
          }
          io.output.stageEnable.drop(fillStateID+1).foreach { en => en := false.B }
          val doneMaskInts = doneMask.zip(io.input.stageMask).zipWithIndex.map{case ((a,b),iii) => (a | ~b) & iii.U >= cycsSinceDone.io.output.data}.take(fillStateID+1).map {Mux(_, 1.U(bitsToAddress(n).W), 0.U(bitsToAddress(n).W))}
          val doneTree = doneMaskInts.reduce {_ + _} + cycsSinceDone.io.output.data === (fillStateID+1).U
          // val doneTree = doneMask.zipWithIndex.map{case (a,i) => a | ~io.input.stageMask(i)}.take(fillStateID+1).reduce {_ & _}
          doneClear := doneTree

          when (doneTree === 1.U) {
            if (i+1 == steadyState) { // If moving to steady state
              stateFF.io.input(0).data := Mux(cycsSinceDone.io.output.data === 0.U & ctr.io.output.count(0) + 1.S < max.asSInt , 
                          steadyState.U, 
                          Mux(io.input.forever, steadyState.U, cycsSinceDone.io.output.data + 2.U + stateFF.io.output.data) 
                        ) // If already in drain step, bypass steady state
            } else {
              cycsSinceDone.io.input(0).data := cycsSinceDone.io.output.data + 1.U
              cycsSinceDone.io.input(0).enable := ctr.io.output.count(0) + 1.S === max.asSInt
              stateFF.io.input(0).data := (i+1).U
            }
          }.otherwise {
            cycsSinceDone.io.input(0).enable := false.B
            stateFF.io.input(0).data := state
          }
        }
      }
    }.elsewhen (state === steadyState.U) {  // STEADY
      io.output.stageEnable.zipWithIndex.foreach { case (en, i) => en := ~doneMask(i) & io.input.stageMask(i)  }

      val doneTree = doneMask.zipWithIndex.map{case (a,i) => a | ~io.input.stageMask(i)}.reduce{_ & _}
      doneClear := doneTree
      when (doneTree === 1.U) {
        when(ctr.io.output.count(0) === (max.asSInt - 1.S)) {
          stateFF.io.input(0).data := Mux(io.input.forever, steadyState.U, drainState.U)
        }.otherwise {
          stateFF.io.input(0).data := state
        }
      }.otherwise {
        stateFF.io.input(0).data := state
      }
    }.elsewhen (state < doneState.U) {   // DRAIN
      for ( i <- drainState until doneState) {
        val drainStateID = i - drainState
        when (state === i.U) {
          io.output.stageEnable.zipWithIndex.takeRight(n - drainStateID - 1).foreach { case (en, i) => en := ~doneMask(i) & io.input.stageMask(i) }
          io.output.stageEnable.dropRight(n - drainStateID - 1).foreach { en => en := 0.U }
          val doneTree = doneMask.zipWithIndex.map{case (a,i) => a | ~io.input.stageMask(i)}.takeRight(n - drainStateID - 1).reduce {_&_}
          doneClear := doneTree
          when (doneTree === 1.U) {
            stateFF.io.input(0).data := (i+1).U
          }.otherwise {
            stateFF.io.input(0).data := state
          }
        }
      }
    }.elsewhen (state === doneState.U) {  // DONE
      doneClear := false.B
      stateFF.io.input(0).data := initState.U
    }.otherwise {
      stateFF.io.input(0).data := state
    }
  }.otherwise {
    (0 until n).foreach { i => io.output.stageEnable(i) := false.B }
    doneClear := false.B
    stateFF.io.input(0).data := initState.U
  }

  // Output logic
  io.output.ctr_inc := io.input.stageDone(0) & Utils.delay(~io.input.stageDone(0), 1) // on rising edge
  io.output.done := state === doneState.U
  io.output.state := state.asSInt
}

