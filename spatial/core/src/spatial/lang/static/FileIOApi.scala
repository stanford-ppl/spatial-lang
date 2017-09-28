package spatial.lang.static

import argon.core._
import forge._
import org.virtualized.virtualize
import spatial.lang.File._

trait FileIOApi { this: SpatialApi =>

  /** Loads the CSV at `filename` as an @Array, using the supplied `delimeter` (comma by default). **/
  @api def loadCSV1D[T:Type](filename: MString, delim: MString = opt[MString])(implicit cast: Cast[MString,T]): MArray[T] = {
    val del = delim.getOrElseCreate(",")
    val file = open_file(filename.s, write = false)
    val tokens = wrap(read_tokens(file, del.s))
    close_file(file)
    tokens.map{token => token.to[T] }
  }

  // FIXME: This will not work if delim2 is not \n, so we need to have a read_tokens take multiple delimiters
  /** Loads the CSV at `filename`as a @Matrix, using the supplied element delimeter and linebreaks across rows. **/
  @api def loadCSV2D[T:Type](filename: MString, delim1: MString = opt[MString], delim2: MString = opt[MString])(implicit cast: Cast[MString,T]): Matrix[T] = {
    val del1 = delim1.getOrElseCreate(",")
    val del2 = delim2.getOrElseCreate("\n")
    val file = open_file(filename.s, write = false)
    val all_tokens = wrap(read_tokens(file, del1.s))
    val row_tokens = wrap(read_tokens(file, del2.s))
    close_file(file)
    val data = all_tokens.map{token => token.to[T] }
    matrix(data, row_tokens.length, all_tokens.length / row_tokens.length)
  }

  /** Writes the given Array to the file at `filename` using the given `delimiter`.
    * If no delimiter is given, defaults to comma.
    **/
  @virtualize
  @api def writeCSV1D[T:Type](array: MArray[T], filename: MString, delim: MString = opt[MString]): MUnit = {
    val del = delim.getOrElseCreate(",")
    val file = open_file(filename.s, write = true)
    val length = array.length
    val i = fresh[Index]
    val token = {i: Exp[Index] => typ[T].ev(array(wrap(i))).toText.s }
    write_tokens(file, del.s, length.s, token, i)
    wrap(close_file(file))
  }

  /** Writes the given Matrix to the file at `filename` using the given element delimiter.
    * If no element delimiter is given, defaults to comma.
    **/
  @virtualize
  @api def writeCSV2D[T:Type](matrix: Matrix[T], filename: MString, delim1: MString = opt[MString], delim2: MString = opt[MString]): MUnit = {
    val del1 = delim1.getOrElseCreate(",")
    val del2 = delim2.getOrElseCreate("\n")
    val file = open_file(filename.s, write = true)
    val rows = matrix.rows
    val cols = matrix.cols
    val dummy = fresh[Index]

    for (i <- 0 until rows) {
      val token = {j: Exp[Index] => typ[T].ev(matrix(i, wrap(j))).toText.s }
      write_tokens(file, del1.s, cols.s, token, fresh[Index])
      write_tokens(file, del2.s, int32s(1), {_: Exp[Index] => MString.const("") }, dummy)
      ()
    }

    // for (i <- 0 until rows) {
    //   val j = fresh[Index]
    //   val row = matrix(i)
    //   val token = () => meta[T].ev(row(wrap(j))).toText.s
    //   write_tokens(file, delim1.s, cols.s, token, j)
    //   write_tokens(file, delim2.s, wrap(1), () => " ".toText.s, dummy)
    // }
    wrap(close_file(file))
  }

  /** Loads the given binary file at `filename` as an @Array. **/
  @virtualize
  @api def loadBinary[T:Type:Num](filename: MString): MArray[T] = {
    val file = MBinaryFile.open(filename.s, write = false)
    val array = MBinaryFile.read_values[T](file)
    MBinaryFile.close(file)
    wrap(array)
  }

  /** Saves the given Array to disk as a binary file at `filename`. **/
  @virtualize
  @api def writeBinary[T:Type:Num](array: MArray[T], filename: MString): MUnit = {
    val file = MBinaryFile.open(filename.s, write = true)
    val index = fresh[Index]
    MBinaryFile.write_values(file, array.length.s, {i => array(wrap(i)).s }, index)
    wrap(MBinaryFile.close(file))
  }

}