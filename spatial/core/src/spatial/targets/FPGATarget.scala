package spatial.targets

import argon.core.State
import spatial.analysis.{AreaAnalyzer, LatencyAnalyzer}
import spatial.models.{AreaMetric, AreaModel, AreaSummary, LatencyModel}

case class Pin(name: String) {
  override def toString: String = name
}

abstract class Bus {
  def length: Int
}

case class PinBus(valid: Pin, data: Seq[Pin]) extends Bus {
  override def toString: String = "Bus(" + valid.toString + ": " + data.mkString(", ") + ")"
  def length: Int = data.length
}

object Bus {
  def apply(valid: Pin, data: Pin*) = PinBus(valid, data)
  def apply(valid: String, data: String*) = PinBus(Pin(valid), data.map(Pin(_)))
}

// TODO: Hack - remove later
case object GPInput extends Bus { val length = 32 }
case object GPOutput extends Bus { val length = 32 }

abstract class FPGATarget {
  def name: String    // FPGA name
  def burstSize: Int  // Size of DRAM burst (in bits)

  type Area
  type Sum <: AreaSummary[Sum]
  def areaMetric: AreaMetric[Area]
  lazy val areaModel: AreaModel[Area,Sum] = null
  lazy val latencyModel: LatencyModel = null
  def areaAnalyzer(state: State): AreaAnalyzer[Area,Sum] = {
    AreaAnalyzer[Area,Sum](state, areaModel, latencyModel)(areaMetric)
  }
  def cycleAnalyzer(state: State): LatencyAnalyzer = LatencyAnalyzer(state, latencyModel)
  def capacity: Sum
}

object Targets {
  var targets: Set[FPGATarget] = Set.empty
  targets += DefaultTarget

  lazy val Default = spatial.targets.DefaultTarget
  lazy val DE1 = spatial.targets.DE1
  lazy val F1 = spatial.targets.AWS_F1
}