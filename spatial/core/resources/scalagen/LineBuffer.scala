import scala.reflect.ClassTag

case class LineBuffer[T:ClassTag](rows: Int, cols: Int, stride: Int, invalid: T) {
  val totalRows: Int = rows + stride
  val buffers: Array[Array[T]] = Array.fill(totalRows){ Array.fill(cols)(invalid) }

  private var activeRow = 0 // The row currently being written to
  private var activeCol = 0 // The column currently being written to

  private def rotatedRow(row: Int) = {
    val r = (activeRow - rows + row) % totalRows
    if (r < 0) r + totalRows else r
  }

  def apply(row: Int, col: Int): T = buffers(rotatedRow(row))(col)


  // Expected to be called after a row has been written
  def rotate(): Unit = {
    activeRow = (activeRow + 1) % totalRows
    activeCol = 0
  }

  def enq(data: T, rotate: Boolean = true): Unit = {
    buffers(activeRow).update(activeCol, data)
    activeCol = (activeCol + 1) % cols
  }
}