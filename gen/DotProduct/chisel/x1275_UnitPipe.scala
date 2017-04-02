package accel
import templates._
import types._
import chisel3._
trait x1275_UnitPipe extends x1302_UnitPipe {
  // Controller Stack: Stack(x1302, x1303, x1323, x1327)
  val x1270 = io.argIns(2)
  val x722_reuse1 = b714 * 4.U(32.W)
  val x1271_sumx722_x1270 = x722_reuse1 + x1270
  x1272_tuple := Utils.Cat(x1271_sumx722_x1270,2560.U(32.W)(31,0),true.B)
  x1267_valid := x1275_UnitPipe_done & true.B
  x1267_data := x1272_tuple
  converted_data := x1267_data
  x1268_$anonfun_writeEn := x1275_UnitPipe_ctr_en & true.B 
  x1268_$anonfun_wdata := Vec(List(640.U(32.W).number))
  // results in x1274_enqTo1268
}
