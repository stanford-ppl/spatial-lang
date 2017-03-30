package accel
import templates._
import types._
import chisel3._
trait x1278_UnitPipe extends x1301_UnitPipe {
  // Controller Stack: Stack(x1301, x1302, x1303, x1323, x1327)
  x1268_$anonfun_readEn := x1278_UnitPipe_ctr_en & true.B
  val x1277_deqFrom1268 = Utils.FixedPoint(true,32,0,x1268_$anonfun_rdata(0))
  // results in ()
}
