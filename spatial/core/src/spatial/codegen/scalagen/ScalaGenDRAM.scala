package spatial.codegen.scalagen

import argon.core._
import org.virtualized.SourceContext
import spatial.aliases._
import spatial.nodes._

trait ScalaGenDRAM extends ScalaGenMemories {

  override protected def remap(tp: Type[_]): String = tp match {
    case tp: DRAMType[_] => src"Array[${tp.child}]"
    case _ => super.remap(tp)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case op@DRAMNew(dims,zero) =>
      val elementsPerBurst = spatialConfig.target.burstSize / op.bT.length
      open(src"val $lhs = {")
        emit(src"""Array.fill(${dims.map(quote).mkString("*")} + $elementsPerBurst)($zero)""") //${invalid(op.mA)})""")
      close("}")

    case GetDRAMAddress(dram) =>
      emit(src"val $lhs = 0")

    // Fringe templates expect byte-based addresses and sizes, while Scala gen expects word-based
    case e@FringeDenseLoad(dram,cmdStream,dataStream) =>
      val bytesPerWord = e.bT.length / 8 + (if (e.bT.length % 8 != 0) 1 else 0)
      open(src"val $lhs = $cmdStream.foreach{cmd => ")
        open(src"for (i <- cmd.offset until cmd.offset+cmd.size by $bytesPerWord) {")
          open(src"val data = {")
            oobApply(e.mT,dram, lhs, Nil){ emit(src"$dram.apply(i / $bytesPerWord)") }
          close("}")
          emit(src"$dataStream.enqueue(data)")
        close("}")
      close("}")
      emit(src"$cmdStream.clear()")

    case e@FringeDenseStore(dram,cmdStream,dataStream,ackStream) =>
      val bytesPerWord = e.bT.length / 8 + (if (e.bT.length % 8 != 0) 1 else 0)
      open(src"val $lhs = $cmdStream.foreach{cmd => ")
        open(src"for (i <- cmd.offset until cmd.offset+cmd.size by $bytesPerWord) {")
          emit(src"val data = $dataStream.dequeue()")
          oobUpdate(e.mT, dram, lhs, Nil){ emit(src"if (data._2) $dram(i / $bytesPerWord) = data._1") }
        close("}")
        emit(src"$ackStream.enqueue(true)")
      close("}")
      emit(src"$cmdStream.clear()")

    case e@FringeSparseLoad(dram,addrStream,dataStream) =>
      val bytesPerWord = e.bT.length / 8 + (if (e.bT.length % 8 != 0) 1 else 0)
      open(src"val $lhs = $addrStream.foreach{addr => ")
        open(src"val data = {")
          oobApply(e.mT, dram, lhs, Nil){ emit(src"$dram(addr / $bytesPerWord)") }
        close("}")
        emit(src"$dataStream.enqueue(data)")
      close("}")
      emit(src"$addrStream.clear()")

    case e@FringeSparseStore(dram,cmdStream,ackStream) =>
      val bytesPerWord = e.bT.length / 8 + (if (e.bT.length % 8 != 0) 1 else 0)
      open(src"val $lhs = $cmdStream.foreach{cmd => ")
        oobUpdate(e.mT,dram, lhs, Nil){ emit(src"$dram(cmd._2 / $bytesPerWord) = cmd._1 ") }
        emit(src"$ackStream.enqueue(true)")
      close("}")
      emit(src"$cmdStream.clear()")


    case _ => super.emitNode(lhs, rhs)
  }

}
