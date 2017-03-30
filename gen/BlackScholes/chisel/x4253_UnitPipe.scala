package accel
import templates._
import types._
import chisel3._
trait x4253_UnitPipe extends x4265_UnitPipe {
  // Controller Stack: Stack(x4265, x4350, x4469, x4470)
  val x4248 = io.argIns(4)
  val x2973_reuse1 = b2960 * 4.U(32.W)
  val x4249_sumx2973_x4248 = x2973_reuse1 + x4248
  val x4250_tuple = Utils.Cat(x4249_sumx2973_x4248,2560.U(32.W),true.B)
  x4245_valid := x4253_UnitPipe_done & true.B
  x4245_data := x4250_tuple
  x4246_$anonfun_writeEn := x4253_UnitPipe_ctr_en & true.B 
  x4246_$anonfun_wdata := Vec(List(640.U(32.W).number))
  // results in x4252_enqTo4246
}
