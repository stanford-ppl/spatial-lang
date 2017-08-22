package spatial.targets
import argon.core.State
import spatial.analysis.AreaAnalyzer
import spatial.models.altera._
import spatial.models.{AreaMetric, AreaModel, LatencyModel}

object Ethernet extends FPGATarget {
  val name = "Ethernet"
  
  // FIXME: Not sure what size this should be
  def burstSize = 96 


  case object EthInput extends Bus { def length = 32 }
  case object EthOutput extends Bus { def length = 32 }

  // FIXME: No models for Ethernet yet
  override type Area = AlteraArea
  override type Sum = AlteraAreaSummary
  override def areaMetric: AreaMetric[AlteraArea] = AlteraAreaMetric
  def areaModel: AreaModel[Area,Sum] = new StratixVAreaModel
  def latencyModel: LatencyModel = new StratixVLatencyModel
  override def capacity: AlteraAreaSummary = AlteraAreaSummary(alms=262400,regs=524800,dsps=1963,bram=2567,channels=13)
}
