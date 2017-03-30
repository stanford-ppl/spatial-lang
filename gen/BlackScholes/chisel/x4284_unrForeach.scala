package accel
import templates._
import types._
import chisel3._
trait x4284_unrForeach extends x4285_UnitPipe {
  // Controller Stack: Stack(x4285, x4286, x4350, x4469, x4470)
  b3032 := x4278_ctr(0)
  val x3034 = b3033 && b2961
  val x4280 = List(x3034).zipWithIndex.map{case (en, i) => x4268_data(i) }
  val x4281_elem0 = x4280.apply(0)
  val x4282_vecified = Array(x4281_elem0)
  // Assemble multidimW vector
  val x4283_parSt_wVec = Wire(Vec(1, new multidimW(1, 32))) 
  x4283_parSt_wVec.zip(x4282_vecified).foreach{ case (port, dat) => port.data := dat.number }
  x4283_parSt_wVec(0).en := x3034
  x4283_parSt_wVec(0).addr(0) := b3032 
  x4219_strikeBlk_0.connectWPort(x4283_parSt_wVec, x4284_unrForeach_enq, List(0))
  // results in ()
}
