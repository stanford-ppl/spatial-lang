package spatial.nodes

import spatial.compiler._

object FileType extends Type[MFile] {
  override def wrapped(x: Exp[MFile]) = new MFile(x)
  override def stagedClass = classOf[MFile]
  override def isPrimitive = false
}

/** IR Nodes **/
case class OpenFile(filename: Exp[MString], write: Boolean) extends Op[MFile] {
  def mirror(f:Tx) = MFile.open_file(f(filename), write)
}

case class CloseFile(file: Exp[MFile]) extends Op[Void] {
  def mirror(f:Tx) = MFile.close_file(f(file))
}

case class ReadTokens(file: Exp[MFile], delim: Exp[MString]) extends Op[MArray[MString]] {
  def mirror(f:Tx) = MFile.read_tokens(f(file), f(delim))
}

case class WriteTokens(
  file:  Exp[MFile],
  delim: Exp[MString],
  len:   Exp[Index],
  token: Block[MString],
  i:     Bound[Index]
) extends Op[Void] {
  def mirror(f:Tx) = MFile.write_tokens(f(file), f(delim), f(len), f(token), i)
  override def inputs = dyns(file, delim, len) ++ dyns(token)
  override def binds  = i +: super.binds
}