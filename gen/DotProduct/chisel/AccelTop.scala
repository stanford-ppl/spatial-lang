package accel
import templates._
import fringe._
import chisel3._
import chisel3.util._
class AccelTop(val top_w: Int, val numArgIns: Int, val numArgOuts: Int, val numMemoryStreams: Int = 1) extends GlobalWires with x1265_UnitPipe
 with x1301_UnitPipe
 with x1303
 with x1239_UnitPipe
 with x1323_unrRed
 with x1300_unrForeach
 with x1316_unrRed
 with x1326_UnitPipe
 with IOModule
 with BufferControlCxns
 with x1264_unrForeach
 with x1322_UnitPipe
 with x1278_UnitPipe
 with x1275_UnitPipe
 with x1266_UnitPipe
 with RootController
 with x1302_UnitPipe
 with x1242_UnitPipe {

  // TODO: Figure out better way to pass constructor args to IOModule.  Currently just recreate args inside IOModule redundantly

}
