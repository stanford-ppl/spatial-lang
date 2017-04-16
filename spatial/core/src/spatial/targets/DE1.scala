package spatial.targets

object DE1 extends FPGATarget {
  val name = "DE1"
  def burstSize = 96 

  case object VideoCamera extends Bus {def length = 24}
  case object VGA extends Bus {def length = 16}
  case object SliderSwitch extends Bus {def length = 32}
  case object LEDR extends Bus {def length = 32}
}
