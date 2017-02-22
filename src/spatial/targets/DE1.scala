package spatial.targets

// TODO: Name?
object DE1 extends FPGATarget {
  val name = "DE1"
  def burstSize = 96  // ???

  // TODO: Some random pin names right now, should correspond to something real
  val VideoCamera = Bus(valid = Pin("A32"), data = List.tabulate(32){i => Pin(s"A$i") })
}
