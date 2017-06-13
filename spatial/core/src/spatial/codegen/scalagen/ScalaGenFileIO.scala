package spatial.codegen.scalagen

import argon.codegen.scalagen.ScalaCodegen
import spatial.compiler._
import spatial.nodes._

trait ScalaGenFileIO extends ScalaCodegen {

  override protected def remap(tp: Type[_]): String = tp match {
    case FileType => src"java.io.File"
    case _ => super.remap(tp)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case OpenFile(filename, write) => emit(src"val $lhs = new java.io.File($filename)")

    case ReadTokens(file, delim) =>
      open(src"val $lhs = {")
        emit(src"val scanner = new java.util.Scanner($file)")
        emit(src"val tokens = new scala.collection.mutable.ArrayBuffer[String]() ")
        emit(src"""scanner.useDelimiter("\\s*" + $delim + "\\s*|\\s*\n\\s*")""")
        open(src"while (scanner.hasNext) {")
          emit(src"tokens += scanner.next.trim")
        close("}")
        emit(src"scanner.close()")
        emit(src"tokens.toArray")
      close("}")

    case WriteTokens(file, delim, len, token, i) =>
      open(src"val $lhs = {")
        emit(src"val writer = new java.io.PrintWriter($file)")
        open(src"for ($i <- 0 until $len.toInt) {")
          emit(src"if ($i > 0) writer.write($delim)")
          visitBlock(token)
          emit(src"writer.write(${token.result})")
        close("}")
        emit(src"writer.close()")
      close("}")

    case CloseFile(file) => // Nothing for now?

    case _ => super.emitNode(lhs, rhs)
  }

}
