
abstract class Stateful[T](implicit owner: Component) extends Module(owner) {
  val log: Boolean
  def value: T

  val record = java.io.PrintStream(s"$name.vcd")

  override def step(time) {

  }
}

case class Reg[T<:Data](name: String, init: T, log: Boolean)(implicit owner: Component) extends Stateful(owner) {
  var next: T = init
  private var current: T = init

  def <==(x: T) { next = x }

  override def value: T = current

  override def swap() { current = next }
}


case class Delay[T<:Data](name: String, depth: Int, func: () => T, log: Boolean)(implicit owner: Component) extends Stateful(owner) {
  val buffer = new Array[T](depth)
  var wrAddr = 0
  var rdAddr = 1

  override def value: T = buffer(rdAddr)

  override def step(time: Long) = {
    super.step(time)
    buffer(wrAddr) = func()
  }
  override def swap() = {
    wrAddr = (1 + wrAddr) % depth
    rdAddr = (1 + rdAddr) % depth
  }
}
