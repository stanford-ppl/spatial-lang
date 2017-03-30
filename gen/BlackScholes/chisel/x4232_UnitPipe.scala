package accel
import templates._
import types._
import chisel3._
trait x4232_UnitPipe extends x4244_UnitPipe {
  // Controller Stack: Stack(x4244, x4350, x4469, x4470)
  val x4227 = io.argIns(7)
  val x2973 = b2960 * 4.U(32.W)
  val x4228_sumx2973_x4227 = x2973 + x4227
  val x4229_tuple = Utils.Cat(x4228_sumx2973_x4227,2560.U(32.W),true.B)
  x4224_valid := x4232_UnitPipe_done & true.B
  x4224_data := x4229_tuple
  x4225_$anonfun_writeEn := x4232_UnitPipe_ctr_en & true.B 
  x4225_$anonfun_wdata := Vec(List(640.U(32.W).number))
  // results in x4231_enqTo4225
}
