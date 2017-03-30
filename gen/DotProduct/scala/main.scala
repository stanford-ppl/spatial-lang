import DataImplicits._
object Main {
  def main(args: Array[String]): Unit = {
    val x0 = args
    val x1 = x0.apply(Number(BigDecimal(0),true,FixedPoint(true,32,0)))
    val x2 = x1.toInt
    val x1212 = Array.tabulate(x2){bbb => 
      val b3 = Number(bbb)
      val x1211 = Number(scala.util.Random.nextInt())
      x1211
    }
    val x1214 = Array.tabulate(x2){bbb => 
      val b6 = Number(bbb)
      val x1213 = Number(scala.util.Random.nextInt())
      x1213
    }
    val x1215 = Array[Number](Number(BigDecimal(0),true,FixedPoint(true,32,0)))
    val x1216 = x1212.length
    val x1217 = x1215.update(0, x1216)
    val x1218 = x1215.apply(0)
    val x1219 = {
      Array.fill(x1218 + 16)(Number(BigDecimal(0),true,FixedPoint(true,32,0)))
    }
    val x1220 = x1215.apply(0)
    val x1221 = {
      Array.fill(x1220 + 16)(Number(BigDecimal(0),true,FixedPoint(true,32,0)))
    }
    val x1222 = Array[Number](Number(BigDecimal(0),true,FixedPoint(true,32,0)))
    val x1223 = System.arraycopy(x1212, 0, x1219, 0, x1212.length)
    val x1224 = System.arraycopy(x1214, 0, x1221, 0, x1214.length)
    /** BEGIN HARDWARE BLOCK x1327 **/
    val x1327 = {
      val x1225 = Array[Number](Number(BigDecimal(0),true,FixedPoint(true,32,0)))
      val x1226 = x1215.apply(0)
      val x1227 = Counter(Number(BigDecimal(0),true,FixedPoint(true,32,0)), x1226, Number(BigDecimal(640),true,FixedPoint(true,32,0)), Number(BigDecimal(1),true,FixedPoint(true,32,0)))
      val x1228 = Array(x1227)
      /** BEGIN UNROLLED REDUCE x1323 **/
      val x1323 = if (TRUE) {
        x1228(0).foreach{case (is,vs) => 
          val b714 = is(0)
          val b715 = vs(0)
          val x1229 = Array.fill(Number(BigDecimal(640),true,FixedPoint(true,32,0)))(X(FixedPoint(true,32,0)))
          val x1230 = Array.fill(Number(BigDecimal(640),true,FixedPoint(true,32,0)))(X(FixedPoint(true,32,0)))
          /** BEGIN PARALLEL PIPE x1303 **/
          val x1303 = if (b715) {
            /** BEGIN UNIT PIPE x1266 **/
            val x1266 = if (true) {
              val x1231 = new scala.collection.mutable.Queue[Struct1]
              val x1232 = new scala.collection.mutable.Queue[Number] // size: Number(BigDecimal(16),true,FixedPoint(true,32,0))
              val x1233 = new scala.collection.mutable.Queue[Number]
              /** BEGIN UNIT PIPE x1239 **/
              val x1239 = if (true) {
                val x1234 = 0
                val x722 = b714 * Number(BigDecimal(4),true,FixedPoint(true,32,0))
                val x1235 = x722 + x1234
                val x1236: Struct1 = new Struct1( x1235, Number(BigDecimal(2560),true,FixedPoint(true,32,0)), TRUE )
                val x1237 = if (TRUE) x1231.enqueue(x1236)
                val x1238 = if (TRUE) x1232.enqueue(Number(BigDecimal(640),true,FixedPoint(true,32,0)))
                x1238
              }
              /** END UNIT PIPE x1239 **/
              val x1240 = x1231.foreach{cmd => 
                for (i <- cmd.offset until cmd.offset+cmd.size by 4) {
                  val data = {
                    try {
                      x1219.apply(i / 4)
                    }
                    catch {case err: java.lang.ArrayIndexOutOfBoundsException => 
                      System.out.println("[warn] DotProduct.scala:36:16 Memory a (x1219): Out of bounds read at address " + err.getMessage)
                      X(FixedPoint(true,32,0))
                    }
                  }
                  x1233.enqueue(data)
                }
              }
              x1231.clear()
              /** BEGIN UNIT PIPE x1265 **/
              val x1265 = if (true) {
                /** BEGIN UNIT PIPE x1242 **/
                val x1242 = if (true) {
                  val x1241 = if (TRUE && x1232.nonEmpty) x1232.dequeue() else X(FixedPoint(true,32,0))
                  ()
                }
                /** END UNIT PIPE x1242 **/
                val x1243 = Counter(Number(BigDecimal(0),true,FixedPoint(true,32,0)), Number(BigDecimal(640),true,FixedPoint(true,32,0)), Number(BigDecimal(1),true,FixedPoint(true,32,0)), Number(BigDecimal(16),true,FixedPoint(true,32,0)))
                val x1244 = Array(x1243)
                /** BEGIN UNROLLED FOREACH x1264 **/
                val x1264 = if (b715) {
                  x1244(0).foreach{case (is,vs) => 
                    val b733 = is(0)
                    val b734 = is(1)
                    val b735 = is(2)
                    val b736 = is(3)
                    val b737 = is(4)
                    val b738 = is(5)
                    val b739 = is(6)
                    val b740 = is(7)
                    val b741 = is(8)
                    val b742 = is(9)
                    val b743 = is(10)
                    val b744 = is(11)
                    val b745 = is(12)
                    val b746 = is(13)
                    val b747 = is(14)
                    val b748 = is(15)
                    val b749 = vs(0)
                    val b750 = vs(1)
                    val b751 = vs(2)
                    val b752 = vs(3)
                    val b753 = vs(4)
                    val b754 = vs(5)
                    val b755 = vs(6)
                    val b756 = vs(7)
                    val b757 = vs(8)
                    val b758 = vs(9)
                    val b759 = vs(10)
                    val b760 = vs(11)
                    val b761 = vs(12)
                    val b762 = vs(13)
                    val b763 = vs(14)
                    val b764 = vs(15)
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
                    val x1245 = {
                      val a0 = if (x765 && x1233.nonEmpty) x1233.dequeue() else X(FixedPoint(true,32,0))
                      val a1 = if (x766 && x1233.nonEmpty) x1233.dequeue() else X(FixedPoint(true,32,0))
                      val a2 = if (x767 && x1233.nonEmpty) x1233.dequeue() else X(FixedPoint(true,32,0))
                      val a3 = if (x768 && x1233.nonEmpty) x1233.dequeue() else X(FixedPoint(true,32,0))
                      val a4 = if (x769 && x1233.nonEmpty) x1233.dequeue() else X(FixedPoint(true,32,0))
                      val a5 = if (x770 && x1233.nonEmpty) x1233.dequeue() else X(FixedPoint(true,32,0))
                      val a6 = if (x771 && x1233.nonEmpty) x1233.dequeue() else X(FixedPoint(true,32,0))
                      val a7 = if (x772 && x1233.nonEmpty) x1233.dequeue() else X(FixedPoint(true,32,0))
                      val a8 = if (x773 && x1233.nonEmpty) x1233.dequeue() else X(FixedPoint(true,32,0))
                      val a9 = if (x774 && x1233.nonEmpty) x1233.dequeue() else X(FixedPoint(true,32,0))
                      val a10 = if (x775 && x1233.nonEmpty) x1233.dequeue() else X(FixedPoint(true,32,0))
                      val a11 = if (x776 && x1233.nonEmpty) x1233.dequeue() else X(FixedPoint(true,32,0))
                      val a12 = if (x777 && x1233.nonEmpty) x1233.dequeue() else X(FixedPoint(true,32,0))
                      val a13 = if (x778 && x1233.nonEmpty) x1233.dequeue() else X(FixedPoint(true,32,0))
                      val a14 = if (x779 && x1233.nonEmpty) x1233.dequeue() else X(FixedPoint(true,32,0))
                      val a15 = if (x780 && x1233.nonEmpty) x1233.dequeue() else X(FixedPoint(true,32,0))
                      Array(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15)
                    }
                    val x1246 = x1245.apply(0)
                    val x1247 = x1245.apply(1)
                    val x1248 = x1245.apply(2)
                    val x1249 = x1245.apply(3)
                    val x1250 = x1245.apply(4)
                    val x1251 = x1245.apply(5)
                    val x1252 = x1245.apply(6)
                    val x1253 = x1245.apply(7)
                    val x1254 = x1245.apply(8)
                    val x1255 = x1245.apply(9)
                    val x1256 = x1245.apply(10)
                    val x1257 = x1245.apply(11)
                    val x1258 = x1245.apply(12)
                    val x1259 = x1245.apply(13)
                    val x1260 = x1245.apply(14)
                    val x1261 = x1245.apply(15)
                    val x1262 = Array(x1246,x1247,x1248,x1249,x1250,x1251,x1252,x1253,x1254,x1255,x1256,x1257,x1258,x1259,x1260,x1261)
                    val x1263 = {
                      try {
                        if (x765) x1229.update(b733*1, x1262(0))
                      }
                      catch {case err: java.lang.ArrayIndexOutOfBoundsException => 
                        System.out.println("[warn] DotProduct.scala:36:16 Memory aBlk (x1229): Out of bounds write at address " + "" + s"""${b733.toString}""" + "")
                      }
                      try {
                        if (x766) x1229.update(b734*1, x1262(1))
                      }
                      catch {case err: java.lang.ArrayIndexOutOfBoundsException => 
                        System.out.println("[warn] DotProduct.scala:36:16 Memory aBlk (x1229): Out of bounds write at address " + "" + s"""${b734.toString}""" + "")
                      }
                      try {
                        if (x767) x1229.update(b735*1, x1262(2))
                      }
                      catch {case err: java.lang.ArrayIndexOutOfBoundsException => 
                        System.out.println("[warn] DotProduct.scala:36:16 Memory aBlk (x1229): Out of bounds write at address " + "" + s"""${b735.toString}""" + "")
                      }
                      try {
                        if (x768) x1229.update(b736*1, x1262(3))
                      }
                      catch {case err: java.lang.ArrayIndexOutOfBoundsException => 
                        System.out.println("[warn] DotProduct.scala:36:16 Memory aBlk (x1229): Out of bounds write at address " + "" + s"""${b736.toString}""" + "")
                      }
                      try {
                        if (x769) x1229.update(b737*1, x1262(4))
                      }
                      catch {case err: java.lang.ArrayIndexOutOfBoundsException => 
                        System.out.println("[warn] DotProduct.scala:36:16 Memory aBlk (x1229): Out of bounds write at address " + "" + s"""${b737.toString}""" + "")
                      }
                      try {
                        if (x770) x1229.update(b738*1, x1262(5))
                      }
                      catch {case err: java.lang.ArrayIndexOutOfBoundsException => 
                        System.out.println("[warn] DotProduct.scala:36:16 Memory aBlk (x1229): Out of bounds write at address " + "" + s"""${b738.toString}""" + "")
                      }
                      try {
                        if (x771) x1229.update(b739*1, x1262(6))
                      }
                      catch {case err: java.lang.ArrayIndexOutOfBoundsException => 
                        System.out.println("[warn] DotProduct.scala:36:16 Memory aBlk (x1229): Out of bounds write at address " + "" + s"""${b739.toString}""" + "")
                      }
                      try {
                        if (x772) x1229.update(b740*1, x1262(7))
                      }
                      catch {case err: java.lang.ArrayIndexOutOfBoundsException => 
                        System.out.println("[warn] DotProduct.scala:36:16 Memory aBlk (x1229): Out of bounds write at address " + "" + s"""${b740.toString}""" + "")
                      }
                      try {
                        if (x773) x1229.update(b741*1, x1262(8))
                      }
                      catch {case err: java.lang.ArrayIndexOutOfBoundsException => 
                        System.out.println("[warn] DotProduct.scala:36:16 Memory aBlk (x1229): Out of bounds write at address " + "" + s"""${b741.toString}""" + "")
                      }
                      try {
                        if (x774) x1229.update(b742*1, x1262(9))
                      }
                      catch {case err: java.lang.ArrayIndexOutOfBoundsException => 
                        System.out.println("[warn] DotProduct.scala:36:16 Memory aBlk (x1229): Out of bounds write at address " + "" + s"""${b742.toString}""" + "")
                      }
                      try {
                        if (x775) x1229.update(b743*1, x1262(10))
                      }
                      catch {case err: java.lang.ArrayIndexOutOfBoundsException => 
                        System.out.println("[warn] DotProduct.scala:36:16 Memory aBlk (x1229): Out of bounds write at address " + "" + s"""${b743.toString}""" + "")
                      }
                      try {
                        if (x776) x1229.update(b744*1, x1262(11))
                      }
                      catch {case err: java.lang.ArrayIndexOutOfBoundsException => 
                        System.out.println("[warn] DotProduct.scala:36:16 Memory aBlk (x1229): Out of bounds write at address " + "" + s"""${b744.toString}""" + "")
                      }
                      try {
                        if (x777) x1229.update(b745*1, x1262(12))
                      }
                      catch {case err: java.lang.ArrayIndexOutOfBoundsException => 
                        System.out.println("[warn] DotProduct.scala:36:16 Memory aBlk (x1229): Out of bounds write at address " + "" + s"""${b745.toString}""" + "")
                      }
                      try {
                        if (x778) x1229.update(b746*1, x1262(13))
                      }
                      catch {case err: java.lang.ArrayIndexOutOfBoundsException => 
                        System.out.println("[warn] DotProduct.scala:36:16 Memory aBlk (x1229): Out of bounds write at address " + "" + s"""${b746.toString}""" + "")
                      }
                      try {
                        if (x779) x1229.update(b747*1, x1262(14))
                      }
                      catch {case err: java.lang.ArrayIndexOutOfBoundsException => 
                        System.out.println("[warn] DotProduct.scala:36:16 Memory aBlk (x1229): Out of bounds write at address " + "" + s"""${b747.toString}""" + "")
                      }
                      try {
                        if (x780) x1229.update(b748*1, x1262(15))
                      }
                      catch {case err: java.lang.ArrayIndexOutOfBoundsException => 
                        System.out.println("[warn] DotProduct.scala:36:16 Memory aBlk (x1229): Out of bounds write at address " + "" + s"""${b748.toString}""" + "")
                      }
                    }
                    ()
                  }
                }
                /** END UNROLLED FOREACH x1264 **/
                ()
              }
              /** END UNIT PIPE x1265 **/
              ()
            }
            /** END UNIT PIPE x1266 **/
            /** BEGIN UNIT PIPE x1302 **/
            val x1302 = if (true) {
              val x1267 = new scala.collection.mutable.Queue[Struct1]
              val x1268 = new scala.collection.mutable.Queue[Number] // size: Number(BigDecimal(16),true,FixedPoint(true,32,0))
              val x1269 = new scala.collection.mutable.Queue[Number]
              /** BEGIN UNIT PIPE x1275 **/
              val x1275 = if (true) {
                val x1270 = 0
                val x722 = b714 * Number(BigDecimal(4),true,FixedPoint(true,32,0))
                val x1271 = x722 + x1270
                val x1272: Struct1 = new Struct1( x1271, Number(BigDecimal(2560),true,FixedPoint(true,32,0)), TRUE )
                val x1273 = if (TRUE) x1267.enqueue(x1272)
                val x1274 = if (TRUE) x1268.enqueue(Number(BigDecimal(640),true,FixedPoint(true,32,0)))
                x1274
              }
              /** END UNIT PIPE x1275 **/
              val x1276 = x1267.foreach{cmd => 
                for (i <- cmd.offset until cmd.offset+cmd.size by 4) {
                  val data = {
                    try {
                      x1221.apply(i / 4)
                    }
                    catch {case err: java.lang.ArrayIndexOutOfBoundsException => 
                      System.out.println("[warn] DotProduct.scala:37:16 Memory b (x1221): Out of bounds read at address " + err.getMessage)
                      X(FixedPoint(true,32,0))
                    }
                  }
                  x1269.enqueue(data)
                }
              }
              x1267.clear()
              /** BEGIN UNIT PIPE x1301 **/
              val x1301 = if (true) {
                /** BEGIN UNIT PIPE x1278 **/
                val x1278 = if (true) {
                  val x1277 = if (TRUE && x1268.nonEmpty) x1268.dequeue() else X(FixedPoint(true,32,0))
                  ()
                }
                /** END UNIT PIPE x1278 **/
                val x1279 = Counter(Number(BigDecimal(0),true,FixedPoint(true,32,0)), Number(BigDecimal(640),true,FixedPoint(true,32,0)), Number(BigDecimal(1),true,FixedPoint(true,32,0)), Number(BigDecimal(16),true,FixedPoint(true,32,0)))
                val x1280 = Array(x1279)
                /** BEGIN UNROLLED FOREACH x1300 **/
                val x1300 = if (b715) {
                  x1280(0).foreach{case (is,vs) => 
                    val b817 = is(0)
                    val b818 = is(1)
                    val b819 = is(2)
                    val b820 = is(3)
                    val b821 = is(4)
                    val b822 = is(5)
                    val b823 = is(6)
                    val b824 = is(7)
                    val b825 = is(8)
                    val b826 = is(9)
                    val b827 = is(10)
                    val b828 = is(11)
                    val b829 = is(12)
                    val b830 = is(13)
                    val b831 = is(14)
                    val b832 = is(15)
                    val b833 = vs(0)
                    val b834 = vs(1)
                    val b835 = vs(2)
                    val b836 = vs(3)
                    val b837 = vs(4)
                    val b838 = vs(5)
                    val b839 = vs(6)
                    val b840 = vs(7)
                    val b841 = vs(8)
                    val b842 = vs(9)
                    val b843 = vs(10)
                    val b844 = vs(11)
                    val b845 = vs(12)
                    val b846 = vs(13)
                    val b847 = vs(14)
                    val b848 = vs(15)
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
                    val x1281 = {
                      val a0 = if (x849 && x1269.nonEmpty) x1269.dequeue() else X(FixedPoint(true,32,0))
                      val a1 = if (x850 && x1269.nonEmpty) x1269.dequeue() else X(FixedPoint(true,32,0))
                      val a2 = if (x851 && x1269.nonEmpty) x1269.dequeue() else X(FixedPoint(true,32,0))
                      val a3 = if (x852 && x1269.nonEmpty) x1269.dequeue() else X(FixedPoint(true,32,0))
                      val a4 = if (x853 && x1269.nonEmpty) x1269.dequeue() else X(FixedPoint(true,32,0))
                      val a5 = if (x854 && x1269.nonEmpty) x1269.dequeue() else X(FixedPoint(true,32,0))
                      val a6 = if (x855 && x1269.nonEmpty) x1269.dequeue() else X(FixedPoint(true,32,0))
                      val a7 = if (x856 && x1269.nonEmpty) x1269.dequeue() else X(FixedPoint(true,32,0))
                      val a8 = if (x857 && x1269.nonEmpty) x1269.dequeue() else X(FixedPoint(true,32,0))
                      val a9 = if (x858 && x1269.nonEmpty) x1269.dequeue() else X(FixedPoint(true,32,0))
                      val a10 = if (x859 && x1269.nonEmpty) x1269.dequeue() else X(FixedPoint(true,32,0))
                      val a11 = if (x860 && x1269.nonEmpty) x1269.dequeue() else X(FixedPoint(true,32,0))
                      val a12 = if (x861 && x1269.nonEmpty) x1269.dequeue() else X(FixedPoint(true,32,0))
                      val a13 = if (x862 && x1269.nonEmpty) x1269.dequeue() else X(FixedPoint(true,32,0))
                      val a14 = if (x863 && x1269.nonEmpty) x1269.dequeue() else X(FixedPoint(true,32,0))
                      val a15 = if (x864 && x1269.nonEmpty) x1269.dequeue() else X(FixedPoint(true,32,0))
                      Array(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15)
                    }
                    val x1282 = x1281.apply(0)
                    val x1283 = x1281.apply(1)
                    val x1284 = x1281.apply(2)
                    val x1285 = x1281.apply(3)
                    val x1286 = x1281.apply(4)
                    val x1287 = x1281.apply(5)
                    val x1288 = x1281.apply(6)
                    val x1289 = x1281.apply(7)
                    val x1290 = x1281.apply(8)
                    val x1291 = x1281.apply(9)
                    val x1292 = x1281.apply(10)
                    val x1293 = x1281.apply(11)
                    val x1294 = x1281.apply(12)
                    val x1295 = x1281.apply(13)
                    val x1296 = x1281.apply(14)
                    val x1297 = x1281.apply(15)
                    val x1298 = Array(x1282,x1283,x1284,x1285,x1286,x1287,x1288,x1289,x1290,x1291,x1292,x1293,x1294,x1295,x1296,x1297)
                    val x1299 = {
                      try {
                        if (x849) x1230.update(b817*1, x1298(0))
                      }
                      catch {case err: java.lang.ArrayIndexOutOfBoundsException => 
                        System.out.println("[warn] DotProduct.scala:37:16 Memory bBlk (x1230): Out of bounds write at address " + "" + s"""${b817.toString}""" + "")
                      }
                      try {
                        if (x850) x1230.update(b818*1, x1298(1))
                      }
                      catch {case err: java.lang.ArrayIndexOutOfBoundsException => 
                        System.out.println("[warn] DotProduct.scala:37:16 Memory bBlk (x1230): Out of bounds write at address " + "" + s"""${b818.toString}""" + "")
                      }
                      try {
                        if (x851) x1230.update(b819*1, x1298(2))
                      }
                      catch {case err: java.lang.ArrayIndexOutOfBoundsException => 
                        System.out.println("[warn] DotProduct.scala:37:16 Memory bBlk (x1230): Out of bounds write at address " + "" + s"""${b819.toString}""" + "")
                      }
                      try {
                        if (x852) x1230.update(b820*1, x1298(3))
                      }
                      catch {case err: java.lang.ArrayIndexOutOfBoundsException => 
                        System.out.println("[warn] DotProduct.scala:37:16 Memory bBlk (x1230): Out of bounds write at address " + "" + s"""${b820.toString}""" + "")
                      }
                      try {
                        if (x853) x1230.update(b821*1, x1298(4))
                      }
                      catch {case err: java.lang.ArrayIndexOutOfBoundsException => 
                        System.out.println("[warn] DotProduct.scala:37:16 Memory bBlk (x1230): Out of bounds write at address " + "" + s"""${b821.toString}""" + "")
                      }
                      try {
                        if (x854) x1230.update(b822*1, x1298(5))
                      }
                      catch {case err: java.lang.ArrayIndexOutOfBoundsException => 
                        System.out.println("[warn] DotProduct.scala:37:16 Memory bBlk (x1230): Out of bounds write at address " + "" + s"""${b822.toString}""" + "")
                      }
                      try {
                        if (x855) x1230.update(b823*1, x1298(6))
                      }
                      catch {case err: java.lang.ArrayIndexOutOfBoundsException => 
                        System.out.println("[warn] DotProduct.scala:37:16 Memory bBlk (x1230): Out of bounds write at address " + "" + s"""${b823.toString}""" + "")
                      }
                      try {
                        if (x856) x1230.update(b824*1, x1298(7))
                      }
                      catch {case err: java.lang.ArrayIndexOutOfBoundsException => 
                        System.out.println("[warn] DotProduct.scala:37:16 Memory bBlk (x1230): Out of bounds write at address " + "" + s"""${b824.toString}""" + "")
                      }
                      try {
                        if (x857) x1230.update(b825*1, x1298(8))
                      }
                      catch {case err: java.lang.ArrayIndexOutOfBoundsException => 
                        System.out.println("[warn] DotProduct.scala:37:16 Memory bBlk (x1230): Out of bounds write at address " + "" + s"""${b825.toString}""" + "")
                      }
                      try {
                        if (x858) x1230.update(b826*1, x1298(9))
                      }
                      catch {case err: java.lang.ArrayIndexOutOfBoundsException => 
                        System.out.println("[warn] DotProduct.scala:37:16 Memory bBlk (x1230): Out of bounds write at address " + "" + s"""${b826.toString}""" + "")
                      }
                      try {
                        if (x859) x1230.update(b827*1, x1298(10))
                      }
                      catch {case err: java.lang.ArrayIndexOutOfBoundsException => 
                        System.out.println("[warn] DotProduct.scala:37:16 Memory bBlk (x1230): Out of bounds write at address " + "" + s"""${b827.toString}""" + "")
                      }
                      try {
                        if (x860) x1230.update(b828*1, x1298(11))
                      }
                      catch {case err: java.lang.ArrayIndexOutOfBoundsException => 
                        System.out.println("[warn] DotProduct.scala:37:16 Memory bBlk (x1230): Out of bounds write at address " + "" + s"""${b828.toString}""" + "")
                      }
                      try {
                        if (x861) x1230.update(b829*1, x1298(12))
                      }
                      catch {case err: java.lang.ArrayIndexOutOfBoundsException => 
                        System.out.println("[warn] DotProduct.scala:37:16 Memory bBlk (x1230): Out of bounds write at address " + "" + s"""${b829.toString}""" + "")
                      }
                      try {
                        if (x862) x1230.update(b830*1, x1298(13))
                      }
                      catch {case err: java.lang.ArrayIndexOutOfBoundsException => 
                        System.out.println("[warn] DotProduct.scala:37:16 Memory bBlk (x1230): Out of bounds write at address " + "" + s"""${b830.toString}""" + "")
                      }
                      try {
                        if (x863) x1230.update(b831*1, x1298(14))
                      }
                      catch {case err: java.lang.ArrayIndexOutOfBoundsException => 
                        System.out.println("[warn] DotProduct.scala:37:16 Memory bBlk (x1230): Out of bounds write at address " + "" + s"""${b831.toString}""" + "")
                      }
                      try {
                        if (x864) x1230.update(b832*1, x1298(15))
                      }
                      catch {case err: java.lang.ArrayIndexOutOfBoundsException => 
                        System.out.println("[warn] DotProduct.scala:37:16 Memory bBlk (x1230): Out of bounds write at address " + "" + s"""${b832.toString}""" + "")
                      }
                    }
                    ()
                  }
                }
                /** END UNROLLED FOREACH x1300 **/
                ()
              }
              /** END UNIT PIPE x1301 **/
              ()
            }
            /** END UNIT PIPE x1302 **/
            ()
          }
          /** END PARALLEL PIPE x1303 **/
          val x1304 = Array[Number](Number(BigDecimal(0),true,FixedPoint(true,32,0)))
          val x1305 = Counter(Number(BigDecimal(0),true,FixedPoint(true,32,0)), Number(BigDecimal(640),true,FixedPoint(true,32,0)), Number(BigDecimal(1),true,FixedPoint(true,32,0)), Number(BigDecimal(1),true,FixedPoint(true,32,0)))
          val x1306 = Array(x1305)
          /** BEGIN UNROLLED REDUCE x1316 **/
          val x1316 = if (b715) {
            x1306(0).foreach{case (is,vs) => 
              val b891 = is(0)
              val b892 = vs(0)
              val x893 = b892 && b715
              val x1307 = {
                val a0 = {
                  try {
                    if (x893) x1229.apply(b891*1) else X(FixedPoint(true,32,0))
                  }
                  catch {case err: java.lang.ArrayIndexOutOfBoundsException => 
                    System.out.println("[warn] DotProduct.scala:39:53 Memory aBlk (x1229): Out of bounds read at address " + "" + s"""${b891.toString}""" + "")
                    X(FixedPoint(true,32,0))
                  }
                }
                Array(a0)
              }
              val x1308 = {
                val a0 = {
                  try {
                    if (x893) x1230.apply(b891*1) else X(FixedPoint(true,32,0))
                  }
                  catch {case err: java.lang.ArrayIndexOutOfBoundsException => 
                    System.out.println("[warn] DotProduct.scala:39:64 Memory bBlk (x1230): Out of bounds read at address " + "" + s"""${b891.toString}""" + "")
                    X(FixedPoint(true,32,0))
                  }
                }
                Array(a0)
              }
              val x1309 = x1304.apply(0)
              val x900 = b891 === Number(BigDecimal(0),true,FixedPoint(true,32,0))
              val x1310 = x1307.apply(0)
              val x1311 = x1308.apply(0)
              val x1312 = x1310 * x1311
              val x1313 = x1312 + x1309
              val x1314 = if (x900) x1312 else x1313
              val x1315 = if (TRUE) x1304.update(0, x1314)
              ()
            }
          }
          /** END UNROLLED REDUCE x1316 **/
          /** BEGIN UNIT PIPE x1322 **/
          val x1322 = if (true) {
            val x1317 = x1225.apply(0)
            val x1318 = x1304.apply(0)
            val x910 = b714 === Number(BigDecimal(0),true,FixedPoint(true,32,0))
            val x1319 = x1318 + x1317
            val x1320 = if (x910) x1318 else x1319
            val x1321 = if (TRUE) x1225.update(0, x1320)
            x1321
          }
          /** END UNIT PIPE x1322 **/
          ()
        }
      }
      /** END UNROLLED REDUCE x1323 **/
      /** BEGIN UNIT PIPE x1326 **/
      val x1326 = if (true) {
        val x1324 = x1225.apply(0)
        val x1325 = if (TRUE) x1222.update(0, x1324)
        ()
      }
      /** END UNIT PIPE x1326 **/
      ()
    }
    /** END HARDWARE BLOCK x1327 **/
    val x1328 = x1222.apply(0)
    val x1332 = Array.tabulate(x1212.length){bbb => 
      val b58 = Number(bbb)
      val x1329 = x1212.apply(b58)
      val x1330 = x1214.apply(b58)
      val x1331 = x1329 * x1330
      x1331
    }
    val x1334 = x1332.reduce{(b64,b65) => 
      val x67 = b64 + b65
      x67
    }
    val x1335 = x1334.toString
    val x1336 = "expected: " + x1335
    val x1337 = if (TRUE) System.out.println(x1336)
    val x1338 = x1328.toString
    val x1339 = "result: " + x1338
    val x1340 = if (TRUE) System.out.println(x1339)
    val x1341 = x1334 === x1328
    val x1342 = x1341.toString
    val x1343 = "PASS: " + x1342
    val x1344 = x1343 + " (DotProduct)"
    val x1345 = if (TRUE) System.out.println(x1344)
    ()
  }
}
