package accel
import templates._
import types._
import chisel3._
trait x4347_unrForeach extends x4348_UnitPipe {
  // Controller Stack: Stack(x4348, x4349, x4350, x4469, x4470)
  b3104 := x4341_ctr(0)
  val x3106 = b3105 && b2961
  val x4343 = List(x3106).zipWithIndex.map{case (en, i) => x4331_data(i) }
  val x4344_elem0 = x4343.apply(0)
  val x4345_vecified = Array(x4344_elem0)
  // Assemble multidimW vector
  val x4346_parSt_wVec = Wire(Vec(1, new multidimW(1, 32))) 
  x4346_parSt_wVec.zip(x4345_vecified).foreach{ case (port, dat) => port.data := dat.number }
  x4346_parSt_wVec(0).en := x3106
  x4346_parSt_wVec(0).addr(0) := b3104 
  x4222_timeBlk_0.connectWPort(x4346_parSt_wVec, x4347_unrForeach_enq, List(0))
  // results in ()
}
