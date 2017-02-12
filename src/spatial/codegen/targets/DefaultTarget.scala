package spatial.codegen.targets

// TODO: Name
object DefaultTarget extends FPGATarget {
  def name = "Default"
  val burstSize = 512 // in bits. TODO: This should actually be selectable
}

