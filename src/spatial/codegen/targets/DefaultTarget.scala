package spatial.codegen.targets

// TODO: Name
object DefaultTarget extends FPGATarget {
  def name = "Default"
  val burstSize = 3072 // in bits. TODO: This should actually be selectable
}

