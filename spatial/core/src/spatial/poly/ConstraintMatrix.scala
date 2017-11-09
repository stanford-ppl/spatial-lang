package spatial.poly

case class ConstraintMatrix(
  private var r: Int,
  private var c: Int,
  private var p: Array[Constraint]
) {
  def rows: Int = r
  def setRows(rows: Int): Unit = r = rows

  def cols: Int = c
  def status(r: Int): Value = p(r).status


  def apply(r: Int): Constraint = p(r)
  def apply(r: Int, c: Int): Value = p(r)(c)
  def update(r: Int, c: Int, v: Value): Unit = p(r)(c) = v

  def swapRows(r1: Int, r2: Int): Unit = if (r1 != r2) {
    val tmp = p(r1)
    p(r1) = p(r2)
    p(r2) = tmp
  }
  def copyRow(src: Int, dst: Int): Unit = if (src != dst) {
    Array.copy(p(src).vec.array,0,p(dst).vec.array,0,c)
    p(dst).status = p(src).status
  }

  // Assumes rows is sorted, all values are in [0, p.length)
  def dropRows(rows: Seq[Int]): Unit = {
    var j = 0
    var k = 0
    val p2 = new Array[Constraint](p.length - rows.length)
    (0 until p.length).foreach{i =>
      if (k < rows.length && rows(k) == i) k += 1
      else {
        p2(j) = p(i)
        j += 1
      }
    }
    p = p2
    r = p2.length
  }

  def reorderRows(rows: Seq[Int]): Unit = {
    assert(rows.length == p.length)
    val p2 = rows.map{r => p(r) }.toArray
    p = p2
  }

  def extend(newRows: Int): Unit = {
    if (newRows > p.length) {
      p = p ++ Array.fill(newRows - rows)(Constraint.alloc(cols))
    }
    else if (newRows < p.length) {
      p = p.take(newRows)
    }
    r = newRows
  }


  def isEmptyPolytope: Boolean = {

  }
}


object ConstraintMatrix {
  def alloc(rows: Int, cols: Int): ConstraintMatrix = {
    val p = Array.fill(rows)(Constraint.alloc(cols))
    new ConstraintMatrix(rows, cols, p)
  }

  def apply(constraints: Seq[Constraint]): ConstraintMatrix = {
    new ConstraintMatrix(constraints.length, constraints.head.len, constraints.toArray)
  }
}