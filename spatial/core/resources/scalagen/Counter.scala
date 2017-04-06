import DataImplicits._

abstract class Counterlike {
  def foreach(func: (Array[Number],Array[Bit]) => Unit): Unit
}

case class Counter(start: Number, end: Number, step: Number, par: Number) extends Counterlike {
  private val parStep = par
  private val fullStep = parStep * step
  private val vecOffsets = Array.tabulate(par){p => p * step}

  def foreach(func: (Array[Number],Array[Bit]) => Unit): Unit = {
    var i = start
    while (i < end) {
      val vec = vecOffsets.map{ofs => Number(ofs + i) } // Create current vector
      val valids = vec.map{ix => Bit(ix < end) }        // Valid bits
      func(vec, valids)
      i += fullStep
    }
  }
}

case class Forever() extends Counterlike {
  def foreach(func: (Array[Number],Array[Bit]) => Unit): Unit = {
    val vec = Array.tabulate(1){ofs => Number(ofs) }  // Create current vector
    val valids = vec.map{ix => Bit(true) }                 // Valid bits

    while (true) {
      func(vec, valids)
    }
  }
}