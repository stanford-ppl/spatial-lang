package accel
import templates._
import types._
import chisel3._
trait x4277_UnitPipe extends x4285_UnitPipe {
  // Controller Stack: Stack(x4285, x4286, x4350, x4469, x4470)
  x4267_$anonfun_readEn := x4277_UnitPipe_ctr_en & true.B
  val x4276_deqFrom4267 = Utils.FixedPoint(true,32,0,x4267_$anonfun_rdata(0))
  // results in ()
}
