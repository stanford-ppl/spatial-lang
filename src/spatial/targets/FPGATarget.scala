package spatial.targets

abstract class FPGATarget {
  def name: String    // FPGA name
  def burstSize: Int  // Size of DRAM burst (in bits)
}

case class Pin(name: String) {
  override def toString = name
}

abstract class Bus {
  def length: Int
}

case class PinBus(valid: Pin, data: Seq[Pin]) extends Bus {
  override def toString = "Bus(" + valid.toString + ": " + data.mkString(", ") + ")"
  def length = data.length
}

object Bus {
  def apply(valid: Pin, data: Pin*) = PinBus(valid, data)
}

object Targets {
  var targets: Set[FPGATarget] = Set.empty
  targets += DefaultTarget
  targets += FakeTarget
}