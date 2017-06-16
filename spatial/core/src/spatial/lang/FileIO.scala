package spatial.lang

import argon.core._
import forge._
import org.virtualized._
import spatial.SpatialApi
import spatial.nodes._

case class File(s: Exp[File]) extends MetaAny[File] {
  @api override def ===(that: File) = this.s == that.s
  @api override def =!=(that: File) = this.s != that.s
  @api override def toText = MString.ify(this)
}

object File {
  implicit def fileIsStaged: Type[File] = FileType

  /** Constructors **/
  @internal def open_file(filename: Exp[MString], write: Boolean) = stageMutable(OpenFile(filename, write))(ctx)
  @internal def close_file(file: Exp[MFile]) = stageWrite(file)(CloseFile(file))(ctx)

  @internal def read_tokens(file: Exp[MFile], delim: Exp[MString]) = {
    stageWrite(file)(ReadTokens(file, delim))(ctx)
  }
  @internal def write_tokens(file: Exp[MFile], delim: Exp[MString], len: Exp[Index], token: Exp[Index] => Exp[MString], i: Bound[Index]) = {
    val tBlk = stageLambda1(i){ token(i) }
    val effects = tBlk.effects andAlso Write(file)
    stageEffectful(WriteTokens(file, delim, len, tBlk, i), effects)(ctx)
  }
}




trait FileIOApi { this: SpatialApi =>
  import File._

  @api def loadCSV1D[T:Type](filename: MString, delim: MString = opt[MString])(implicit cast: Cast[MString,T]): MArray[T] = {
    val del = delim.getOrElseCreate(",")
    val file = open_file(filename.s, write = false)
    val tokens = wrap(read_tokens(file, del.s))
    close_file(file)
    tokens.map{token => token.to[T] }
  }

  // FIXME: This will not work if delim2 is not \n, so we need to have a read_tokens take multiple delimiters
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
      write_tokens(file, del2.s, int32(1), {_: Exp[Index] => MString.const("") }, dummy)
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
}
