package spatial.lang

import argon.core._
import forge._
import spatial.nodes._

case class BinaryFile(s: Exp[BinaryFile]) extends MetaAny[BinaryFile] {
  @api override def ===(that: BinaryFile) = this.s == that.s
  @api override def =!=(that: BinaryFile) = this.s != that.s
  @api override def toText = MString.ify(this)
}

object BinaryFile {
  implicit def binaryFileIsStaged: Type[BinaryFile] = BinaryFileType

  @internal def open(filename: Exp[MString], write: Boolean): Exp[BinaryFile] = {
    stageMutable(OpenBinaryFile(filename, write))(ctx)
  }
  @internal def close(file: Exp[BinaryFile]): Exp[MUnit] = {
    stageWrite(file)(CloseBinaryFile(file))(ctx)
  }

  @internal def read_values[T:Type:Num](file: Exp[BinaryFile]): Exp[MArray[T]] = {
    stage(ReadBinaryFile[T](file))(ctx)
  }
  @internal def write_values[T:Type:Num](file: Exp[BinaryFile], len: Exp[Index], value: Exp[Index] => Exp[T], i: Bound[Index]): Exp[MUnit] = {
    val lambda = stageLambda1(i){ value(i) }
    stageWrite(file)(WriteBinaryFile(file,len,lambda,i))(ctx)
  }
}
