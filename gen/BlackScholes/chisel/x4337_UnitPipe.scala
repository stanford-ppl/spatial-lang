package accel
import templates._
import types._
import chisel3._
trait x4337_UnitPipe extends x4349_UnitPipe {
  // Controller Stack: Stack(x4349, x4350, x4469, x4470)
  val x4332 = io.argIns(5)
  val x2973_reuse5 = b2960 * 4.U(32.W)
  val x4333_sumx2973_x4332 = x2973_reuse5 + x4332
  val x4334_tuple = Utils.Cat(x4333_sumx2973_x4332,2560.U(32.W),true.B)
  x4329_valid := x4337_UnitPipe_done & true.B
  x4329_data := x4334_tuple
  x4330_$anonfun_writeEn := x4337_UnitPipe_ctr_en & true.B 
  x4330_$anonfun_wdata := Vec(List(640.U(32.W).number))
  // results in x4336_enqTo4330
}
