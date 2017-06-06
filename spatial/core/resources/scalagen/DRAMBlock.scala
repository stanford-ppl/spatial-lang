import scala.reflect.ClassTag

case class DRAMBlock[T:ClassTag](count: Int, zero: T) {
  private val data = Array.fill(count)(zero)
  private var valid = true

  private def checkValid = if (!valid) { throw new Exception("DRAM block has been deallocated.") }

  def apply(i: Int): T = {
    checkValid
    data(i)
  }

  def update(i: Int, x: T) = {
    checkValid
    data(i) = x
  }

  def dealloc = valid = false
}
