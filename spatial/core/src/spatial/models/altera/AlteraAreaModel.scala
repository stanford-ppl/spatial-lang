package spatial.models
package altera

import argon.core._
import forge._
import spatial.aliases._

abstract class AlteraAreaModel extends AreaModel {
  import spatial.targets.AlteraDevice._
  def calculateRoutingLUTs(design: Area): Double
  def calculateRoutingRegs(design: Area): Double
  def calculateUnavailALMs(design: Area): Double

  @stateful override def summarize(area: Area): Area = {
    val design = area + model("Fringe")()

    val routingLUTs = calculateRoutingLUTs(design)
    val fanoutRegs  = calculateRoutingRegs(design)
    val unavailable = calculateUnavailALMs(design)

    val recoverable = design(LUT3)/2 + design(LUT4)/2 + design(LUT5)/2 + design(LUT6)/10 + design(MEM16)/2  + routingLUTs/2

    val logicALMs = design(LUT3) + design(LUT4) + design(LUT5) + design(LUT6) + design(LUT7) +
      design(MEM16) + design(MEM32) + design(MEM64) + routingLUTs - recoverable + unavailable

    val totalRegs = design(Regs) + fanoutRegs + design(Mregs)

    val regALMs = Math.max( ((totalRegs - (logicALMs*2.16))/3).toInt, 0)

    val totalALMs = logicALMs + regALMs

    val dupBRAMs = Math.max(0.02*routingLUTs - 500, 0.0).toInt

    val totalDSPs = design(DSPs)
    val totalBRAM = design(BRAM) + dupBRAMs

    val capacity = spatialConfig.target.capacity

    if (config.verbosity > 0) {
      val areaReport = s"""
                          |Resource Estimate Breakdown:
                          |----------------------------
                          |LUTs
                          |  LUT3: ${design(LUT3)}
                          |  LUT4: ${design(LUT4)}
                          |  LUT5: ${design(LUT5)}
                          |  LUT6: ${design(LUT6)}
                          |  LUT7: ${design(LUT7)}
                          |  Estimated Routing LUTs: $routingLUTs
                          |
                          |ALMs
                          |  Logic + Register ALMS: $logicALMs
                          |  Register-only ALMs:    $regALMs
                          |  Recovered ALMs:        $recoverable
                          |  Unavailable ALMs:      $unavailable
                          |
                          |MEMs
                          |  MEM16: ${design(MEM16)}
                          |  MEM32: ${design(MEM32)}
                          |  MEM64: ${design(MEM64)}
                          |
                          |Registers
                          |  Design: ${design(Regs)}
                          |  Memory: ${design(Mregs)}
                          |  Fanout: $fanoutRegs
                          |
                          |BRAMs
                          |  Design: ${design(BRAM)}
                          |  Fanout: $dupBRAMs
                          |
                          |Resource Estimate Summary
                          |-------------------------
                          |ALMs: $totalALMs / ${capacity(ALMs)} (${"%.2f".format(100*totalALMs.toDouble/capacity(ALMs))}%)
                          |Regs: $totalRegs
                          |DSPs: $totalDSPs / ${capacity(DSPs)} (${"%.2f".format(100*totalDSPs.toDouble/capacity(DSPs))}%)
                          |BRAM: $totalBRAM / ${capacity(BRAM)} (${"%.2f".format(100*totalBRAM.toDouble/capacity(BRAM))}%)
                          |
         """.stripMargin

      report(areaReport)
    }

    AreaMap(
      ALMs -> totalALMs,
      Regs -> totalRegs,
      DSPs -> totalDSPs,
      BRAM -> totalBRAM,
      Channels -> design(Channels)
    )
  }
}
