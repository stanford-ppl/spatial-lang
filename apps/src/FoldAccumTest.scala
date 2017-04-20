import spatial._
import org.virtualized._

object FoldAccumTest extends SpatialApp {
  import IR._

  @virtualize
  def main() {
    Accel {
      val product = Reg[Int](1)
      Reduce(product)(16 by 1) { i => i } {_ * _}
      val sum2 = Reduce(0)(0 :: 1 :: 16 par 2) { i => i } {_ + _}
      println(product.value)
      println(sum2.value)
    }
  }
}