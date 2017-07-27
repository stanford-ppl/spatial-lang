package spatial.targets

import spatial.models._
import spatial.models.altera._

object DefaultTarget extends FPGATarget {
  def name = "Default"
  val burstSize = 512 // in bits. TODO: This should actually be selectable

  override type Area = AlteraArea
  override type Sum  = AlteraAreaSummary
  def areaMetric: AreaMetric[Area] = AlteraAreaMetric
  def areaModel: AreaModel[Area,Sum] = new StratixVAreaModel
  def latencyModel: LatencyModel = new StratixVLatencyModel
  def capacity: AlteraAreaSummary = AlteraAreaSummary(alms=262400, regs=524800, dsps=1963, bram=2567, channels=13)
}

