
import scala.language.implicitConversions

case class Ptr[T](var x: Option[T] = None) {
  def init(x2: T): Ptr[T] = {
    if (x.isEmpty) { x = Some(x2) }
    this
  }
  def set(x2: T): Ptr[T] = { x = Some(x2); this }
  def value: T = x.get
}
object Ptr {
  implicit def unwrapPtr[T](x: Ptr[T]): T = x.value
  implicit def wrapPtr[T](x: T): Ptr[T] = Ptr(Some(x))
}
