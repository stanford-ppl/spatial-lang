package accel
import templates._
import types._
import chisel3._
trait x4242_unrForeach extends x4243_UnitPipe {
  // Controller Stack: Stack(x4243, x4244, x4350, x4469, x4470)
  b2984 := x4236_ctr(0)
  val x2986 = b2985 && b2961
  val x4238 = List(x2986).zipWithIndex.map{case (en, i) => x4226_data(i) }
  val x4239_elem0 = x4238.apply(0)
  val x4240_vecified = Array(x4239_elem0)
  // Assemble multidimW vector
  val x4241_parSt_wVec = Wire(Vec(1, new multidimW(1, 32))) 
  x4241_parSt_wVec.zip(x4240_vecified).foreach{ case (port, dat) => port.data := dat.number }
  x4241_parSt_wVec(0).en := x2986
  x4241_parSt_wVec(0).addr(0) := b2984 
  x4217_typeBlk_0.connectWPort(x4241_parSt_wVec, x4242_unrForeach_enq, List(0))
  // results in ()
}
