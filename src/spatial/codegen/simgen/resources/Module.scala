
trait Component {
  var submodules: Seq[Module] = Nil
}

abstract class Module(owner: Component) extends Component {
  val name: String
  def swap(): Unit = submodules.foreach(_.swap())
  def step(): Unit = submodules.foreach(_.step())
  def stop(): Unit = submodules.foreach(_.stop())
  owner.submodules = this +: owner.submodules
}


object ModuleImplicits {
  implicit def bitToBoolean(x: Bit): Boolean = x.value && x.valid
  implicit def regToBoolean(x: Reg[Bit]): Boolean = x.value.valid && x.value.valid
  implicit def regToValue[T](x: Reg[T]): T = x.value

  implicit def intToNumber(x: Int): Number = Number(BigDecimal(x), true, IntFormat)
  implicit def booleanToBit(x: Boolean): Bit = Bit(x, true)
}

object Config {
  def logControllers: Boolean = false
}

object State {
  var time: Long = 0
}