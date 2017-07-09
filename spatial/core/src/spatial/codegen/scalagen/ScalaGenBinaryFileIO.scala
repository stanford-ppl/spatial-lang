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

        // TODO: How to do more arbitrary binary file reading?
        op.mT match {
          case FixPtType(s,i,f) if (s && i+f <= 8) || (!s && i+f < 8) =>
            emit(src"val array = new Array[Byte](buffer.length)")
            emit(src"bb.get(array)")
          case FixPtType(s,i,f) if (s && i+f <= 16) || (!s && i+f < 16) =>
            emit(src"val array = new Array[Short](buffer.length/2)")
            emit(src"bb.asShortBuffer.get(array)")
          case FixPtType(s,i,f) if (s && i+f <= 32) || (!s && i+f < 32) =>
            emit(src"val array = new Array[Int](buffer.length/4)")
            emit(src"bb.asIntBuffer.get(array)")
          case FixPtType(s,i,f) if (s && i+f <= 64) || (!s && i+f < 64) =>
            emit(src"val array = new Array[Long](buffer.length/8)")
            emit(src"bb.asLongBuffer.get(array)")

          case FloatType() =>
            emit(src"val array = new Array[Float](buffer.length/4)")
            emit(src"bb.asFloatBuffer.get(array)")

          case DoubleType() =>
            emit(src"val array = new Array[Double](buffer.length/8)")
            emit(src"bb.asDoubleBuffer.get(array)")

          case _ => throw new Exception("Binary file reading not yet supported for arbitrary precision")
        }

        open(src"array.map{x => ")
          op.mT match {
            case FixPtType(s,i,f) => emit(src"Number.fromBits(x, FixedPoint($s,$i,$f))")
            case FltPtType(g,e)   => emit(src"Number(x, FloatPoint($g,$e))")
          }
        close("}")
      close("}")

    case op @ WriteBinaryFile(file, len, value, index) =>
      open(src"val $lhs = {")
        emit(src"var $index = Number(0)")
        emit(src"val stream = new java.io.DataOutputStream(new java.io.FileOutputStream($file))")
        open(src"while ($index < $len) {")
          visitBlock(value)
          emit(src"val value = ${value.result}")
          op.mT match {
            case FixPtType(s,i,f) if (s && i+f <= 8) || (!s && i+f < 8)   => emit(src"stream.writeByte(value.fixValueByte)")
            case FixPtType(s,i,f) if (s && i+f <= 16) || (!s && i+f < 16) => emit(src"stream.writeShort(value.fixValueShort)")
            case FixPtType(s,i,f) if (s && i+f <= 32) || (!s && i+f < 32) => emit(src"stream.writeInt(value.fixValueInt)")
            case FixPtType(s,i,f) if (s && i+f <= 64) || (!s && i+f < 64) => emit(src"stream.writeLong(value.fixValueLong)")
            case FloatType()  => emit(src"stream.writeFloat(value.toFloat)")
            case DoubleType() => emit(src"stream.writeDouble(value.toDouble)")
            case _ => throw new Exception("Binary file reading not yet supported for arbitrary precision")
          }

          emit(src"$index = $index + 1")
        close("}")
        emit(src"stream.close()")
      close("}")

    case CloseBinaryFile(file) => // Anything for this?

    case _ => super.emitNode(lhs, rhs)
  }

}