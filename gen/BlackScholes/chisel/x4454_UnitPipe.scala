package accel
import templates._
import types._
import chisel3._
trait x4454_UnitPipe extends x4463_UnitPipe {
  // Controller Stack: Stack(x4463, x4468, x4469, x4470)
  val x4449 = io.argIns(2)
  val x2973_reuse6 = b2960_chain.read(2) * 4.U(32.W)
  val x4450_sumx2973_x4449 = x2973_reuse6 + x4449
  val x4451_tuple = Utils.Cat(x4450_sumx2973_x4449,2560.U(32.W),false.B)
  x4445_valid := x4454_UnitPipe_done & true.B
  x4445_data := x4451_tuple
  x4446_$anonfun_writeEn := x4454_UnitPipe_ctr_en & true.B 
  x4446_$anonfun_wdata := Vec(List(640.U(32.W).number))
  // results in x4453_enqTo4446
}
