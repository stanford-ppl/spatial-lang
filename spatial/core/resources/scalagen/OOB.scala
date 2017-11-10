

object OOB {
  def readOrElse[T](rd: => T, els: => T): T = {
    try {
      rd
    }
    catch {case err: java.lang.ArrayIndexOutOfBoundsException =>
      els
    }
  }
  def writeOrElse(wr: => Unit, els: => Unit): Unit = {
    try {
      wr
    }
    catch {case err: java.lang.ArrayIndexOutOfBoundsException =>
      els
    }
  }
}