package spatial.codegen.scalagen

import argon.codegen.scalagen.ScalaCodegen
import argon.core._
import argon.nodes._
import spatial.nodes._

trait ScalaGenBinaryFileIO extends ScalaCodegen {

  override protected def remap(tp: Type[_]): String = tp match {
    case BinaryFileType => "String" // ??? //src"java.nio.file.Path"
    case _ => super.remap(tp)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case OpenBinaryFile(filename, write) =>
      emit(src"val $lhs = $filename")

    case op @ ReadBinaryFile(file) =>
      open(src"val $lhs = {")
        emit(src"val filepath = java.nio.file.Paths.get($file)")
        emit(src"val buffer = java.nio.file.Files.readAllBytes(filepath)")
        emit(src"val bb = java.nio.ByteBuffer.wrap(buffer)")
        emit(src"bb.order(java.nio.ByteOrder.nativeOrder)")
        emit(src"val array = bb.array")
        val bytes = Math.ceil(op.bT.length.toDouble / 8).toInt

        open(s"array.sliding($bytes,$bytes).toArray.map{x => ")
        op.mT match {
          case FixPtType(s,i,f) => emit(src"FixedPoint.fromByteArray(x, FixFormat($s,$i,$f))")
          case FltPtType(g,e)   => emit(src"FloatPoint.fromByteArray(x, FltFormat(${g-1},$e))")
          case tp => throw new Exception(u"Reading binary files of type $tp is not yet supported")
        }
        close("}")
      close("}")

    case op @ WriteBinaryFile(file, len, value, index) =>
      open(src"val $lhs = {")
        emit(src"var $index = FixedPoint(0)")
        emit(src"val stream = new java.io.DataOutputStream(new java.io.FileOutputStream($file))")
        open(src"while ($index < $len) {")
          visitBlock(value)
          emit(src"val value = ${value.result}")
          op.mT match {
            case FixPtType(_,_,_) => emit(src"value.toByteArray.foreach{byte => stream.writeByte(byte) }")
            case FltPtType(_,_)   => emit(src"value.toByteArray.foreach{byte => stream.writeByte(byte) }")
            case tp => throw new Exception(u"Binary file reading not yet supported for type $tp")
          }
          emit(src"$index = $index + 1")
        close("}")
        emit(src"stream.close()")
      close("}")

    case CloseBinaryFile(file) => // Anything for this?

    case _ => super.emitNode(lhs, rhs)
  }

}