import scala.reflect.ClassTag

case class LineBuffer[T:ClassTag](rows: Int, cols: Int, invalid: T) {
  val buffers: Array[Array[T]] = Array.fill(rows){ Array.fill(cols)(invalid) }

  private var start = 0
  private def rotatedRow(row: Int) = {
    val r = (start - rows + row + 1) % rows
    if (r < 0) r + rows else r
  }

  def apply(row: Int, col: Int): T = buffers(rotatedRow(row))(col)

  var colEnqCount = 0

  def enq(data: T, rotate: Boolean = true): Unit = {
    if (colEnqCount == 0 && rotate) start = (start + 1) % rows
    buffers(start).update(colEnqCount, data)
    colEnqCount = (colEnqCount + 1) % cols
  }
}