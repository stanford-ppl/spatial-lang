package accel
import templates._
import types._
import chisel3._
trait x4274_UnitPipe extends x4286_UnitPipe {
  // Controller Stack: Stack(x4286, x4350, x4469, x4470)
  val x4269 = io.argIns(6)
  val x2973_reuse2 = b2960 * 4.U(32.W)
  val x4270_sumx2973_x4269 = x2973_reuse2 + x4269
  val x4271_tuple = Utils.Cat(x4270_sumx2973_x4269,2560.U(32.W),true.B)
  x4266_valid := x4274_UnitPipe_done & true.B
  x4266_data := x4271_tuple
  x4267_$anonfun_writeEn := x4274_UnitPipe_ctr_en & true.B 
  x4267_$anonfun_wdata := Vec(List(640.U(32.W).number))
  // results in x4273_enqTo4267
}
