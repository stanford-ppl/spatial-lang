package accel
import templates._
import types._
import chisel3._
trait x4235_UnitPipe extends x4243_UnitPipe {
  // Controller Stack: Stack(x4243, x4244, x4350, x4469, x4470)
  x4225_$anonfun_readEn := x4235_UnitPipe_ctr_en & true.B
  val x4234_deqFrom4225 = Utils.FixedPoint(true,32,0,x4225_$anonfun_rdata(0))
  // results in ()
}
