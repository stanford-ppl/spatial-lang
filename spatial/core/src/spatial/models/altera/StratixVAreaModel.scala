package spatial.models.altera

import argon.core._
import argon.nodes._
import forge._
import spatial.SpatialConfig
import spatial.aliases._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._

class StratixVAreaModel extends AlteraAreaModel {
  private val areaFile = "StratixV-Routing.csv"
  private val baseDesign = AlteraArea(
    lut3 = 22600,
    lut4 = 11140,
    lut5 = 12350,
    lut6 = 9200,
    lut7 = 468,
    mem16 = 559,
    mem32 = 519,
    mem64 = 4619,
    regs = 75400,
    sram = 340
  )

  override val MAX_PORT_WIDTH: Int = 40
  override def bramWordDepth(width: Int): Int = {
    if      (width == 1) 16384
    else if (width == 2) 8192
    else if (width <= 5) 4096
    else if (width <= 10) 2048
    else if (width <= 20) 1024
    else 512
  }

  import AreaNeuralModel._
  class LUTRoutingModel extends AreaNeuralModel(name="RoutingLUTs", filename=areaFile, OUT=RLUTS, LAYER2=6)
  class RegFanoutModel extends AreaNeuralModel(name="FanoutRegisters", filename=areaFile, OUT=FREGS, LAYER2=6)
  class UnavailALMsModel extends AreaNeuralModel(name="UnavailableALMs", filename=areaFile, OUT=UNVAIL, LAYER2=6)

  lazy val lutModel = new LUTRoutingModel
  lazy val regModel = new RegFanoutModel
  lazy val almModel = new UnavailALMsModel

  def init(): Unit = {
    lutModel.init()
    regModel.init()
    almModel.init()
  }

  @stateful override def areaOfNode(e: Exp[_], d: Def): AlteraArea = d match {
    case FltAdd(_,_) if e.tp == FloatType => AlteraArea(lut3=397,lut4=29,lut5=125,lut6=34,lut7=5,regs=606,mem16=50) // ~372 ALMs, 1 DSP (around 564)
    case FltSub(_,_) if e.tp == FloatType => AlteraArea(lut3=397,lut4=29,lut5=125,lut6=34,lut7=5,regs=606,mem16=50)
    case FltMul(_,_) if e.tp == FloatType => AlteraArea(lut3=152,lut4=10,lut5=21,lut6=2,dsps=1,regs=335,mem16=43)   // ~76 ALMs, 1 DSP (around 1967)
    case FltDiv(_,_) if e.tp == FloatType => AlteraArea(lut3=2384,lut4=448,lut5=149,lut6=385,lut7=1,regs=3048,mem32=25,mem16=9)
    case FltLt(a,_)  if a.tp == FloatType => AlteraArea(lut4=42,lut6=26,regs=33)
    case FltLeq(a,_) if a.tp == FloatType => AlteraArea(lut4=42,lut6=26,regs=33)
    case FltNeq(a,_) if a.tp == FloatType => AlteraArea(lut4=42,lut6=26,regs=33)
    case FltEql(a,_) if a.tp == FloatType => AlteraArea(lut4=42,lut6=26,regs=33)
    case FltExp(_)   if e.tp == FloatType => AlteraArea(lut3=368,lut4=102,lut5=137,lut6=38,mem16=24,regs=670,dsps=5,sram=2)
    case FltSqrt(_)  if e.tp == FloatType => AlteraArea(lut3=476,lut4=6,lut5=6,mem32=11,regs=900)
    case FixPtToFltPt(_) if e.tp == FloatType => AlteraArea(lut4=50,lut6=132,regs=238)
    case FltPtToFixPt(x) if x.tp == FloatType => AlteraArea(lut4=160,lut6=96,regs=223+nbits(e))

    case x: DenseTransfer[_,_] if x.isLoad  => AlteraArea(lut3=410,lut4=50,lut5=70,lut6=53,          regs=920,  channels=1) // ~353 ALMs
    case x: DenseTransfer[_,_] if x.isStore => AlteraArea(lut3=893,lut4=91,lut5=96,lut6=618,lut7=10, regs=4692, channels=1) // ~1206 ALMs

    case _ => super.areaOfNode(e,d)
  }

  @stateful def summarize(area: AlteraArea): (AlteraAreaSummary, String) = {
    val design = area + baseDesign

    val routingLUTs = lutModel.evaluate(design)
    val fanoutRegs  = regModel.evaluate(design)
    val unavailable = almModel.evaluate(design)

    val recoverable = design.lut3/2 + design.lut4/2 + design.lut5/2 + design.lut6/10 + design.mem16/2  + routingLUTs/2

    val logicALMs = design.lut3 + design.lut4 + design.lut5 + design.lut6 + design.lut7 +
      design.mem16 + design.mem32 + design.mem64 + routingLUTs - recoverable + unavailable

    val totalRegs = design.regs + fanoutRegs + design.mregs

    val regALMs = Math.max( ((totalRegs - (logicALMs*2.16))/3).toInt, 0)

    val totalALMs = logicALMs + regALMs

    val dupBRAMs = Math.max(0.02*routingLUTs - 500, 0.0).toInt

    val totalDSPs = design.dsps
    val totalBRAM = design.sram + dupBRAMs

    val capacity = SpatialConfig.target.capacity.asInstanceOf[AlteraAreaSummary]


    if (Config.verbosity > 0) {
      val areaReport = s"""
                          |Resource Estimate Breakdown:
                          |----------------------------
                          |LUTs
                          |  LUT3: ${design.lut3}
                          |  LUT4: ${design.lut4}
                          |  LUT5: ${design.lut5}
                          |  LUT6: ${design.lut6}
                          |  LUT7: ${design.lut7}
                          |  Estimated Routing LUTs: $routingLUTs
                          |
                          |ALMs
                          |  Logic + Register ALMS: $logicALMs
                          |  Register-only ALMs:    $regALMs
                          |  Recovered ALMs:        $recoverable
                          |  Unavailable ALMs:      $unavailable
                          |
                          |MEMs
                          |  MEM16: ${design.mem16}
                          |  MEM32: ${design.mem32}
                          |  MEM64: ${design.mem64}
                          |
                          |Registers
                          |  Design: ${design.regs}
                          |  Memory: ${design.mregs}
                          |  Fanout: $fanoutRegs
                          |
                          |BRAMs
                          |  Design: ${design.sram}
                          |  Fanout: $dupBRAMs
                          |
                          |Resource Estimate Summary
                          |-------------------------
                          |ALMs: $totalALMs / ${capacity.alms} (${"%.2f".format(100*totalALMs.toDouble/capacity.alms)}%)
                          |Regs: $totalRegs
                          |DSPs: $totalDSPs / ${capacity.dsps} (${"%.2f".format(100*totalDSPs.toDouble/capacity.dsps)}%)
                          |BRAM: $totalBRAM / ${capacity.bram} (${"%.2f".format(100*totalBRAM.toDouble/capacity.bram)}%)
                          |
         """.stripMargin

      report(areaReport)
    }

    val summ = AlteraAreaSummary(
      alms = totalALMs,
      regs = totalRegs,
      dsps = totalDSPs,
      bram = totalBRAM,
      channels = design.channels
    )
    (summ, "")
  }

}


