package spatial.models

case class AlteraArea(
                      lut3: Int = 0,
                      lut4: Int = 0,
                      lut5: Int = 0,
                      lut6: Int = 0,
                      lut7: Int = 0,
                      regs: Int = 0,
                      dsps: Int = 0,
                      sram: Int = 0
                     )
{
  def *(x: Int) = AlteraArea(lut3=lut3*x,lut4=lut4*x,lut5=lut5*x,lut6=lut6*x,lut7=lut7*x,regs=regs*x,dsps=dsps*x,sram=sram*x)
  def +(that: AlteraArea) = AlteraArea(
    lut3 = this.lut3 + that.lut3,
    lut4 = this.lut4 + that.lut4,
    lut5 = this.lut5 + that.lut5,
    lut6 = this.lut6 + that.lut6,
    lut7 = this.lut7 + that.lut7,
    regs = this.regs + that.regs,
    dsps = this.dsps + that.dsps,
    sram = this.sram + that.sram
  )
  def <(that: AlteraArea) = {
    this.lut3 < that.lut3 &&
    this.lut4 < that.lut4 &&
    this.lut5 < that.lut5 &&
    this.lut6 < that.lut6 &&
    this.lut7 < that.lut7 &&
    this.regs < that.regs &&
    this.dsps < that.dsps &&
    this.sram < that.sram
  }
}

object AlteraAreaMetric extends AreaMetric[AlteraArea] {
  override def zero: AlteraArea = AlteraArea()
}

object AlteraArea {
  implicit def alteraAreaIsAreaMetric: AreaMetric[AlteraArea] = AlteraAreaMetric
}
