

object OOB {
  def readOrElse[T](rd: => T, els: Throwable => T): T = {
    try {
      rd
    }
    catch {case err: java.lang.ArrayIndexOutOfBoundsException =>
      els(err)
    }
  }
  def writeOrElse(wr: => Unit, els: Throwable => Unit): Unit = {
    try {
      wr
    }
    catch {case err: java.lang.ArrayIndexOutOfBoundsException =>
      els(err)
    }
  }
}