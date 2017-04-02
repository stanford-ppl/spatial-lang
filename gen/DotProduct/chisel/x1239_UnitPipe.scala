package accel
import templates._
import types._
import chisel3._
trait x1239_UnitPipe extends x1266_UnitPipe {
  // Controller Stack: Stack(x1266, x1303, x1323, x1327)
  val x1234 = io.argIns(1)
  val x722 = b714 * 4.U(32.W)
  val x1235_sumx722_x1234 = x722 + x1234
  x1236_tuple := Utils.Cat(x1235_sumx722_x1234,2560.U(32.W)(31,0),true.B)
  x1231_valid := x1239_UnitPipe_done & true.B
  x1231_data := x1236_tuple
  converted_data := x1231_data
  x1232_$anonfun_writeEn := x1239_UnitPipe_ctr_en & true.B 
  x1232_$anonfun_wdata := Vec(List(640.U(32.W).number))
  // results in x1238_enqTo1232
}
