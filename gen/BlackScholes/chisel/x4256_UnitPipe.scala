package accel
import templates._
import types._
import chisel3._
trait x4256_UnitPipe extends x4264_UnitPipe {
  // Controller Stack: Stack(x4264, x4265, x4350, x4469, x4470)
  x4246_$anonfun_readEn := x4256_UnitPipe_ctr_en & true.B
  val x4255_deqFrom4246 = Utils.FixedPoint(true,32,0,x4246_$anonfun_rdata(0))
  // results in ()
}
