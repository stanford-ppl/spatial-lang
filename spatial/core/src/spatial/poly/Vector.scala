package spatial.poly

case class Vector(private var data: Array[Value]){
  def len: Int = data.length
  def apply(i: Int): Value = data(i)
  def update(i: Int, x: Value): Unit = { data(i) = x }
  def array: Array[Value] = data

  def setAll(x: Value, length: Int = len): Unit = {
    (0 until len).foreach{i => data(i) = x }
  }

  def mutate(func: Value => Value): Unit = {
    (0 until len).foreach{i => data(i) = func(data(i)) }
  }
  def mutateWithIndex(func: (Value,Int) => Value): Unit = {
    (0 until len).foreach{i => data(i) = func(data(i), i) }
  }
  def mzip(that: Vector)(func: (Value,Value) => Value): Unit = {
    (0 until len).foreach{i => data(i) = func(data(i),that.data(i)) }
  }

  def reduce(func: (Value,Value) => Value): Value = this.data.reduce(func)
  def map(func: Value => Value): Vector = Vector(this.data.map{a => func(a)})
  def zip(that: Vector)(func: (Value,Value) => Value): Vector = {
    Vector(this.data.zip(that.data).map{case (a,b) => func(a,b)})
  }
  def exists(func: Value => Boolean): Boolean = data.exists(func)

  def unary_-(): Vector = this.map{x => -x }                // "Oppose"
  def /(lambda: Value): Vector = this.map{x => x / lambda } // "AntiScale"
  def *(lambda: Value): Vector = this.map{x => x * lambda } // "Scale"
  def /=(lambda: Value): Unit  = if (lambda != 1) this.mutate{x => x / lambda }
  def *=(lambda: Value): Unit  = if (lambda != 1) this.mutate{x => x * lambda }

  def |(that: Vector): Vector = this.zip(that){_|_}
  def +(that: Vector): Vector = this.zip(that){_+_}
  def -(that: Vector): Vector = this.zip(that){_-_}

  def |=(that: Vector): Unit = this.mzip(that){_|_}
  def +=(that: Vector): Unit = this.mzip(that){_+_}
  def -=(that: Vector): Unit = this.mzip(that){_-_}

  // Inner Product
  def +*+(that: Vector): Value = {
    var acc = 0
    (0 until len).foreach{i => acc += this(i) * that(i) }
    acc
  }

  def print(): Unit = { println(data.mkString(" ")) }

  def min: Value = data.min
  def max: Value = data.max

  def indexWhere(func: Value => Boolean): Int = data.indexWhere(func)

  def isZero: Boolean = indexWhere(_ != 0) == -1

  def firstNonZero(length: Int = len): Int = {
    (0 until length).find{i => this(i) != 0 }.getOrElse(-1)
  }

  /**
    * Returns the abs value and position of smallest absolute, non-zero element
    * If no non-zero elements, returns (-1, 1)
    */
  def minNonZero(length: Int = len): (Int, Value) = {
    val startIdx = this.indexWhere(_ != 0)
    if (startIdx == - 1) {
      (-1, 1)
    }
    else {
      var minIdx = startIdx
      var min = data(minIdx)
      (startIdx until length).foreach { i =>
        if (data(i) != 0) {
          val x = abs(data(i))
          if (x < min) { minIdx = i; min = x }
        }
      }
      (minIdx,min)
    }
  }

  def gcd(length: Int = len): Value = {
    val q = abs(this)
    var notZero = true
    var result = 0
    while (notZero) {
      notZero = false
      val (minIdx, min) = q.minNonZero(length)
      println(s"minIdx: $minIdx, min: $min")
      if (min != 1) {
        q.mutateWithIndex{(x,i) =>
          if (i == minIdx) x
          else {
            notZero ||= x != 0
            x % min
          }
        }
        q.print()
      }
      result = min
    }
    result
  }



  def normalize: Vector = { this / this.gcd() }
  def mnormalize(): Unit = { this /= this.gcd() }

  def normalizePositive(k: Int): Vector = {
    val div = this.gcd()
    if (this(k) < 0) { this / -div } else { this / div }
  }
  def mnormalizePositive(k: Int): Unit = {
    val div = this.gcd()
    if (this(k) < 0) { this /= -div } else { this / div }
  }

  def sort: Vector = Vector(data.sorted)
  def msort(): Unit = { scala.util.Sorting.quickSort(data) }

  override def equals(o: Any): Boolean = o match {
    case that: Vector => this.data.zip(that.data).forall{case (a,b) => a == b}
    case _ => false
  }

  def mkString(del: String): String = data.mkString(del)
}

object Vector {
  def alloc(arr: Array[Value]): Vector = new Vector(arr)
  def alloc(len: Int): Vector = new Vector(new Array[Value](len))
  def apply(vals: Value*): Vector = new Vector(vals.toArray)

  def combine(p1: Vector, p2: Vector, a: Value, b: Value): Vector = {
    p1.zip(p2){(x,y) => a*x + b*y}
  }
}