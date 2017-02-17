package spatial.targets

abstract class FPGATarget {
  def name: String    // FPGA name
  def burstSize: Int  // Size of DRAM burst (in bits)
}

case class Pin(name: String)
case class Bus(pins: Seq[Pin])

object Targets {
  var targets: Set[FPGATarget] = Set.empty
  targets += DefaultTarget
  targets += FakeTarget
}