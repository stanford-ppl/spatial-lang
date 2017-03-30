package accel
import templates._
import types._
import chisel3._
trait x4340_UnitPipe extends x4348_UnitPipe {
  // Controller Stack: Stack(x4348, x4349, x4350, x4469, x4470)
  x4330_$anonfun_readEn := x4340_UnitPipe_ctr_en & true.B
  val x4339_deqFrom4330 = Utils.FixedPoint(true,32,0,x4330_$anonfun_rdata(0))
  // results in ()
}
