package accel
import templates._
import types._
import chisel3._
trait x4462_unrForeach extends x4463_UnitPipe {
  // Controller Stack: Stack(x4463, x4468, x4469, x4470)
  b3224 := x4455_ctr(0)
  val x3226 = b3225 && b2961
  // Assemble multidimR vector
  val x4457_parLd_rVec = Wire(Vec(1, new multidimR(1, 32)))
  x4457_parLd_rVec(0).en := x4462_unrForeach_en
  x4457_parLd_rVec(0).addr(0) := b3224 
  x4223_optpriceBlk_0.connectRPort(Vec(x4457_parLd_rVec.toArray), 1)
  val x4457_parLd = (0 until 1).map{i => Utils.FixedPoint(true,16,16, x4223_optpriceBlk_0.io.output.data(1*1+i))}
  val x4458_elem0 = x4457_parLd.apply(0)
  val x4459_tuple = Utils.Cat(x4458_elem0,true.B)
  val x4460_vecified = Array(x4459_tuple)
  x4447_data := x4460_vecified
  x4447_en := x3226 & x4462_unrForeach_datapath_en & ~x4462_unrForeach_done /*mask off double-enq for sram loads*/
  // results in ()
}
