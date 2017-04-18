package spatial.api

import spatial._
import forge._
import org.virtualized._

trait FileIOApi { this: SpatialApi =>

  @api def loadCSV1D[T:Meta](filename: Text, delim: Text = ",")(implicit cast: Cast[Text,T]): MetaArray[T] = {
    val file = open_file(filename.s, write = false)
    val tokens = wrap(read_tokens(file, delim.s))
    close_file(file)
    tokens.map{token => token.to[T] }
  }

  // FIXME: This may not work if delim2 is not linebreak - maybe should have ND file reading support?
  @api def loadCSV2D[T:Meta](filename: Text, delim1: Text = ",", delim2: Text = "\n")(implicit cast: Cast[Text,T]): Matrix[T] = {
    val file = open_file(filename.s, write = false)
    val all_tokens = wrap(read_tokens(file, delim1.s))
    val row_tokens = wrap(read_tokens(file, delim2.s))
    close_file(file)
    val data = all_tokens.map{token => token.to[T] }
    matrix(data, row_tokens.length, all_tokens.length / row_tokens.length)
  }

  @virtualize
  @api def writeCSV[T:Meta](array: MetaArray[T], filename: Text, delim: Text = ""): Void = {
    val file = open_file(filename.s, write = true)
    val length = array.length
    val i = fresh[Index]
    val token = () => meta[T].ev(array(wrap(i))).toText.s
    write_tokens(file, delim.s, length.s, token(), i)
    wrap(close_file(file))
  }

}

trait FileIOExp { this: SpatialExp =>

  implicit object MetaFileType extends Meta[MetaFile] {
    override def wrapped(x: Exp[MetaFile]) = MetaFile(x)
    override def stagedClass = classOf[MetaFile]
    override def isPrimitive = false
  }

  case class MetaFile(s: Exp[MetaFile]) extends MetaAny[MetaFile] {
    @api override def ===(that: MetaFile) = this.s == that.s
    @api override def =!=(that: MetaFile) = this.s != that.s
    @api override def toText = textify(this)
  }

  /** IR Nodes **/
  case class OpenFile(filename: Exp[Text], write: Boolean) extends Op[MetaFile] {
    def mirror(f:Tx) = open_file(f(filename), write)
  }

  case class CloseFile(file: Exp[MetaFile]) extends Op[Void] {
    def mirror(f:Tx) = close_file(f(file))
  }

  case class ReadTokens(file: Exp[MetaFile], delim: Exp[Text]) extends Op[MetaArray[Text]] {
    def mirror(f:Tx) = read_tokens(f(file), f(delim))
  }

  case class WriteTokens(
    file:  Exp[MetaFile],
    delim: Exp[Text],
    len:   Exp[Index],
    token: Block[Text],
    i:     Bound[Index]
  ) extends Op[Void] {
    def mirror(f:Tx) = write_tokens(f(file), f(delim), f(len), f(token), i)
    override def inputs = dyns(file, delim, len) ++ dyns(token)
    override def binds  = i +: super.binds
  }

  // Should be able to generalize to ND read/write fairly easily from this

  /** Constructors **/
  @internal def open_file(filename: Exp[Text], write: Boolean) = stageMutable(OpenFile(filename, write))(ctx)
  @internal def close_file(file: Exp[MetaFile]) = stageWrite(file)(CloseFile(file))(ctx)

  @internal def read_tokens(file: Exp[MetaFile], delim: Exp[Text]) = {
    stageWrite(file)(ReadTokens(file, delim))(ctx)
  }
  @internal def write_tokens(file: Exp[MetaFile], delim: Exp[Text], len: Exp[Index], token: => Exp[Text], i: Bound[Index]) = {
    val tBlk = stageBlock{ token }
    val effects = tBlk.summary andAlso Write(file)
    stageEffectful(WriteTokens(file, delim, len, tBlk, i), effects)(ctx)
  }

}

