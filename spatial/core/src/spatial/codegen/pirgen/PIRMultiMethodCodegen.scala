package spatial.codegen.pirgen

import argon.codegen.{Codegen, FileDependencies}
import argon.core._
import spatial.metadata._

trait PIRMultiMethodCodegen extends Codegen {

  private var splitting = false
  private var lineCount = 0

  val splitThreshold = 800

  var splitCount = 0

  override def emit(x: String, forceful: Boolean = false): Unit = { 
    super.emit(x, forceful)
    if (splitting) {
      lineCount += 1
      if (lineCount > splitThreshold) {
        splitCount += 1
        lineCount = 0
        super.emit(s"def split${splitCount} = {")
      }
    }
  }

  def startSplit = {
    splitting = true
    lineCount = 0
  }

  def endSplit = {
    splitting = false
    lineCount = 0
    (splitCount until 0 by -1).foreach { splitCount =>
      emit(s"}; split${splitCount}")
    }
  }

  def split[T](block: => T) = {
    startSplit
    val res = block
    endSplit
    res
  }

}
