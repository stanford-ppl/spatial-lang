package spatial.targets

abstract class FPGATarget {
  def name: String    // FPGA name
  def burstSize: Int  // Size of DRAM burst (in bits)
}

case class Pin(name: String) {
  override def toString = name
}
case class Bus(valid: Pin, data: Seq[Pin]) {
  override def toString = "Bus(" + valid.toString + ": " + data.mkString(", ") + ")"
}

object Targets {
  var targets: Set[FPGATarget] = Set.empty
  targets += DefaultTarget
  targets += FakeTarget
}