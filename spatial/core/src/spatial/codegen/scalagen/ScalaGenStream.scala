package spatial.codegen.scalagen

import argon.core._
import argon.nodes._
import spatial.aliases._
import spatial.banking._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._

trait ScalaGenStream extends ScalaGenMemories with ScalaGenControl {
  var streamIns: List[Exp[_]] = Nil
  var streamOuts: List[Exp[_]] = Nil
  var bufferedOuts: List[Exp[_]] = Nil
  dependencies ::= FileDep("scalagen", "Stream.scala")

  override protected def remap(tp: Type[_]): String = tp match {
    case tp: StreamInType[_]  => src"scala.collection.mutable.Queue[${tp.child}]"
    case tp: StreamOutType[_] => src"scala.collection.mutable.Queue[${tp.child}]"
    case tp: BufferedOutType[_] => src"Memory[${tp.child}]"
    case _ => super.remap(tp)
  }

  override protected def emitControlDone(ctrl: Exp[_]): Unit = {
    super.emitControlDone(ctrl)

    val written = localMems.filter{mem => writersOf(mem).exists{wr => topControllerOf(wr.node,mem).exists(_.node == ctrl) } }
    val bufferedOuts = written.filter(isBufferedOut)
    if (bufferedOuts.nonEmpty) {
      emit("/** Dump BufferedOuts **/")
      bufferedOuts.foreach{buff => emit(src"$buff.dump()") }
      emit("/***********************/")
    }
  }

  // HACK
  def bitsFromString(lhs: String, line: String, tp: Type[_]): Unit = tp match {
    case FixPtType(s,i,f)  => emit(s"val $lhs = FixedPoint($line, FixFormat($s,$i,$f))")
    case FltPtType(g,e)    => emit(s"val $lhs = FixedPoint($line, FltFormat(${g-1},$e))")
    case BooleanType()     => emit(s"val $lhs = Bool($line.toBoolean, true)")
    case tp: VectorType[_] =>
      open(s"""val $lhs = $line.split(",").map(_.trim).map{elem => """)
        bitsFromString("out", "elem", tp.child)
        emit("out")
      close("}.toArray")
    case tp: StructType[_] =>
      emit(s"""val tokens = $line.split(";").map(_.trim)""")
      tp.fields.zipWithIndex.foreach{case (field,i) =>
        bitsFromString(s"field$i", s"tokens($i)", field._2)
      }
      emit(src"val $lhs = $tp(" + List.tabulate(tp.fields.length){i => s"field$i"}.mkString(", ") + ")")

    case _ => throw new Exception(c"Cannot create Stream with type $tp")
  }

  def bitsToString(lhs: String, elem: String, tp: Type[_]): Unit = tp match {
    case FixPtType(s,i,f) => emit(s"val $lhs = $elem.toString")
    case FltPtType(g,e)   => emit(s"val $lhs = $elem.toString")
    case BooleanType()    => emit(s"val $lhs = $elem.toString")
    case tp: VectorType[_] =>
      open(s"""val $lhs = $elem.map{elem => """)
        bitsToString("out", "elem", tp.child)
        emit("out")
      close("""}.mkString(", ")""")
    case tp: StructType[_] =>
      tp.fields.zipWithIndex.foreach{case (field,i) =>
        emit(s"val field$i = $elem.${field._1}")
        bitsToString(s"fieldStr$i", s"field$i", field._2)
      }
      emit(s"val $lhs = List(" + List.tabulate(tp.fields.length){i => s"fieldStr$i"}.mkString(", ") + s""").mkString("; ")""")
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case op@StreamInNew(bus)  =>
      val name = lhs.name.map(_ + " (" + lhs.ctx + ")").getOrElse("defined at " + lhs.ctx)
      streamIns :+= lhs

      emitMemObject(lhs){
        open(src"""object $lhs extends StreamIn[${op.mT}]("$name", {str => """)
          bitsFromString("x", "str", op.mT)
          emit(src"x")
        close(src"})")
      }
      if (!bus.isInstanceOf[DRAMBus[_]]) emit(src"$lhs.initMem()")

    case op@StreamOutNew(bus) =>
      val name = lhs.name.map(_ + " (" +lhs.ctx + ")").getOrElse("defined at " + lhs.ctx)
      streamOuts :+= lhs

      emitMemObject(lhs){
        open(src"""object $lhs extends StreamOut[${op.mT}]("$name", {elem => """)
          bitsToString("x", "elem", op.mT)
          emit(src"x")
        close("})")
      }
      if (!bus.isInstanceOf[DRAMBus[_]]) emit(src"$lhs.initMem()")

    case op@StreamWrite(strm, data, en) => emit(src"val $lhs = if ($en) $strm.enqueue($data)")
    case op@StreamRead(strm, en) => emit(src"val $lhs = if ($en && $strm.nonEmpty) $strm.dequeue() else ${invalid(op.mT)}")

    case op@BankedStreamRead(strm, ens) =>
      open(src"val $lhs = {")
      ens.zipWithIndex.foreach{case (en,i) =>
        emit(src"val a$i = if ($en && $strm.nonEmpty) $strm.dequeue() else ${invalid(op.mT)}")
      }
      emit(src"Array[${op.mT}](" + ens.indices.map{i => src"a$i"}.mkString(", ") + ")")
      close("}")

    case BankedStreamWrite(strm, data, ens) =>
      open(src"val $lhs = {")
      ens.zipWithIndex.foreach{case (en,i) =>
        emit(src"if ($en) $strm.enqueue(${data(i)})")
      }
      close("}")

    case op@BufferedOutNew(dims, bus) =>
      bufferedOuts :+= lhs

      val name = lhs.name.map(_ + " (" +lhs.ctx + ")").getOrElse("defined at " + lhs.ctx)
      val size = dims.map(quote).mkString("*")

      emitMemObject(lhs){
        open(src"""object $lhs extends BufferedOut[${op.mT}]("$name", $size, ${invalid(op.mT)}, {elem => """)
          bitsToString("x", "elem", op.mT)
          emit(src"x")
        close("})")
      }

      emit(src"$lhs.initMem()")


    case op@BufferedOutWrite(buffer,data,inds,en) =>
      val dims = stagedDimsOf(buffer)
      open(src"val $lhs = {")
        oobUpdate(op.mT,buffer,lhs,inds){ emit(src"if ($en) $buffer.update(${flattenAddress(dims,inds,None)}, $data)") }
      close("}")

    case _ => super.emitNode(lhs, rhs)
  }

}
