package accel
import templates._
import chisel3._
import types._
trait GlobalWires extends IOModule{
val AccelController_done = Wire(Bool())
val x4469_unrForeach_done = Wire(Bool())
val x4469_unrForeach_en = Wire(Bool())
val x4469_unrForeach_resetter = Wire(Bool())
val x4214_readx4189 = Wire(new FixedPoint(true, 32, 0))
x4214_readx4189.number := io.argIns(0)
val x4216_ctrchain_done = Wire(Bool())
val x4216_ctrchain_resetter = Wire(Bool())
val x4216_ctrchain_en = Wire(Bool())
val x4350_done = Wire(Bool())
val x4350_en = Wire(Bool())
val x4350_resetter = Wire(Bool())
val x4444_unrForeach_done = Wire(Bool())
val x4444_unrForeach_en = Wire(Bool())
val x4444_unrForeach_resetter = Wire(Bool())
val x4468_UnitPipe_done = Wire(Bool())
val x4468_UnitPipe_en = Wire(Bool())
val x4468_UnitPipe_resetter = Wire(Bool())
val b2960 = Wire(UInt(32.W))
val x4217_typeBlk_0 = Module(new NBufSRAM(List(640), 2, 32,
  List(1), List(1),
  1, 1, 
  1, 1, "BankedMemory", 32 // TODO: Be more precise with parallelizations 
))
val x4218_priceBlk_0 = Module(new NBufSRAM(List(640), 2, 32,
  List(1), List(1),
  1, 1, 
  1, 1, "BankedMemory", 32 // TODO: Be more precise with parallelizations 
))
val x4219_strikeBlk_0 = Module(new NBufSRAM(List(640), 2, 32,
  List(1), List(1),
  1, 1, 
  1, 1, "BankedMemory", 32 // TODO: Be more precise with parallelizations 
))
val x4220_rateBlk_0 = Module(new NBufSRAM(List(640), 2, 32,
  List(1), List(1),
  1, 1, 
  1, 1, "BankedMemory", 32 // TODO: Be more precise with parallelizations 
))
val x4221_volBlk_0 = Module(new NBufSRAM(List(640), 2, 32,
  List(1), List(1),
  1, 1, 
  1, 1, "BankedMemory", 32 // TODO: Be more precise with parallelizations 
))
val x4222_timeBlk_0 = Module(new NBufSRAM(List(640), 2, 32,
  List(1), List(1),
  1, 1, 
  1, 1, "BankedMemory", 32 // TODO: Be more precise with parallelizations 
))
val x4223_optpriceBlk_0 = Module(new NBufSRAM(List(640), 2, 32,
  List(1), List(1),
  1, 1, 
  1, 1, "BankedMemory", 32 // TODO: Be more precise with parallelizations 
))
val x4244_UnitPipe_done = Wire(Bool())
val x4244_UnitPipe_en = Wire(Bool())
val x4244_UnitPipe_resetter = Wire(Bool())
val x4265_UnitPipe_done = Wire(Bool())
val x4265_UnitPipe_en = Wire(Bool())
val x4265_UnitPipe_resetter = Wire(Bool())
val x4286_UnitPipe_done = Wire(Bool())
val x4286_UnitPipe_en = Wire(Bool())
val x4286_UnitPipe_resetter = Wire(Bool())
val x4307_UnitPipe_done = Wire(Bool())
val x4307_UnitPipe_en = Wire(Bool())
val x4307_UnitPipe_resetter = Wire(Bool())
val x4328_UnitPipe_done = Wire(Bool())
val x4328_UnitPipe_en = Wire(Bool())
val x4328_UnitPipe_resetter = Wire(Bool())
val x4349_UnitPipe_done = Wire(Bool())
val x4349_UnitPipe_en = Wire(Bool())
val x4349_UnitPipe_resetter = Wire(Bool())
val x4232_UnitPipe_done = Wire(Bool())
val x4232_UnitPipe_en = Wire(Bool())
val x4232_UnitPipe_resetter = Wire(Bool())
val x4243_UnitPipe_done = Wire(Bool())
val x4243_UnitPipe_en = Wire(Bool())
val x4243_UnitPipe_resetter = Wire(Bool())
val x4225_$anonfun = Module(new FIFO(1, 1, 16, 32)) // $anonfun
val x4224_valid = Wire(Bool())
val x4224_data = Wire(UInt(65.W))
val x4242_unrForeach_enq = io.memStreams(6).rdata.valid
val x4226_ready = io.memStreams(6).rdata.valid
val x4224_ready = true.B // Assume cmd fifo will never fill up
val x4235_UnitPipe_done = Wire(Bool())
val x4235_UnitPipe_en = Wire(Bool())
val x4235_UnitPipe_resetter = Wire(Bool())
val x4242_unrForeach_done = Wire(Bool())
val x4242_unrForeach_en = Wire(Bool())
val x4242_unrForeach_resetter = Wire(Bool())
val x4237_ctrchain_done = Wire(Bool())
val x4237_ctrchain_resetter = Wire(Bool())
val x4237_ctrchain_en = Wire(Bool())
val b2984 = Wire(UInt(32.W))
val x4253_UnitPipe_done = Wire(Bool())
val x4253_UnitPipe_en = Wire(Bool())
val x4253_UnitPipe_resetter = Wire(Bool())
val x4264_UnitPipe_done = Wire(Bool())
val x4264_UnitPipe_en = Wire(Bool())
val x4264_UnitPipe_resetter = Wire(Bool())
val x4246_$anonfun = Module(new FIFO(1, 1, 16, 32)) // $anonfun
val x4245_valid = Wire(Bool())
val x4245_data = Wire(UInt(65.W))
val x4263_unrForeach_enq = io.memStreams(3).rdata.valid
val x4247_ready = io.memStreams(3).rdata.valid
val x4245_ready = true.B // Assume cmd fifo will never fill up
val x4256_UnitPipe_done = Wire(Bool())
val x4256_UnitPipe_en = Wire(Bool())
val x4256_UnitPipe_resetter = Wire(Bool())
val x4263_unrForeach_done = Wire(Bool())
val x4263_unrForeach_en = Wire(Bool())
val x4263_unrForeach_resetter = Wire(Bool())
val x4258_ctrchain_done = Wire(Bool())
val x4258_ctrchain_resetter = Wire(Bool())
val x4258_ctrchain_en = Wire(Bool())
val b3008 = Wire(UInt(32.W))
val x4274_UnitPipe_done = Wire(Bool())
val x4274_UnitPipe_en = Wire(Bool())
val x4274_UnitPipe_resetter = Wire(Bool())
val x4285_UnitPipe_done = Wire(Bool())
val x4285_UnitPipe_en = Wire(Bool())
val x4285_UnitPipe_resetter = Wire(Bool())
val x4267_$anonfun = Module(new FIFO(1, 1, 16, 32)) // $anonfun
val x4266_valid = Wire(Bool())
val x4266_data = Wire(UInt(65.W))
val x4284_unrForeach_enq = io.memStreams(5).rdata.valid
val x4268_ready = io.memStreams(5).rdata.valid
val x4266_ready = true.B // Assume cmd fifo will never fill up
val x4277_UnitPipe_done = Wire(Bool())
val x4277_UnitPipe_en = Wire(Bool())
val x4277_UnitPipe_resetter = Wire(Bool())
val x4284_unrForeach_done = Wire(Bool())
val x4284_unrForeach_en = Wire(Bool())
val x4284_unrForeach_resetter = Wire(Bool())
val x4279_ctrchain_done = Wire(Bool())
val x4279_ctrchain_resetter = Wire(Bool())
val x4279_ctrchain_en = Wire(Bool())
val b3032 = Wire(UInt(32.W))
val x4295_UnitPipe_done = Wire(Bool())
val x4295_UnitPipe_en = Wire(Bool())
val x4295_UnitPipe_resetter = Wire(Bool())
val x4306_UnitPipe_done = Wire(Bool())
val x4306_UnitPipe_en = Wire(Bool())
val x4306_UnitPipe_resetter = Wire(Bool())
val x4288_$anonfun = Module(new FIFO(1, 1, 16, 32)) // $anonfun
val x4287_valid = Wire(Bool())
val x4287_data = Wire(UInt(65.W))
val x4305_unrForeach_enq = io.memStreams(2).rdata.valid
val x4289_ready = io.memStreams(2).rdata.valid
val x4287_ready = true.B // Assume cmd fifo will never fill up
val x4298_UnitPipe_done = Wire(Bool())
val x4298_UnitPipe_en = Wire(Bool())
val x4298_UnitPipe_resetter = Wire(Bool())
val x4305_unrForeach_done = Wire(Bool())
val x4305_unrForeach_en = Wire(Bool())
val x4305_unrForeach_resetter = Wire(Bool())
val x4300_ctrchain_done = Wire(Bool())
val x4300_ctrchain_resetter = Wire(Bool())
val x4300_ctrchain_en = Wire(Bool())
val b3056 = Wire(UInt(32.W))
val x4316_UnitPipe_done = Wire(Bool())
val x4316_UnitPipe_en = Wire(Bool())
val x4316_UnitPipe_resetter = Wire(Bool())
val x4327_UnitPipe_done = Wire(Bool())
val x4327_UnitPipe_en = Wire(Bool())
val x4327_UnitPipe_resetter = Wire(Bool())
val x4309_$anonfun = Module(new FIFO(1, 1, 16, 32)) // $anonfun
val x4308_valid = Wire(Bool())
val x4308_data = Wire(UInt(65.W))
val x4326_unrForeach_enq = io.memStreams(0).rdata.valid
val x4310_ready = io.memStreams(0).rdata.valid
val x4308_ready = true.B // Assume cmd fifo will never fill up
val x4319_UnitPipe_done = Wire(Bool())
val x4319_UnitPipe_en = Wire(Bool())
val x4319_UnitPipe_resetter = Wire(Bool())
val x4326_unrForeach_done = Wire(Bool())
val x4326_unrForeach_en = Wire(Bool())
val x4326_unrForeach_resetter = Wire(Bool())
val x4321_ctrchain_done = Wire(Bool())
val x4321_ctrchain_resetter = Wire(Bool())
val x4321_ctrchain_en = Wire(Bool())
val b3080 = Wire(UInt(32.W))
val x4337_UnitPipe_done = Wire(Bool())
val x4337_UnitPipe_en = Wire(Bool())
val x4337_UnitPipe_resetter = Wire(Bool())
val x4348_UnitPipe_done = Wire(Bool())
val x4348_UnitPipe_en = Wire(Bool())
val x4348_UnitPipe_resetter = Wire(Bool())
val x4330_$anonfun = Module(new FIFO(1, 1, 16, 32)) // $anonfun
val x4329_valid = Wire(Bool())
val x4329_data = Wire(UInt(65.W))
val x4347_unrForeach_enq = io.memStreams(4).rdata.valid
val x4331_ready = io.memStreams(4).rdata.valid
val x4329_ready = true.B // Assume cmd fifo will never fill up
val x4340_UnitPipe_done = Wire(Bool())
val x4340_UnitPipe_en = Wire(Bool())
val x4340_UnitPipe_resetter = Wire(Bool())
val x4347_unrForeach_done = Wire(Bool())
val x4347_unrForeach_en = Wire(Bool())
val x4347_unrForeach_resetter = Wire(Bool())
val x4342_ctrchain_done = Wire(Bool())
val x4342_ctrchain_resetter = Wire(Bool())
val x4342_ctrchain_en = Wire(Bool())
val b3104 = Wire(UInt(32.W))
val x4352_ctrchain_done = Wire(Bool())
val x4352_ctrchain_resetter = Wire(Bool())
val x4352_ctrchain_en = Wire(Bool())
val b3117 = Wire(UInt(32.W))
val x4463_UnitPipe_done = Wire(Bool())
val x4463_UnitPipe_en = Wire(Bool())
val x4463_UnitPipe_resetter = Wire(Bool())
val x4467_UnitPipe_done = Wire(Bool())
val x4467_UnitPipe_en = Wire(Bool())
val x4467_UnitPipe_resetter = Wire(Bool())
val x4446_$anonfun = Module(new FIFO(1, 1, 16, 32)) // $anonfun
val x4454_UnitPipe_done = Wire(Bool())
val x4454_UnitPipe_en = Wire(Bool())
val x4454_UnitPipe_resetter = Wire(Bool())
val x4462_unrForeach_done = Wire(Bool())
val x4462_unrForeach_en = Wire(Bool())
val x4462_unrForeach_resetter = Wire(Bool())
val x4445_valid = Wire(Bool())
val x4445_data = Wire(UInt(65.W))
val x4456_ctrchain_done = Wire(Bool())
val x4456_ctrchain_resetter = Wire(Bool())
val x4456_ctrchain_en = Wire(Bool())
val b3224 = Wire(UInt(32.W))
val x4447_data = Wire(Vec(1, UInt(32.W)))
val x4447_en = Wire(Bool())
val x4445_ready = true.B // Assume cmd fifo will never fill up
val x4447_ready = true.B // Assume cmd fifo will never fill up
val x4448_ready = io.memStreams(1).rdata.valid // [sic] rData signal is used for write ack
val x4448_data = 0.U // Definitely wrong signal
}
