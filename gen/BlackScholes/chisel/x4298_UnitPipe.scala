package accel
import templates._
import types._
import chisel3._
trait x4298_UnitPipe extends x4306_UnitPipe {
  // Controller Stack: Stack(x4306, x4307, x4350, x4469, x4470)
  x4288_$anonfun_readEn := x4298_UnitPipe_ctr_en & true.B
  val x4297_deqFrom4288 = Utils.FixedPoint(true,32,0,x4288_$anonfun_rdata(0))
  // results in ()
}
