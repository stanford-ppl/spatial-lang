
import scala.language.implicitConversions

object DataImplicits {
  implicit def fixedPointToInt(x: FixedPoint): Int = x.toInt
  implicit def intToFixedPoint(x: Int): FixedPoint = FixedPoint(x)
  implicit def boolToBoolean(x: Bool): Boolean = x.value
  implicit def booleanToBool(x: Boolean): Bool = Bool(x)
}