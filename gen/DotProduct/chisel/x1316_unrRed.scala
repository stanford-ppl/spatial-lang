package accel
import templates._
import types._
import chisel3._
trait x1316_unrRed extends x1323_unrRed {
  // Controller Stack: Stack(x1323, x1327)
  b891 := x1305_ctr(0)
  val x893 = b892 && b715
  // Assemble multidimR vector
  val x1307_parLd_rVec = Wire(Vec(1, new multidimR(1, 32)))
  x1307_parLd_rVec(0).en := x1316_unrRed_en
  x1307_parLd_rVec(0).addr(0) := b891 
  x1229_aBlk_0.connectRPort(Vec(x1307_parLd_rVec.toArray), 1)
  val x1307_parLd = (0 until 1).map{i => Utils.FixedPoint(true,32,0, x1229_aBlk_0.io.output.data(1*1+i))}
  // Assemble multidimR vector
  val x1308_parLd_rVec = Wire(Vec(1, new multidimR(1, 32)))
  x1308_parLd_rVec(0).en := x1316_unrRed_en
  x1308_parLd_rVec(0).addr(0) := b891 
  x1230_bBlk_0.connectRPort(Vec(x1308_parLd_rVec.toArray), 1)
  val x1308_parLd = (0 until 1).map{i => Utils.FixedPoint(true,32,0, x1230_bBlk_0.io.output.data(1*1+i))}
  val x1309_readx1304 = Utils.FixedPoint(1, 32, 0, x1304_$anonfun_initval // get reset value that was created by reduce controller
  val x900 = b891 === 0.U(32.W)
  val x1310_elem0 = x1307_parLd.apply(0)
  val x1311_elem0 = x1308_parLd.apply(0)
  val x1312 = x1310_elem0 * x1311_elem0
  val x1313_sumx1312_x1309 = x1312 + x1309_readx1304
  val x1314 = Mux((x900), x1312, x1313_sumx1312_x1309)
  x1304_$anonfun_0.io.next := x1314.number
  x1304_$anonfun_0.io.enable := x1304_$anonfun_wren
  x1304_$anonfun_0.io.reset := Utils.delay(x1304_$anonfun_resetter, 2)
  x1304_$anonfun := x1304_$anonfun_0.io.output
  x1304_$anonfun_1.write(x1304_$anonfun, true.B & Utils.delay(x1304_$anonfun_wren,1) /* TODO: This delay actually depends on latency of reduction function */, false.B, List(0))
  // results in ()
}
