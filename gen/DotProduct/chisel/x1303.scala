package accel
import templates._
import types._
import chisel3._
trait x1303 extends x1323_unrRed {
  // Controller Stack: Stack(x1323, x1327)
  //  ---- OUTER: Begin Streampipe x1266_UnitPipe Controller ----
  val x1266_UnitPipe_offset = 0 // TODO: Compute real delays
  val x1266_UnitPipe_sm = Module(new Streampipe(2, false))
  x1266_UnitPipe_sm.io.input.enable := x1266_UnitPipe_en;
  x1266_UnitPipe_done := Utils.delay(x1266_UnitPipe_sm.io.output.done, x1266_UnitPipe_offset)
  val x1266_UnitPipe_rst_en = x1266_UnitPipe_sm.io.output.rst_en // Generally used in inner pipes
  val x1266_UnitPipe_datapath_en = x1266_UnitPipe_en
  x1266_UnitPipe_sm.io.input.forever := false.B
  // ---- Begin Streampipe x1266_UnitPipe Children Signals ----
  x1266_UnitPipe_sm.io.input.stageDone(0) := x1239_UnitPipe_done;
  x1239_UnitPipe_en := x1266_UnitPipe_sm.io.output.stageEnable(0) & x1231_ready /*not sure if this sig exists*/ & ~x1232_$anonfun.io.full  
  x1239_UnitPipe_resetter := x1266_UnitPipe_sm.io.output.rst_en
  x1266_UnitPipe_sm.io.input.stageDone(1) := x1265_UnitPipe_done;
  x1265_UnitPipe_en := x1266_UnitPipe_sm.io.output.stageEnable(1)    
  x1265_UnitPipe_resetter := x1266_UnitPipe_sm.io.output.rst_en
  // Creating sub kernel x1266_UnitPipe
  //  ---- OUTER: Begin Streampipe x1302_UnitPipe Controller ----
  val x1302_UnitPipe_offset = 0 // TODO: Compute real delays
  val x1302_UnitPipe_sm = Module(new Streampipe(2, false))
  x1302_UnitPipe_sm.io.input.enable := x1302_UnitPipe_en;
  x1302_UnitPipe_done := Utils.delay(x1302_UnitPipe_sm.io.output.done, x1302_UnitPipe_offset)
  val x1302_UnitPipe_rst_en = x1302_UnitPipe_sm.io.output.rst_en // Generally used in inner pipes
  val x1302_UnitPipe_datapath_en = x1302_UnitPipe_en
  x1302_UnitPipe_sm.io.input.forever := false.B
  // ---- Begin Streampipe x1302_UnitPipe Children Signals ----
  x1302_UnitPipe_sm.io.input.stageDone(0) := x1275_UnitPipe_done;
  x1275_UnitPipe_en := x1302_UnitPipe_sm.io.output.stageEnable(0) & x1267_ready /*not sure if this sig exists*/ & ~x1268_$anonfun.io.full  
  x1275_UnitPipe_resetter := x1302_UnitPipe_sm.io.output.rst_en
  x1302_UnitPipe_sm.io.input.stageDone(1) := x1301_UnitPipe_done;
  x1301_UnitPipe_en := x1302_UnitPipe_sm.io.output.stageEnable(1)    
  x1301_UnitPipe_resetter := x1302_UnitPipe_sm.io.output.rst_en
  // Creating sub kernel x1302_UnitPipe
  // results in ()
}
