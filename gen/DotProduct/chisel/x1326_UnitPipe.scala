package accel
import templates._
import types._
import chisel3._
trait x1326_UnitPipe extends RootController {
  // Controller Stack: Stack(x1327)
  val x1324_readx1225 = x1225_reg_0.read(1)
  val x1222_argout = RegInit(0.U) // HW-accessible register
  x1222_argout := Mux(true.B & x1326_UnitPipe_en, x1324_readx1225.number, x1222_argout)
  io.argOuts(0).bits := x1222_argout // out
  io.argOuts(0).valid := true.B & x1326_UnitPipe_en
  // results in ()
}
