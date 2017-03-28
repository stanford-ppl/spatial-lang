package accel
import templates._
import fringe._
import chisel3._
import chisel3.util._
class AccelTop(val top_w: Int, val numArgIns: Int, val numArgOuts: Int, val numMemoryStreams: Int = 1) extends GlobalWires with IOModule
 with BufferControlCxns
 with RootController {

  // TODO: Figure out better way to pass constructor args to IOModule.  Currently just recreate args inside IOModule redundantly

}
