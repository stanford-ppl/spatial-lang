package spatial.models
package xilinx

case class XilinxArea(
                       lut3: Int = 0,
                       lut4: Int = 0,
                       lut5: Int = 0,
                       lut6: Int = 0,
                       lut7: Int = 0,
                       mem16: Int = 0,
                       mem32: Int = 0,
                       mem64: Int = 0,
                       regs: Int = 0,
                       mregs: Int = 0,   // Memory registers (used in place of SRAMs)
                       dsps: Int = 0,
                       sram: Int = 0,
                       channels: Int = 0
                     )
{
  def +(that: XilinxArea) = XilinxArea(
    lut3 = this.lut3 + that.lut3,
    lut4 = this.lut4 + that.lut4,
    lut5 = this.lut5 + that.lut5,
    lut6 = this.lut6 + that.lut6,
    lut7 = this.lut7 + that.lut7,
    mem16 = this.mem16 + that.mem16,
    mem32 = this.mem32 + that.mem32,
    mem64 = this.mem64 + that.mem64,
    regs = this.regs + that.regs,
    mregs = this.mregs + that.mregs,
    dsps = this.dsps + that.dsps,
    sram = this.sram + that.sram,
    channels = this.channels + that.channels
  )

  def replicate(x: Int, inner: Boolean) = XilinxArea(
    lut3 = this.lut3 * x,
    lut4 = this.lut4 * x,
    lut5 = this.lut5 * x,
    lut6 = this.lut6 * x,
    lut7 = this.lut7 * x,
    mem16 = this.mem16 * x,
    mem32 = this.mem32 * x,
    mem64 = this.mem64 * x,
    regs  = this.regs * x,
    mregs = if (inner) mregs else mregs * x,
    dsps = this.dsps * x,
    sram = if (inner) sram else sram * x,
    channels = channels * x
  )
  def isNonzero: Boolean = lut7 > 0 || lut6 > 0 || lut5 > 0 || lut4 > 0 || lut3 > 0 ||
    mem64 > 0 || mem32 > 0 || mem16 > 0 || regs > 0 || dsps > 0 || sram > 0 || mregs > 0 || channels > 0

  override def toString: String = s"lut3=$lut3, lut4=$lut4, lut5=$lut5, lut6=$lut6, lut7=$lut7, mem16=$mem16, mem32=$mem32, mem64=$mem64, regs=$regs, dsps=$dsps, bram=$sram, mregs=$mregs"

  def toArray: Array[Int] = Array(lut3,lut4,lut5,lut6,lut7,mem16,mem32,mem64,regs+mregs,dsps,sram)

  def <(that: XilinxArea): Boolean = {
    this.lut3 < that.lut3 &&
      this.lut4 < that.lut4 &&
      this.lut5 < that.lut5 &&
      this.lut6 < that.lut6 &&
      this.lut7 < that.lut7 &&
      this.mem16 < that.mem16 &&
      this.mem32 < that.mem32 &&
      this.mem64 < that.mem64 &&
      this.regs < that.regs &&
      this.mregs < that.mregs &&
      this.dsps < that.dsps &&
      this.sram < that.sram &&
      this.channels < that.channels
  }
}

case class XilinxAreaSummary(
  clbs: Double,
  regs: Double,
  dsps: Double,
  bram: Double,
  channels: Double
) extends AreaSummary[XilinxAreaSummary] {
  override def headings: List[String] = List("CLBs", "DSPs", "BRAMs")
  override def toFile: List[Double] = List(clbs, dsps, bram)
  override def toList: List[Double] = List(clbs, regs, dsps, bram, channels)
  override def fromList(items: List[Double]): XilinxAreaSummary = XilinxAreaSummary(items(0),items(1),items(2),items(3),items(4))
}

object XilinxAreaMetric extends AreaMetric[XilinxArea] {
  override def plus(a: XilinxArea, b: XilinxArea): XilinxArea = a + b
  override def zero: XilinxArea = XilinxArea()
  override def lessThan(x: XilinxArea, y: XilinxArea): Boolean = x < y
  override def times(a: XilinxArea, x: Int, isInner: Boolean): XilinxArea = a.replicate(x, isInner)

  override def sramArea(n: Int): XilinxArea = XilinxArea(sram=n)
  override def regArea(n: Int, bits: Int): XilinxArea = XilinxArea(regs=n*bits)
  override def muxArea(n: Int, bits: Int): XilinxArea = XilinxArea(lut3=n*bits, regs=n*bits)
}

object XilinxArea {
  implicit def alteraAreaIsAreaMetric: AreaMetric[XilinxArea] = XilinxAreaMetric
}
