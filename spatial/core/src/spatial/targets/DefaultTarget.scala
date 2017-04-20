package spatial.targets

object DefaultTarget extends FPGATarget {
  def name = "Default"
  val burstSize = 512 // in bits. TODO: This should actually be selectable
}

