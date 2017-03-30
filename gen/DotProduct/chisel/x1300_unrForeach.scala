package accel
import templates._
import types._
import chisel3._
trait x1300_unrForeach extends x1301_UnitPipe {
  // Controller Stack: Stack(x1301, x1302, x1303, x1323, x1327)
  b817 := x1279_ctr(0)
  b818 := x1279_ctr(1)
  b819 := x1279_ctr(2)
  b820 := x1279_ctr(3)
  b821 := x1279_ctr(4)
  b822 := x1279_ctr(5)
  b823 := x1279_ctr(6)
  b824 := x1279_ctr(7)
  b825 := x1279_ctr(8)
  b826 := x1279_ctr(9)
  b827 := x1279_ctr(10)
  b828 := x1279_ctr(11)
  b829 := x1279_ctr(12)
  b830 := x1279_ctr(13)
  b831 := x1279_ctr(14)
  b832 := x1279_ctr(15)
  val x849 = b833 && b715
  val x850 = b834 && b715
  val x851 = b835 && b715
  val x852 = b836 && b715
  val x853 = b837 && b715
  val x854 = b838 && b715
  val x855 = b839 && b715
  val x856 = b840 && b715
  val x857 = b841 && b715
  val x858 = b842 && b715
  val x859 = b843 && b715
  val x860 = b844 && b715
  val x861 = b845 && b715
  val x862 = b846 && b715
  val x863 = b847 && b715
  val x864 = b848 && b715
  val x1281 = List(x849,x850,x851,x852,x853,x854,x855,x856,x857,x858,x859,x860,x861,x862,x863,x864).zipWithIndex.map{case (en, i) => x1269_data(i) }
  val x1282_elem0 = x1281.apply(0)
  val x1283_elem1 = x1281.apply(1)
  val x1284_elem2 = x1281.apply(2)
  val x1285_elem3 = x1281.apply(3)
  val x1286_elem4 = x1281.apply(4)
  val x1287_elem5 = x1281.apply(5)
  val x1288_elem6 = x1281.apply(6)
  val x1289_elem7 = x1281.apply(7)
  val x1290_elem8 = x1281.apply(8)
  val x1291_elem9 = x1281.apply(9)
  val x1292_elem10 = x1281.apply(10)
  val x1293_elem11 = x1281.apply(11)
  val x1294_elem12 = x1281.apply(12)
  val x1295_elem13 = x1281.apply(13)
  val x1296_elem14 = x1281.apply(14)
  val x1297_elem15 = x1281.apply(15)
  val x1298_vecified = Array(x1282_elem0,x1283_elem1,x1284_elem2,x1285_elem3,x1286_elem4,x1287_elem5,x1288_elem6,x1289_elem7,x1290_elem8,x1291_elem9,x1292_elem10,x1293_elem11,x1294_elem12,x1295_elem13,x1296_elem14,x1297_elem15)
  // Assemble multidimW vector
  val x1299_parSt_wVec = Wire(Vec(16, new multidimW(1, 32))) 
  x1299_parSt_wVec.zip(x1298_vecified).foreach{ case (port, dat) => port.data := dat.number }
  x1299_parSt_wVec(0).en := x849
  x1299_parSt_wVec(0).addr(0) := b817 
  x1299_parSt_wVec(1).en := x850
  x1299_parSt_wVec(1).addr(0) := b818 
  x1299_parSt_wVec(2).en := x851
  x1299_parSt_wVec(2).addr(0) := b819 
  x1299_parSt_wVec(3).en := x852
  x1299_parSt_wVec(3).addr(0) := b820 
  x1299_parSt_wVec(4).en := x853
  x1299_parSt_wVec(4).addr(0) := b821 
  x1299_parSt_wVec(5).en := x854
  x1299_parSt_wVec(5).addr(0) := b822 
  x1299_parSt_wVec(6).en := x855
  x1299_parSt_wVec(6).addr(0) := b823 
  x1299_parSt_wVec(7).en := x856
  x1299_parSt_wVec(7).addr(0) := b824 
  x1299_parSt_wVec(8).en := x857
  x1299_parSt_wVec(8).addr(0) := b825 
  x1299_parSt_wVec(9).en := x858
  x1299_parSt_wVec(9).addr(0) := b826 
  x1299_parSt_wVec(10).en := x859
  x1299_parSt_wVec(10).addr(0) := b827 
  x1299_parSt_wVec(11).en := x860
  x1299_parSt_wVec(11).addr(0) := b828 
  x1299_parSt_wVec(12).en := x861
  x1299_parSt_wVec(12).addr(0) := b829 
  x1299_parSt_wVec(13).en := x862
  x1299_parSt_wVec(13).addr(0) := b830 
  x1299_parSt_wVec(14).en := x863
  x1299_parSt_wVec(14).addr(0) := b831 
  x1299_parSt_wVec(15).en := x864
  x1299_parSt_wVec(15).addr(0) := b832 
  x1230_bBlk_0.connectWPort(x1299_parSt_wVec, x1300_unrForeach_enq, List(0))
  // results in ()
}
