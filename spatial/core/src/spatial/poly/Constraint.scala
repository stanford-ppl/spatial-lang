package spatial.poly

case class Constraint(var status: Value, private var vector: Vector) {
  def len: Int = vector.len
  def apply(i: Int): Value = if (i >= 0) vector.apply(i) else status
  def update(i: Int, x: Value): Unit = vector.update(i,x)

  def vec: Vector = vector

  def mnegate(): Unit = {
    status = -status
    (0 until vector.len).foreach{i => vector(i) = -vector(i) }
  }

  /**
    * Replaces constraint a x >= c by x >= ceil(c/a)
    * where "a" is a common factor in the coefficients
    * Returns true if constraint could be simplified
    */
  def simplify: (Boolean, Constraint) = {
    val v = vector.gcd()
    val newp = vector / v
    val v2 = newp.gcd(len - 1)
    if (v2 == 1) {
      (false, Constraint(status,newp))
    }
    else {
      newp.mzip(vector){(_,x) => x / v2 }
      newp(len - 1) = vector(len-1) floordiv v2
      (true, Constraint(status,newp))
    }
  }

  override def toString: String = s"""$status ${vector.mkString(" ")}"""
}

object Constraint {
  def alloc(cols: Int) = new Constraint(0, Vector.alloc(cols))

  /**
    * Sets constraint p3 to a linear combination of two vectors (p1 and p2)
    * such that p3(pos) is zero if pos is >= 0. The flag of p3 is not changed.
    * However, the value of 'pos' may be -1 to denote the flag.
    */
  def combine(p1: Constraint, p2: Constraint, p3: Constraint, pos: Int): Unit = {
    var a1 = p1(pos)
    var a2 = p2(pos)
    val abs_a1 = abs(a1)
    val abs_a2 = abs(a2)
    val gcd = GCD(abs_a1, abs_a2)
    a1 = a1 / gcd
    a2 = a2 / gcd
    val neg_a1 = -a1
    //val vec3 = Vector.combine(p1.vector, p2.vector, a2, neg_a1)
    // p1.zip(p2){(x,y) => a*x + b*y}
    //(0 until p1.len).foreach{i => p3(i) = a2*p1(i) + neg_a1*p2(i) }
    p3.vec.mutateWithIndex{(x,i) => a2*x + neg_a1*p2(i) }
    p3.vec.mnormalize()
    //p3.vector = vec3
  }

}
