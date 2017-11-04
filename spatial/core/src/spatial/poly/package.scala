package spatial

package object poly {
  type Value = Int

  def abs(x: Value): Value = math.abs(x)
  def abs(v: Vector): Vector = v.map{x => abs(x) }

  implicit class FloorDivOp(lhs: Value) {
    def floordiv(rhs: Value): Value = {
      if (lhs < 0 && lhs % rhs != 0) {
        (lhs / rhs) - 1
      }
      else {
        lhs / rhs
      }
    }
  }

  def LCM(a: Value, b: Value): Value = {
    val bigA = BigInt(a)
    val bigB = BigInt(b)
    (bigA*bigB / bigA.gcd(bigB)).intValue()
  }
  def GCD(a: Value, b: Value): Value = {
    val bigA = BigInt(a)
    val bigB = BigInt(b)
    bigA.gcd(bigB).intValue()

    /*var acopy = a
    var bcopy = b
    while (acopy != 0) {
      val tmp = bcopy % acopy
      bcopy = acopy
      acopy = tmp
    }
    abs(bcopy)*/
  }

}
