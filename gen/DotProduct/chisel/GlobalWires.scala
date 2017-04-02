package accel
import templates._
import chisel3._
import types._
trait GlobalWires extends IOModule{
val AccelController_done = Wire(Bool())
val x1323_unrRed_done = Wire(Bool())
val x1323_unrRed_en = Wire(Bool())
val x1323_unrRed_resetter = Wire(Bool())
val x1326_UnitPipe_done = Wire(Bool())
val x1326_UnitPipe_en = Wire(Bool())
val x1326_UnitPipe_resetter = Wire(Bool())
val x1225_reg_0 = Module(new NBufFF(2, 32)) // 
val x1225_reg_1 = Module(new SpecialAccum(1,"add","FixedPoint", List(1,32,0))) // TODO: Create correct accum based on type
val x1226_readx1215 = Wire(new FixedPoint(true, 32, 0))
x1226_readx1215.number := io.argIns(0)
val x1228_ctrchain_done = Wire(Bool())
val x1228_ctrchain_resetter = Wire(Bool())
val x1228_ctrchain_en = Wire(Bool())
val x1303_done = Wire(Bool())
val x1303_en = Wire(Bool())
val x1303_resetter = Wire(Bool())
val x1316_unrRed_done = Wire(Bool())
val x1316_unrRed_en = Wire(Bool())
val x1316_unrRed_resetter = Wire(Bool())
val x1322_UnitPipe_done = Wire(Bool())
val x1322_UnitPipe_en = Wire(Bool())
val x1322_UnitPipe_resetter = Wire(Bool())
val b714 = Wire(UInt(32.W))
val x1229_aBlk_0 = Module(new NBufSRAM(List(640), 2, 32,
  List(16), List(16),
  1, 1, 
  16, 1, "BankedMemory", 32 // TODO: Be more precise with parallelizations 
))
val x1230_bBlk_0 = Module(new NBufSRAM(List(640), 2, 32,
  List(16), List(16),
  1, 1, 
  16, 1, "BankedMemory", 32 // TODO: Be more precise with parallelizations 
))
val x1266_UnitPipe_done = Wire(Bool())
val x1266_UnitPipe_en = Wire(Bool())
val x1266_UnitPipe_resetter = Wire(Bool())
val x1302_UnitPipe_done = Wire(Bool())
val x1302_UnitPipe_en = Wire(Bool())
val x1302_UnitPipe_resetter = Wire(Bool())
val x1239_UnitPipe_done = Wire(Bool())
val x1239_UnitPipe_en = Wire(Bool())
val x1239_UnitPipe_resetter = Wire(Bool())
val x1265_UnitPipe_done = Wire(Bool())
val x1265_UnitPipe_en = Wire(Bool())
val x1265_UnitPipe_resetter = Wire(Bool())
val x1232_$anonfun = Module(new FIFO(1, 1, 16, 32)) // $anonfun
val x1236_tuple = Wire(UInt(97.W))
val x1231_valid = Wire(Bool())
val x1231_data = Wire(UInt(16.W))
val converted_data = Wire(UInt(16.W))
val x1264_unrForeach_enq = io.memStreams(0).rdata.valid
val x1233_ready = io.memStreams(0).rdata.valid
val x1231_ready = true.B // Assume cmd fifo will never fill up
val x1242_UnitPipe_done = Wire(Bool())
val x1242_UnitPipe_en = Wire(Bool())
val x1242_UnitPipe_resetter = Wire(Bool())
val x1264_unrForeach_done = Wire(Bool())
val x1264_unrForeach_en = Wire(Bool())
val x1264_unrForeach_resetter = Wire(Bool())
val x1244_ctrchain_done = Wire(Bool())
val x1244_ctrchain_resetter = Wire(Bool())
val x1244_ctrchain_en = Wire(Bool())
val b733 = Wire(UInt(32.W))
val b734 = Wire(UInt(32.W))
val b735 = Wire(UInt(32.W))
val b736 = Wire(UInt(32.W))
val b737 = Wire(UInt(32.W))
val b738 = Wire(UInt(32.W))
val b739 = Wire(UInt(32.W))
val b740 = Wire(UInt(32.W))
val b741 = Wire(UInt(32.W))
val b742 = Wire(UInt(32.W))
val b743 = Wire(UInt(32.W))
val b744 = Wire(UInt(32.W))
val b745 = Wire(UInt(32.W))
val b746 = Wire(UInt(32.W))
val b747 = Wire(UInt(32.W))
val b748 = Wire(UInt(32.W))
val x1275_UnitPipe_done = Wire(Bool())
val x1275_UnitPipe_en = Wire(Bool())
val x1275_UnitPipe_resetter = Wire(Bool())
val x1301_UnitPipe_done = Wire(Bool())
val x1301_UnitPipe_en = Wire(Bool())
val x1301_UnitPipe_resetter = Wire(Bool())
val x1268_$anonfun = Module(new FIFO(1, 1, 16, 32)) // $anonfun
val x1272_tuple = Wire(UInt(97.W))
val x1267_valid = Wire(Bool())
val x1267_data = Wire(UInt(16.W))
val converted_data = Wire(UInt(16.W))
val x1300_unrForeach_enq = io.memStreams(1).rdata.valid
val x1269_ready = io.memStreams(1).rdata.valid
val x1267_ready = true.B // Assume cmd fifo will never fill up
val x1278_UnitPipe_done = Wire(Bool())
val x1278_UnitPipe_en = Wire(Bool())
val x1278_UnitPipe_resetter = Wire(Bool())
val x1300_unrForeach_done = Wire(Bool())
val x1300_unrForeach_en = Wire(Bool())
val x1300_unrForeach_resetter = Wire(Bool())
val x1280_ctrchain_done = Wire(Bool())
val x1280_ctrchain_resetter = Wire(Bool())
val x1280_ctrchain_en = Wire(Bool())
val b817 = Wire(UInt(32.W))
val b818 = Wire(UInt(32.W))
val b819 = Wire(UInt(32.W))
val b820 = Wire(UInt(32.W))
val b821 = Wire(UInt(32.W))
val b822 = Wire(UInt(32.W))
val b823 = Wire(UInt(32.W))
val b824 = Wire(UInt(32.W))
val b825 = Wire(UInt(32.W))
val b826 = Wire(UInt(32.W))
val b827 = Wire(UInt(32.W))
val b828 = Wire(UInt(32.W))
val b829 = Wire(UInt(32.W))
val b830 = Wire(UInt(32.W))
val b831 = Wire(UInt(32.W))
val b832 = Wire(UInt(32.W))
val x1304_$anonfun_0 = Module(new SpecialAccum(1,"add","FixedPoint", List(1,32,0))) // TODO: Create correct accum based on type
val x1304_$anonfun_1 = Module(new NBufFF(2, 32)) // $anonfun
val x1306_ctrchain_done = Wire(Bool())
val x1306_ctrchain_resetter = Wire(Bool())
val x1306_ctrchain_en = Wire(Bool())
val b891 = Wire(UInt(32.W))
val x1304_$anonfun = Wire(UInt(32.W))
val x1225_reg = Wire(UInt(32.W))
}
