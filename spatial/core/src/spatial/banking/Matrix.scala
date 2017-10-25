package spatial.banking

case class Matrix(data: Array[Array[Int]]) {
  def rows: Int = data.length
  def cols: Int = data.head.length
  def apply(i: Int, j: Int): Int = data(i)(j)
}