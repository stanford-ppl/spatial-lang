package accel
import templates._
import types._
import chisel3._
trait x1264_unrForeach extends x1265_UnitPipe {
  // Controller Stack: Stack(x1265, x1266, x1303, x1323, x1327)
  b733 := x1243_ctr(0)
  b734 := x1243_ctr(1)
  b735 := x1243_ctr(2)
  b736 := x1243_ctr(3)
  b737 := x1243_ctr(4)
  b738 := x1243_ctr(5)
  b739 := x1243_ctr(6)
  b740 := x1243_ctr(7)
  b741 := x1243_ctr(8)
  b742 := x1243_ctr(9)
  b743 := x1243_ctr(10)
  b744 := x1243_ctr(11)
  b745 := x1243_ctr(12)
  b746 := x1243_ctr(13)
  b747 := x1243_ctr(14)
  b748 := x1243_ctr(15)
  val x765 = b749 && b715
  val x766 = b750 && b715
  val x767 = b751 && b715
  val x768 = b752 && b715
  val x769 = b753 && b715
  val x770 = b754 && b715
  val x771 = b755 && b715
  val x772 = b756 && b715
  val x773 = b757 && b715
  val x774 = b758 && b715
  val x775 = b759 && b715
  val x776 = b760 && b715
  val x777 = b761 && b715
  val x778 = b762 && b715
  val x779 = b763 && b715
  val x780 = b764 && b715
  val x1245 = List(x765,x766,x767,x768,x769,x770,x771,x772,x773,x774,x775,x776,x777,x778,x779,x780).zipWithIndex.map{case (en, i) => x1233_data(i) }
  val x1246_elem0 = x1245.apply(0)
  val x1247_elem1 = x1245.apply(1)
  val x1248_elem2 = x1245.apply(2)
  val x1249_elem3 = x1245.apply(3)
  val x1250_elem4 = x1245.apply(4)
  val x1251_elem5 = x1245.apply(5)
  val x1252_elem6 = x1245.apply(6)
  val x1253_elem7 = x1245.apply(7)
  val x1254_elem8 = x1245.apply(8)
  val x1255_elem9 = x1245.apply(9)
  val x1256_elem10 = x1245.apply(10)
  val x1257_elem11 = x1245.apply(11)
  val x1258_elem12 = x1245.apply(12)
  val x1259_elem13 = x1245.apply(13)
  val x1260_elem14 = x1245.apply(14)
  val x1261_elem15 = x1245.apply(15)
  val x1262_vecified = Array(x1246_elem0,x1247_elem1,x1248_elem2,x1249_elem3,x1250_elem4,x1251_elem5,x1252_elem6,x1253_elem7,x1254_elem8,x1255_elem9,x1256_elem10,x1257_elem11,x1258_elem12,x1259_elem13,x1260_elem14,x1261_elem15)
  // Assemble multidimW vector
  val x1263_parSt_wVec = Wire(Vec(16, new multidimW(1, 32))) 
  x1263_parSt_wVec.zip(x1262_vecified).foreach{ case (port, dat) => port.data := dat.number }
  x1263_parSt_wVec(0).en := x765
  x1263_parSt_wVec(0).addr(0) := b733 
  x1263_parSt_wVec(1).en := x766
  x1263_parSt_wVec(1).addr(0) := b734 
  x1263_parSt_wVec(2).en := x767
  x1263_parSt_wVec(2).addr(0) := b735 
  x1263_parSt_wVec(3).en := x768
  x1263_parSt_wVec(3).addr(0) := b736 
  x1263_parSt_wVec(4).en := x769
  x1263_parSt_wVec(4).addr(0) := b737 
  x1263_parSt_wVec(5).en := x770
  x1263_parSt_wVec(5).addr(0) := b738 
  x1263_parSt_wVec(6).en := x771
  x1263_parSt_wVec(6).addr(0) := b739 
  x1263_parSt_wVec(7).en := x772
  x1263_parSt_wVec(7).addr(0) := b740 
  x1263_parSt_wVec(8).en := x773
  x1263_parSt_wVec(8).addr(0) := b741 
  x1263_parSt_wVec(9).en := x774
  x1263_parSt_wVec(9).addr(0) := b742 
  x1263_parSt_wVec(10).en := x775
  x1263_parSt_wVec(10).addr(0) := b743 
  x1263_parSt_wVec(11).en := x776
  x1263_parSt_wVec(11).addr(0) := b744 
  x1263_parSt_wVec(12).en := x777
  x1263_parSt_wVec(12).addr(0) := b745 
  x1263_parSt_wVec(13).en := x778
  x1263_parSt_wVec(13).addr(0) := b746 
  x1263_parSt_wVec(14).en := x779
  x1263_parSt_wVec(14).addr(0) := b747 
  x1263_parSt_wVec(15).en := x780
  x1263_parSt_wVec(15).addr(0) := b748 
  x1229_aBlk_0.connectWPort(x1263_parSt_wVec, x1264_unrForeach_enq, List(0))
  // results in ()
}
