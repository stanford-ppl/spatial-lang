package accel
import templates._
import types._
import chisel3._
trait x4319_UnitPipe extends x4327_UnitPipe {
  // Controller Stack: Stack(x4327, x4328, x4350, x4469, x4470)
  x4309_$anonfun_readEn := x4319_UnitPipe_ctr_en & true.B
  val x4318_deqFrom4309 = Utils.FixedPoint(true,32,0,x4309_$anonfun_rdata(0))
  // results in ()
}
