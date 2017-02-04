package spatial.codegen.targets

// TODO: Name
object DefaultTarget extends FPGATarget {
  val burstSize = 3072 // in bits. TODO: This should actually be selectable
}
