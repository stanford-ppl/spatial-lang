// case class Counter(start: Int, end: Int, step: Int, par: Int) {
//   private val parStep = par
//   private val fullStep = parStep * step
//   private val vecOffsets = Array.tabulate(par){p => p * step}

//   def foreach(func: (Array[Int],Array[Boolean]) => Unit): Unit = {
//     var i = start
//     while (i < end) {
//       val vec = vecOffsets.map{ofs => ofs + i} // Create current vector
//       val valids = vec.map{ix => ix < end}     // Valid bits
//       func(vec, valids)
//       i += fullStep
//     }
//   }
// }
