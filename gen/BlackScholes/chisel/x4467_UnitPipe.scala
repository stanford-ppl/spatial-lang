package accel
import templates._
import types._
import chisel3._
trait x4467_UnitPipe extends x4468_UnitPipe {
  // Controller Stack: Stack(x4468, x4469, x4470)
  x4446_$anonfun_readEn := x4467_UnitPipe_ctr_en & true.B
  val x4465_deqFrom4446 = Utils.FixedPoint(true,32,0,x4446_$anonfun_rdata(0))
  val x4466 = x4448_data
  // results in ()
}
