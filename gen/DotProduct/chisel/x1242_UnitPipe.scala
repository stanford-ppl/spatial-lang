package accel
import templates._
import types._
import chisel3._
trait x1242_UnitPipe extends x1265_UnitPipe {
  // Controller Stack: Stack(x1265, x1266, x1303, x1323, x1327)
  x1232_$anonfun_readEn := x1242_UnitPipe_ctr_en & true.B
  val x1241_deqFrom1232 = Utils.FixedPoint(true,32,0,x1232_$anonfun_rdata(0))
  // results in ()
}
