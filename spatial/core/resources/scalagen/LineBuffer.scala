import scala.reflect.ClassTag

case class LineBuffer[T:ClassTag](rows: Int, cols: Int, banks: Int, stride: Int, invalid: T) {
  val totalRows: Int = rows + stride
  val buffers: Array[Array[T]] = Array.fill(totalRows){
    Array.fill(banks){ Array.fill(cols)(invalid) }
  }

  private var activeRow: Int = 0 // The row currently being written to
  private var activeCol: Int = 0 // The column currently being written to

  private def rotatedRow(row: Int): Int = {
    val r = (activeRow - rows + row) % totalRows
    if (r < 0) r + totalRows else r
  }

  //def apply(row: Int, col: Int): T = buffers(rotatedRow(row))(col)
  def bankedRead(row: Int, bank: Int, col: Int): T = buffers.apply(rotatedRow(row)).apply(bank).apply(col)

  // Expected to be called after a row has been written
  def rotate(): Unit = {
    activeRow = (activeRow + 1) % totalRows
    activeCol = 0
  }

  def enq(data: T, rotate: Boolean = true): Unit = {
    val bank = activeCol % banks
    buffers(activeRow).apply(bank).update(activeCol / banks, data)
    activeCol = (activeCol + 1) % cols
  }
}