import DataImplicits._
object Main {
  def main(args: Array[String]): Unit = {
    val x150 = Array[Number](Number(BigDecimal(0),true,FixedPoint(true,32,0)))
    val x151 = Array[Number](Number(BigDecimal(0),true,FixedPoint(true,32,0)))
    val x2 = args
    val x3 = x2.apply(Number(BigDecimal(0),true,FixedPoint(true,32,0)))
    val x4 = x3.toInt
    val x152 = x150.update(0, x4)
    /** BEGIN HARDWARE BLOCK x156 **/
    val x156 = {
      val x153 = x150.apply(0)
      val x154 = x153 + Number(BigDecimal(4),true,FixedPoint(true,32,0))
      val x155 = if (TRUE) x151.update(0, x154)
      x155
    }
    /** END HARDWARE BLOCK x156 **/
    val x157 = x151.apply(0)
    val x11 = x4 + Number(BigDecimal(4),true,FixedPoint(true,32,0))
    val x12 = x11.toString
    val x13 = "expected: " + x12
    val x158 = if (TRUE) System.out.println(x13)
    val x159 = x157.toString
    val x160 = "result: " + x159
    val x161 = if (TRUE) System.out.println(x160)
    ()
  }
}
