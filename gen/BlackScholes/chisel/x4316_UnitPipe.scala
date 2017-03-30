package accel
import templates._
import types._
import chisel3._
trait x4316_UnitPipe extends x4328_UnitPipe {
  // Controller Stack: Stack(x4328, x4350, x4469, x4470)
  val x4311 = io.argIns(1)
  val x2973_reuse4 = b2960 * 4.U(32.W)
  val x4312_sumx2973_x4311 = x2973_reuse4 + x4311
  val x4313_tuple = Utils.Cat(x4312_sumx2973_x4311,2560.U(32.W),true.B)
  x4308_valid := x4316_UnitPipe_done & true.B
  x4308_data := x4313_tuple
  x4309_$anonfun_writeEn := x4316_UnitPipe_ctr_en & true.B 
  x4309_$anonfun_wdata := Vec(List(640.U(32.W).number))
  // results in x4315_enqTo4309
}
