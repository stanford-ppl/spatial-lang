package spatial.targets

case class Pin(name: String) {
  override def toString = name
}
object Pin {
  def apply(name: String) = new Pin(name)
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
  def apply(valid: String, data: String*) = PinBus(Pin(valid), data.map(Pin(_)))
}

// TODO: Hack - remove later
case object GPInput extends Bus { val length = 32 }
case object GPOutput extends Bus { val length = 32 }

abstract class FPGATarget {
  def name: String    // FPGA name
  def burstSize: Int  // Size of DRAM burst (in bits)
  def latencyModel: Option[String] = None
}

object Targets {
  var targets: Set[FPGATarget] = Set.empty
  targets += DefaultTarget
  targets += FakeTarget
}