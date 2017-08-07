package spatial.models

trait AreaConfig[T] {
  val fields: Array[String]
  val default: T
}

case class AreaMap[T](params: Seq[String], entries: Map[String,T])(implicit config: AreaConfig[T]) {
  private val fields = config.fields
  private val default = config.default
  def keys: Array[String] = fields

  def toArray: Array[T] = fields.map{f => entries.getOrElse(f, default) }

  def apply(field: String): T = entries.getOrElse(field, default)
  def array(keys: String*): Array[T] = keys.map{k => this(k)}.toArray

  def map[R](func: T => R)(implicit config: AreaConfig[R]): AreaMap[R] = AreaMap(params, entries.mapValues{v => func(v)})
  def zip(that: AreaMap[T])(func: (T,T) => T): AreaMap[T] = {
    AreaMap(params, fields.map{k => k -> func(this(k), that(k)) }.toMap)
  }
  def zipExists(that: AreaMap[T])(func: (T,T) => Boolean): Boolean = fields.exists{k => func(this(k), that(k)) }
  def zipForall(that: AreaMap[T])(func: (T,T) => Boolean): Boolean = fields.forall{k => func(this(k), that(k)) }

  def +(that: AreaMap[T])(implicit num: Numeric[T]): AreaMap[T] = this.zip(that){(a,b) => num.plus(a,b) }
  def -(that: AreaMap[T])(implicit num: Numeric[T]): AreaMap[T] = this.zip(that){(a,b) => num.minus(a,b) }
  def /(that: AreaMap[T])(implicit num: Fractional[T]): AreaMap[T] = this.zip(that){(a,b) => num.div(a,b) }
  def *(b: Int)(implicit num: Numeric[T]): AreaMap[T] = this.map{x => num.times(x,num.fromInt(b)) }

  def isNonZero(implicit num: Numeric[T], ord: Ordering[T]): Boolean = this.toArray.exists{x => ord.gt(x, num.fromInt(0)) }

  def <(that: AreaMap[T])(implicit ord: Ordering[T]): Boolean = this.zipForall(that){(a,b) => ord.lt(a,b) }   // a0 < b0 && ... && aN < bN
  def <=(that: AreaMap[T])(implicit ord: Ordering[T]): Boolean = this.zipForall(that){(a,b) => ord.lteq(a,b) } // a0 <= b0 && ... && aN <= bN
  // These may seem a bit odd, but required to have the property !(a < b) = a >= b
  def >(that: AreaMap[T])(implicit ord: Ordering[T]): Boolean = this.zipExists(that){(a,b) => ord.gt(a,b) }   // a0 > b0 || ... || aN > b0
  def >=(that: AreaMap[T])(implicit ord: Ordering[T]): Boolean = this.zipExists(that){(a,b) => ord.gteq(a,b) } // a0 >= b0 || ... || aN >= b0

  // Alternative comparisons, where < is true if any is less than, > is true iff all are greater
  def <<(that: AreaMap[T])(implicit ord: Ordering[T]): Boolean = this.zipExists(that){(a,b) => ord.lt(a,b) }
  def <<=(that: AreaMap[T])(implicit ord: Ordering[T]): Boolean = this.zipExists(that){(a,b) => ord.lteq(a,b) }
  def >>(that: AreaMap[T])(implicit ord: Ordering[T]): Boolean = this.zipForall(that){(a,b) => ord.gt(a,b) }
  def >>=(that: AreaMap[T])(implicit ord: Ordering[T]): Boolean = this.zipForall(that){(a,b) => ord.gteq(a,b) }

  override def toString: String = {
    "Area" + fields.map{f => f -> this(f) }
                   .filterNot(_._2 == default)
                   .map{case (f,v) => s"$f=$v"}
                   .mkString("(", ", ", ")")
  }
}

object AreaMap {
  def zero[T](implicit config: AreaConfig[T]): AreaMap[T] = new AreaMap[T](Nil, Map.empty)
  def apply[T](entries: (String,T)*)(implicit config: AreaConfig[T]): AreaMap[T] = new AreaMap(Nil, entries.toMap)

  def fromLine[T](line: String, nParams: Int, indices: Seq[Int])(func: String => T)(implicit config: AreaConfig[T]): (String, AreaMap[T]) = {
    val parts = line.split(",").map(_.trim)
    val name  = parts.head
    val params = parts.slice(1,nParams+1)
    val entries = indices.map{i => func(parts(i)) }
    name -> AreaMap(params, config.fields.zip(entries).toMap)
  }
}
