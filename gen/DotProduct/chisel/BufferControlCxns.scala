package accel
import templates._
import fringe._
import chisel3._
trait BufferControlCxns extends RootController {
  x1229_aBlk_0.connectStageCtrl(x1303_done, x1303_en, List(0)) /* write */
  x1229_aBlk_0.connectStageCtrl(x1316_unrRed_done, x1316_unrRed_en, List(1)) /*read  */
  x1230_bBlk_0.connectStageCtrl(x1303_done, x1303_en, List(0)) /* write */
  x1230_bBlk_0.connectStageCtrl(x1316_unrRed_done, x1316_unrRed_en, List(1)) /*read  */
  x1225_reg_0.connectStageCtrl(x1323_unrRed_done, x1323_unrRed_en, List(0)) /* write */
  x1225_reg_0.connectStageCtrl(x1326_UnitPipe_done, x1326_UnitPipe_en, List(1)) /*read  */
  x1304_$anonfun_1.connectStageCtrl(x1316_unrRed_done, x1316_unrRed_en, List(0)) /* write */
  x1304_$anonfun_1.connectStageCtrl(x1322_UnitPipe_done, x1322_UnitPipe_en, List(1)) /*read  */
}
