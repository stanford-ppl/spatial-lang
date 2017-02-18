case class Forever() {

  def foreach(func: (Array[Int],Array[Boolean]) => Unit): Unit = {
    while (true) {
      val vec = Array.tabulate(1){ofs => ofs} // Create current vector
      val valids = vec.map{ix => true}     // Valid bits
      func(vec, valids)
    }
  }
}
