package spatial.targets

object DE1 extends FPGATarget {
  val name = "DE1"
  def burstSize = 96  // ???

  // TODO: Some random pin names right now, should correspond to something real
  val VideoCamera = Bus(valid = Pin("A34"), data = List.tabulate(24){i => Pin(s"A$i") }:_*)
  val VGA = Bus(valid = Pin("A12"), data = List.tabulate(16){i => Pin(s"B$i")}:_*)
}
