package accel
import templates._
import types._
import chisel3._
trait x4326_unrForeach extends x4327_UnitPipe {
  // Controller Stack: Stack(x4327, x4328, x4350, x4469, x4470)
  b3080 := x4320_ctr(0)
  val x3082 = b3081 && b2961
  val x4322 = List(x3082).zipWithIndex.map{case (en, i) => x4310_data(i) }
  val x4323_elem0 = x4322.apply(0)
  val x4324_vecified = Array(x4323_elem0)
  // Assemble multidimW vector
  val x4325_parSt_wVec = Wire(Vec(1, new multidimW(1, 32))) 
  x4325_parSt_wVec.zip(x4324_vecified).foreach{ case (port, dat) => port.data := dat.number }
  x4325_parSt_wVec(0).en := x3082
  x4325_parSt_wVec(0).addr(0) := b3080 
  x4221_volBlk_0.connectWPort(x4325_parSt_wVec, x4326_unrForeach_enq, List(0))
  // results in ()
}
