import scala.reflect.ClassTag

case class LineBuffer[T:ClassTag](rows: Int, cols: Int, invalid: T) {
  val buffers: Array[Array[T]] = Array.fill(rows){ Array.fill(cols)(invalid) }

  private var start = 0
  private def rotatedRow(row: Int) = (start + row) % rows

  def apply(row: Int, col: Int): T = buffers(rotatedRow(row))(col)

  def store(col: Int, data: T, rotate: Boolean = true): Unit = {
    if (col == 0 && rotate) start = (start + 1) % rows
    buffers(start).update(col, data)
  }
}