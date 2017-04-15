package spatial.targets

// TODO: Name?
object DE1 extends FPGATarget {
  val name = "DE1"
  def burstSize = 96 

  case object VideoCamera extends Bus {def length = 24}
  case object VGA extends Bus {def length = 16}
  case object LEDR extends Bus {def length = 32}

  // val VideoCamera = Bus(valid = Pin("A34"), data = List.tabulate(24){i => Pin(s"A$i") }:_*)
  // val VGA = Bus(valid = Pin("A33"), data = List.tabulate(33){i => Pin(s"A$i") }:_*)
}
