package accel
import templates._
import types._
import chisel3._
trait x4305_unrForeach extends x4306_UnitPipe {
  // Controller Stack: Stack(x4306, x4307, x4350, x4469, x4470)
  b3056 := x4299_ctr(0)
  val x3058 = b3057 && b2961
  val x4301 = List(x3058).zipWithIndex.map{case (en, i) => x4289_data(i) }
  val x4302_elem0 = x4301.apply(0)
  val x4303_vecified = Array(x4302_elem0)
  // Assemble multidimW vector
  val x4304_parSt_wVec = Wire(Vec(1, new multidimW(1, 32))) 
  x4304_parSt_wVec.zip(x4303_vecified).foreach{ case (port, dat) => port.data := dat.number }
  x4304_parSt_wVec(0).en := x3058
  x4304_parSt_wVec(0).addr(0) := b3056 
  x4220_rateBlk_0.connectWPort(x4304_parSt_wVec, x4305_unrForeach_enq, List(0))
  // results in ()
}
