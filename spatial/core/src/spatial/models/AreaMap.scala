package spatial.models

case class AreaConfig[T](fields: Array[String], default: T)

case class AreaMap[T](name: String, params: Seq[String], entries: Map[String,T])(implicit val config: AreaConfig[T]) {
  def fullName: String = name + "_" + params.mkString("_")
  private val fields: Array[String] = config.fields
  private val default = config.default
  def keys: Array[String] = fields
  def nonZeroFields: Array[String] = fields.filter{f => entries.contains(f) && entries(f) != default }
  // HACK - get parameter which gives number
  def n: Option[Int] = {
    val i = params.lastIndexWhere(_ != "")
    if (i >= 0) {
      val x = params(i)
      if (x.nonEmpty && x.forall(_.isDigit)) Some(x.toInt) else None
    }
    else None
  }
  def renameEntries(remapping: String => String): AreaMap[T] = {
    val entries2 = entries.map{case (k,v) => remapping(k) -> v }
    new AreaMap(name, params, entries2)
  }

  def toSeq: Seq[T] = fields.map{f =>
    if (entries.contains(f)) {
      entries(f)
    }
    else {
      default
    }
  }

  def apply(field: String): T = entries.getOrElse(field, default)
  def seq(keys: String*): Seq[T] = keys.map{k => this(k)}

  def map[R](func: T => R)(implicit config: AreaConfig[R]): AreaMap[R] = AreaMap(name, params, entries.map{case (k,v) => k -> func(v)})
  def zip[S,R](that: AreaMap[S])(func: (T,S) => R)(implicit config: AreaConfig[R]): AreaMap[R] = {
    AreaMap(name, params, fields.map{k => k -> func(this(k), that(k)) }.toMap)
  }
  def zipExists(that: AreaMap[T])(func: (T,T) => Boolean): Boolean = fields.exists{k => func(this(k), that(k)) }
  def zipForall(that: AreaMap[T])(func: (T,T) => Boolean): Boolean = fields.forall{k => func(this(k), that(k)) }

  def +(that: AreaMap[T])(implicit num: AffArith[T]): AreaMap[T] = this.zip(that){(a,b) => num.plus(a,b) }
  def -(that: AreaMap[T])(implicit num: AffArith[T]): AreaMap[T] = this.zip(that){(a,b) => num.minus(a,b) }
  def /(that: AreaMap[Double])(implicit num: AffArith[T]): AreaMap[T] = this.zip(that){(a,b) => num.div(a,b) }
  def *(b: Double)(implicit num: AffArith[T]): AreaMap[T] = this.map{x => num.times(x,b) }
  def /(b: Double)(implicit num: AffArith[T]): AreaMap[T] = this.map{x => num.div(x,b) }

  def isNonZero(implicit num: Numeric[T], ord: Ordering[T]): Boolean = this.toSeq.exists{x => ord.gt(x, num.fromInt(0)) }

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
  def toPrintableString(nParams: Int): String = {
    val padParams = Array.fill(nParams - params.length)("")
    val seq = this.toSeq
    (Array(name) ++ params ++ padParams ++ seq).mkString(",")
  }
}

object AreaMap {
  def zero[T](implicit config: AreaConfig[T]): AreaMap[T] = new AreaMap[T]("", Nil, Map.empty)
  def apply[T](entries: (String,T)*)(implicit config: AreaConfig[T]): AreaMap[T] = new AreaMap("", Nil, entries.toMap)

  def fromArray[T](name: String, params: Seq[String], entries: Array[T])(implicit config: AreaConfig[T]): AreaMap[T] = {
    new AreaMap(name, params, config.fields.zip(entries).toMap)
  }

  /*def fromLine[T](line: String, nParams: Int, indices: Seq[Int])(func: String => T)(implicit config: AreaConfig[T]): (String, AreaMap[T]) = {
    val parts = line.split(",").map(_.trim)
    val name  = parts.head
    val params = parts.slice(1,nParams+1)
    val entries = indices.map{i => func(parts(i)) }
    name -> AreaMap(params, config.fields.zip(entries).toMap)
  }*/
}
