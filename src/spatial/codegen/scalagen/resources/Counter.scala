abstract class Counterlike {
  def foreach(func: (Array[Int],Array[Boolean]) => Unit): Unit
}

case class Counter(start: Int, end: Int, step: Int, par: Int) extends Counterlike {
  private val parStep = par
  private val fullStep = parStep * step
  private val vecOffsets = Array.tabulate(par){p => p * step}

  def foreach(func: (Array[Int],Array[Boolean]) => Unit): Unit = {
    var i = start
    while (i < end) {
      val vec = vecOffsets.map{ofs => ofs + i} // Create current vector
      val valids = vec.map{ix => ix < end}     // Valid bits
      func(vec, valids)
      i += fullStep
    }
  }
}

case class Forever() extends Counterlike {
  def foreach(func: (Array[Int],Array[Boolean]) => Unit): Unit = {
    val vec = Array.tabulate(1){ofs => ofs} // Create current vector
    val valids = vec.map{ix => true}        // Valid bits

    while (true) {
      func(vec, valids)
    }
  }
}