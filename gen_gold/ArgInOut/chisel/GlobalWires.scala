package accel
import templates._
import chisel3._
import types._
trait GlobalWires extends IOModule{
val AccelController_done = Wire(Bool())
val x153_readx150 = Wire(new FixedPoint(true, 32, 0))
x153_readx150.number := io.argIns(0)
}
