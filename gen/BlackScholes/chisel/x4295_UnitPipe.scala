package accel
import templates._
import types._
import chisel3._
trait x4295_UnitPipe extends x4307_UnitPipe {
  // Controller Stack: Stack(x4307, x4350, x4469, x4470)
  val x4290 = io.argIns(3)
  val x2973_reuse3 = b2960 * 4.U(32.W)
  val x4291_sumx2973_x4290 = x2973_reuse3 + x4290
  val x4292_tuple = Utils.Cat(x4291_sumx2973_x4290,2560.U(32.W),true.B)
  x4287_valid := x4295_UnitPipe_done & true.B
  x4287_data := x4292_tuple
  x4288_$anonfun_writeEn := x4295_UnitPipe_ctr_en & true.B 
  x4288_$anonfun_wdata := Vec(List(640.U(32.W).number))
  // results in x4294_enqTo4288
}
