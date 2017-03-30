package accel
import templates._
import fringe._
import chisel3._
import chisel3.util._
class AccelTop(val top_w: Int, val numArgIns: Int, val numArgOuts: Int, val numMemoryStreams: Int = 1) extends GlobalWires with x4467_UnitPipe
 with x4328_UnitPipe
 with x4235_UnitPipe
 with x4264_UnitPipe
 with x4307_UnitPipe
 with x4319_UnitPipe
 with x4350
 with x4265_UnitPipe
 with x4468_UnitPipe
 with x4284_unrForeach
 with IOModule
 with x4244_UnitPipe
 with BufferControlCxns
 with x4298_UnitPipe
 with x4243_UnitPipe
 with x4347_unrForeach
 with x4349_UnitPipe
 with x4462_unrForeach
 with x4274_UnitPipe
 with x4256_UnitPipe
 with x4277_UnitPipe
 with x4454_UnitPipe
 with x4337_UnitPipe
 with x4263_unrForeach
 with x4242_unrForeach
 with x4253_UnitPipe
 with x4326_unrForeach
 with RootController
 with x4316_UnitPipe
 with x4305_unrForeach
 with x4286_UnitPipe
 with x4306_UnitPipe
 with x4348_UnitPipe
 with x4463_UnitPipe
 with x4444_unrForeach
 with x4340_UnitPipe
 with x4469_unrForeach
 with x4295_UnitPipe
 with x4285_UnitPipe
 with x4232_UnitPipe
 with x4327_UnitPipe {

  // TODO: Figure out better way to pass constructor args to IOModule.  Currently just recreate args inside IOModule redundantly

}
