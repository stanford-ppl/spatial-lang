
case class Counter(name: String, start: Int, end: Int, stride: Int, par: Int)(implicit owner: Component) extends Module(owner) {
  import ModuleImplicits._

  private var current: Int = start
  private var values = Array.tabulate(par){i => Reg[Number](s"${name}_$i", X(IntFormat), Config.logControllers) }

  def value = values
  val en   = Reg[Bit](s"${name}_en", FALSE, Config.logControllers)
  val done = Reg[Bit](s"${name}_done", FALSE, Config.logControllers)

  override def swap() {
    values.foreach(_.swap())
    super.swap()
  }
  def step() = {
    if (en) {
      values.foreach(_.step())
      current = current + stride*par
      if (current >= end) {
        done <== TRUE
        current = 0
      }
      else {
        done <== FALSE
      }
      (0 until par).foreach{i =>
        values(i) <== Number(BigDecimal(current + stride*i), en, IntFormat)
      }
    }
    super.swap()
  }
}

case class CounterChain(name: String, counters: Array[Counter])(implicit owner: Component) extends Module(owner) {
  import ModuleImplicits._

  val en   = counters.last.en
  val done = counters.head.done
  private val N = counters.length

  def swap() { counters.foreach(_.swap()) }
  def step() {
    counters.foreach(_.step())
    (0 until N - 1).foreach{i => counters(i).en <== counters(i+1).done }
  }
}
object UnitCounterChain {
  def apply(name: String) = CounterChain(s"${name}_cchain", Array(Counter(s"${name}_ctr", 0, 1, 1, 1)))
}

abstract class Controller(owner: Component) extends Module(owner) {
  val cchain: CounterChain = UnitCounterChain(name)
  val en   = Reg[Bit](s"${name}_en", FALSE, Config.logControllers)
  val done = cchain.done
}

abstract class OuterController(owner: Component) extends Controller(owner) {
  var children: Seq[Controller] = Nil
  val N = children.length
}

case class SequentialPipe(name: String, owner: Component) extends OuterController(owner) {
  import ModuleImplicits._
  var state = 0
  def activeChild = children(state)

  override def step() {
    if (en) {
      if (!activeChild.done) {
        activeChild.en <== TRUE
      }
      else {
        activeChild.en <== FALSE
        state = (state + 1) % children.length
        activeChild.en <== TRUE
        cchain.en <== (!cchain.done && state == 0)
      }
    }
    super.step()
  }
}

case class MetaPipe(name: String, owner: Component) extends OuterController(owner) {

}

case class StreamPipe(name: String, owner: Component) extends OuterController(owner) {

}


case class InnerPipe(name: String, owner: Component) extends Controller(owner) {
  var operations: Seq[Module] = Nil

  override def swap() {
    operations.foreach(_.swap())
    super.swap()
  }
  override def step() {
    operations.foreach(_.step())
    super.step()
  }
}

case class ParallelPipe(name: String, owner: Component) extends Module(owner) {
  val en   = Reg[Bit](s"${name}_en", FALSE, Config.logControllers)
  val done = Reg[Bit](s"${name}_en", FALSE, Config.logControllers)

  override def step() {
    if (en) {
      if (!children.forall(_.done)) {
        children.foreach{child => child.en <== TRUE }
      }
      else {
        children.foreach{child => child.en <== FALSE }
        done <== TRUE
      }
    }
    super.step()
  }
}