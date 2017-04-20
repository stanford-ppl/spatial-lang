import spatial._
import org.virtualized._

object ArrayAtomicWrite extends SpatialApp {
  import IR._

  @virtualize
  def main() {
    Accel { }

    val x = Array.empty[Array[Int]](32)

    Array.tabulate(32){i => x(i) = Array.fill(16){ 0.to[Int] } }

    x(0)(1) = 3

    println("" + x(0).apply(1))
  }
}
