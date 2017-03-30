package accel
import templates._
import types._
import chisel3._
trait x4350 extends x4469_unrForeach {
  // Controller Stack: Stack(x4469, x4470)
  //  ---- OUTER: Begin Streampipe x4244_UnitPipe Controller ----
  val x4244_UnitPipe_offset = 0 // TODO: Compute real delays
  val x4244_UnitPipe_sm = Module(new Streampipe(2, false))
  x4244_UnitPipe_sm.io.input.enable := x4244_UnitPipe_en;
  x4244_UnitPipe_done := Utils.delay(x4244_UnitPipe_sm.io.output.done, x4244_UnitPipe_offset)
  val x4244_UnitPipe_rst_en = x4244_UnitPipe_sm.io.output.rst_en // Generally used in inner pipes
  val x4244_UnitPipe_datapath_en = x4244_UnitPipe_en
  x4244_UnitPipe_sm.io.input.forever := false.B
  // ---- Begin Streampipe x4244_UnitPipe Children Signals ----
  x4244_UnitPipe_sm.io.input.stageDone(0) := x4232_UnitPipe_done;
  x4232_UnitPipe_en := x4244_UnitPipe_sm.io.output.stageEnable(0) & x4224_ready /*not sure if this sig exists*/ & ~x4225_$anonfun.io.full  
  x4232_UnitPipe_resetter := x4244_UnitPipe_sm.io.output.rst_en
  x4244_UnitPipe_sm.io.input.stageDone(1) := x4243_UnitPipe_done;
  x4243_UnitPipe_en := x4244_UnitPipe_sm.io.output.stageEnable(1)    
  x4243_UnitPipe_resetter := x4244_UnitPipe_sm.io.output.rst_en
  // Creating sub kernel x4244_UnitPipe
  //  ---- OUTER: Begin Streampipe x4265_UnitPipe Controller ----
  val x4265_UnitPipe_offset = 0 // TODO: Compute real delays
  val x4265_UnitPipe_sm = Module(new Streampipe(2, false))
  x4265_UnitPipe_sm.io.input.enable := x4265_UnitPipe_en;
  x4265_UnitPipe_done := Utils.delay(x4265_UnitPipe_sm.io.output.done, x4265_UnitPipe_offset)
  val x4265_UnitPipe_rst_en = x4265_UnitPipe_sm.io.output.rst_en // Generally used in inner pipes
  val x4265_UnitPipe_datapath_en = x4265_UnitPipe_en
  x4265_UnitPipe_sm.io.input.forever := false.B
  // ---- Begin Streampipe x4265_UnitPipe Children Signals ----
  x4265_UnitPipe_sm.io.input.stageDone(0) := x4253_UnitPipe_done;
  x4253_UnitPipe_en := x4265_UnitPipe_sm.io.output.stageEnable(0) & x4245_ready /*not sure if this sig exists*/ & ~x4246_$anonfun.io.full  
  x4253_UnitPipe_resetter := x4265_UnitPipe_sm.io.output.rst_en
  x4265_UnitPipe_sm.io.input.stageDone(1) := x4264_UnitPipe_done;
  x4264_UnitPipe_en := x4265_UnitPipe_sm.io.output.stageEnable(1)    
  x4264_UnitPipe_resetter := x4265_UnitPipe_sm.io.output.rst_en
  // Creating sub kernel x4265_UnitPipe
  //  ---- OUTER: Begin Streampipe x4286_UnitPipe Controller ----
  val x4286_UnitPipe_offset = 0 // TODO: Compute real delays
  val x4286_UnitPipe_sm = Module(new Streampipe(2, false))
  x4286_UnitPipe_sm.io.input.enable := x4286_UnitPipe_en;
  x4286_UnitPipe_done := Utils.delay(x4286_UnitPipe_sm.io.output.done, x4286_UnitPipe_offset)
  val x4286_UnitPipe_rst_en = x4286_UnitPipe_sm.io.output.rst_en // Generally used in inner pipes
  val x4286_UnitPipe_datapath_en = x4286_UnitPipe_en
  x4286_UnitPipe_sm.io.input.forever := false.B
  // ---- Begin Streampipe x4286_UnitPipe Children Signals ----
  x4286_UnitPipe_sm.io.input.stageDone(0) := x4274_UnitPipe_done;
  x4274_UnitPipe_en := x4286_UnitPipe_sm.io.output.stageEnable(0) & x4266_ready /*not sure if this sig exists*/ & ~x4267_$anonfun.io.full  
  x4274_UnitPipe_resetter := x4286_UnitPipe_sm.io.output.rst_en
  x4286_UnitPipe_sm.io.input.stageDone(1) := x4285_UnitPipe_done;
  x4285_UnitPipe_en := x4286_UnitPipe_sm.io.output.stageEnable(1)    
  x4285_UnitPipe_resetter := x4286_UnitPipe_sm.io.output.rst_en
  // Creating sub kernel x4286_UnitPipe
  //  ---- OUTER: Begin Streampipe x4307_UnitPipe Controller ----
  val x4307_UnitPipe_offset = 0 // TODO: Compute real delays
  val x4307_UnitPipe_sm = Module(new Streampipe(2, false))
  x4307_UnitPipe_sm.io.input.enable := x4307_UnitPipe_en;
  x4307_UnitPipe_done := Utils.delay(x4307_UnitPipe_sm.io.output.done, x4307_UnitPipe_offset)
  val x4307_UnitPipe_rst_en = x4307_UnitPipe_sm.io.output.rst_en // Generally used in inner pipes
  val x4307_UnitPipe_datapath_en = x4307_UnitPipe_en
  x4307_UnitPipe_sm.io.input.forever := false.B
  // ---- Begin Streampipe x4307_UnitPipe Children Signals ----
  x4307_UnitPipe_sm.io.input.stageDone(0) := x4295_UnitPipe_done;
  x4295_UnitPipe_en := x4307_UnitPipe_sm.io.output.stageEnable(0) & x4287_ready /*not sure if this sig exists*/ & ~x4288_$anonfun.io.full  
  x4295_UnitPipe_resetter := x4307_UnitPipe_sm.io.output.rst_en
  x4307_UnitPipe_sm.io.input.stageDone(1) := x4306_UnitPipe_done;
  x4306_UnitPipe_en := x4307_UnitPipe_sm.io.output.stageEnable(1)    
  x4306_UnitPipe_resetter := x4307_UnitPipe_sm.io.output.rst_en
  // Creating sub kernel x4307_UnitPipe
  //  ---- OUTER: Begin Streampipe x4328_UnitPipe Controller ----
  val x4328_UnitPipe_offset = 0 // TODO: Compute real delays
  val x4328_UnitPipe_sm = Module(new Streampipe(2, false))
  x4328_UnitPipe_sm.io.input.enable := x4328_UnitPipe_en;
  x4328_UnitPipe_done := Utils.delay(x4328_UnitPipe_sm.io.output.done, x4328_UnitPipe_offset)
  val x4328_UnitPipe_rst_en = x4328_UnitPipe_sm.io.output.rst_en // Generally used in inner pipes
  val x4328_UnitPipe_datapath_en = x4328_UnitPipe_en
  x4328_UnitPipe_sm.io.input.forever := false.B
  // ---- Begin Streampipe x4328_UnitPipe Children Signals ----
  x4328_UnitPipe_sm.io.input.stageDone(0) := x4316_UnitPipe_done;
  x4316_UnitPipe_en := x4328_UnitPipe_sm.io.output.stageEnable(0) & x4308_ready /*not sure if this sig exists*/ & ~x4309_$anonfun.io.full  
  x4316_UnitPipe_resetter := x4328_UnitPipe_sm.io.output.rst_en
  x4328_UnitPipe_sm.io.input.stageDone(1) := x4327_UnitPipe_done;
  x4327_UnitPipe_en := x4328_UnitPipe_sm.io.output.stageEnable(1)    
  x4327_UnitPipe_resetter := x4328_UnitPipe_sm.io.output.rst_en
  // Creating sub kernel x4328_UnitPipe
  //  ---- OUTER: Begin Streampipe x4349_UnitPipe Controller ----
  val x4349_UnitPipe_offset = 0 // TODO: Compute real delays
  val x4349_UnitPipe_sm = Module(new Streampipe(2, false))
  x4349_UnitPipe_sm.io.input.enable := x4349_UnitPipe_en;
  x4349_UnitPipe_done := Utils.delay(x4349_UnitPipe_sm.io.output.done, x4349_UnitPipe_offset)
  val x4349_UnitPipe_rst_en = x4349_UnitPipe_sm.io.output.rst_en // Generally used in inner pipes
  val x4349_UnitPipe_datapath_en = x4349_UnitPipe_en
  x4349_UnitPipe_sm.io.input.forever := false.B
  // ---- Begin Streampipe x4349_UnitPipe Children Signals ----
  x4349_UnitPipe_sm.io.input.stageDone(0) := x4337_UnitPipe_done;
  x4337_UnitPipe_en := x4349_UnitPipe_sm.io.output.stageEnable(0) & x4329_ready /*not sure if this sig exists*/ & ~x4330_$anonfun.io.full  
  x4337_UnitPipe_resetter := x4349_UnitPipe_sm.io.output.rst_en
  x4349_UnitPipe_sm.io.input.stageDone(1) := x4348_UnitPipe_done;
  x4348_UnitPipe_en := x4349_UnitPipe_sm.io.output.stageEnable(1)    
  x4348_UnitPipe_resetter := x4349_UnitPipe_sm.io.output.rst_en
  // Creating sub kernel x4349_UnitPipe
  // results in ()
}
