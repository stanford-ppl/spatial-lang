package accel
import templates._
import fringe._
import chisel3._
trait BufferControlCxns extends RootController {
  x4217_typeBlk_0.connectStageCtrl(x4350_done, x4350_en, List(0)) /* write */
  x4217_typeBlk_0.connectStageCtrl(x4444_unrForeach_done, x4444_unrForeach_en, List(1)) /*read  */
  x4218_priceBlk_0.connectStageCtrl(x4350_done, x4350_en, List(0)) /* write */
  x4218_priceBlk_0.connectStageCtrl(x4444_unrForeach_done, x4444_unrForeach_en, List(1)) /*read  */
  x4219_strikeBlk_0.connectStageCtrl(x4350_done, x4350_en, List(0)) /* write */
  x4219_strikeBlk_0.connectStageCtrl(x4444_unrForeach_done, x4444_unrForeach_en, List(1)) /*read  */
  x4220_rateBlk_0.connectStageCtrl(x4350_done, x4350_en, List(0)) /* write */
  x4220_rateBlk_0.connectStageCtrl(x4444_unrForeach_done, x4444_unrForeach_en, List(1)) /*read  */
  x4221_volBlk_0.connectStageCtrl(x4350_done, x4350_en, List(0)) /* write */
  x4221_volBlk_0.connectStageCtrl(x4444_unrForeach_done, x4444_unrForeach_en, List(1)) /*read  */
  x4222_timeBlk_0.connectStageCtrl(x4350_done, x4350_en, List(0)) /* write */
  x4222_timeBlk_0.connectStageCtrl(x4444_unrForeach_done, x4444_unrForeach_en, List(1)) /*read  */
  x4223_optpriceBlk_0.connectStageCtrl(x4444_unrForeach_done, x4444_unrForeach_en, List(0)) /* write */
  x4223_optpriceBlk_0.connectStageCtrl(x4468_UnitPipe_done, x4468_UnitPipe_en, List(1)) /*read  */
}
