package spatial.lang

import argon.core._
import forge._
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




