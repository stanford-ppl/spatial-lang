package spatial.models

case class AlteraArea(
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
                      sram: Int = 0
                     )
{
  def +(that: AlteraArea) = AlteraArea(
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
    sram = this.sram + that.sram
  )

  def replicate(x: Int, inner: Boolean) = AlteraArea(
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
    sram = if (inner) sram else sram * x
  )
  def isNonzero: Boolean = lut7 > 0 || lut6 > 0 || lut5 > 0 || lut4 > 0 || lut3 > 0 ||
    mem64 > 0 || mem32 > 0 || mem16 > 0 || regs > 0 || dsps > 0 || sram > 0 || mregs > 0

  override def toString() = s"lut3=$lut3, lut4=$lut4, lut5=$lut5, lut6=$lut6, lut7=$lut7, mem16=$mem16, mem32=$mem32, mem64=$mem64, regs=$regs, dsps=$dsps, bram=$sram, mregs=$mregs"

  def toArray: Array[Int] = Array(lut3,lut4,lut5,lut6,lut7,mem16,mem32,mem64,regs+mregs,dsps,sram)
}

object AlteraAreaMetric extends AreaMetric[AlteraArea] {
  override def zero: AlteraArea = AlteraArea()
}

object AlteraArea {
  implicit def alteraAreaIsAreaMetric: AreaMetric[AlteraArea] = AlteraAreaMetric
}
