package accel
import templates._
import types._
import chisel3._
trait x4263_unrForeach extends x4264_UnitPipe {
  // Controller Stack: Stack(x4264, x4265, x4350, x4469, x4470)
  b3008 := x4257_ctr(0)
  val x3010 = b3009 && b2961
  val x4259 = List(x3010).zipWithIndex.map{case (en, i) => x4247_data(i) }
  val x4260_elem0 = x4259.apply(0)
  val x4261_vecified = Array(x4260_elem0)
  // Assemble multidimW vector
  val x4262_parSt_wVec = Wire(Vec(1, new multidimW(1, 32))) 
  x4262_parSt_wVec.zip(x4261_vecified).foreach{ case (port, dat) => port.data := dat.number }
  x4262_parSt_wVec(0).en := x3010
  x4262_parSt_wVec(0).addr(0) := b3008 
  x4218_priceBlk_0.connectWPort(x4262_parSt_wVec, x4263_unrForeach_enq, List(0))
  // results in ()
}
