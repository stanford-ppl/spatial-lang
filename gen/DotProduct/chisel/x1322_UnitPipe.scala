package accel
import templates._
import types._
import chisel3._
trait x1322_UnitPipe extends x1323_unrRed {
  // Controller Stack: Stack(x1323, x1327)
  val x1317_readx1225 = Utils.FixedPoint(1, 32, 0, x1225_reg_initval // get reset value that was created by reduce controller
  val x1318_readx1304 = x1304_$anonfun_1.read(1)
  val x910 = b714_chain.read(2) === 0.U(32.W)
  val x1319_sumx1318_x1317 = x1318_readx1304 + x1317_readx1225
  val x1320 = Mux((x910), x1318_readx1304, x1319_sumx1318_x1317)
  x1225_reg_0.write(x1225_reg, true.B & Utils.delay(x1225_reg_wren,1) /* TODO: This delay actually depends on latency of reduction function */, false.B, List(0))
  x1225_reg_1.io.next := x1320.number
  x1225_reg_1.io.enable := x1225_reg_wren
  x1225_reg_1.io.reset := Utils.delay(x1225_reg_resetter, 2)
  x1225_reg := x1225_reg_1.io.output
  // results in x1321_writex1225
}
