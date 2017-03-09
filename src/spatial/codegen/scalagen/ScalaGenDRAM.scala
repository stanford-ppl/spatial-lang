package spatial.codegen.scalagen

import spatial.api.DRAMExp

trait ScalaGenDRAM extends ScalaGenSRAM {
  val IR: DRAMExp
  import IR._

  override protected def remap(tp: Staged[_]): String = tp match {
    case tp: DRAMType[_] => src"Array[${tp.child}]"
    case _ => super.remap(tp)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case op@DRAMNew(dims) =>
      emit(src"""val $lhs = new Array[${op.mA}](${dims.map(quote).mkString("*")})""")

    case GetDRAMAddress(dram) =>
      emit(src"val $lhs = 0")

    // Fringe templates expect byte-based addresses and sizes, while Scala gen expects word-based
    case e@FringeDenseLoad(dram,cmdStream,dataStream) =>
      val bytesPerWord = e.bT.length / 8 + (if (e.bT.length % 8 != 0) 1 else 0)
      open(src"val $lhs = $cmdStream.foreach{cmd => ")
        open(src"for (i <- cmd.offset until cmd.offset+cmd.size by $bytesPerWord) {")
          emit(src"$dataStream.enqueue($dram.apply(i / $bytesPerWord))")
        close("}")
      close("}")
      emit(src"$cmdStream.clear()")

    case e@FringeDenseStore(dram,cmdStream,dataStream,ackStream) =>
      val bytesPerWord = e.bT.length / 8 + (if (e.bT.length % 8 != 0) 1 else 0)
      open(src"val $lhs = $cmdStream.foreach{cmd => ")
        open(src"for (i <- cmd.offset until cmd.offset+cmd.size by $bytesPerWord) {")
          emit(src"val data = $dataStream.dequeue()")
          emit(src"if (data._2) $dram(i / $bytesPerWord) = data._1")
        close("}")
        emit(src"$ackStream.enqueue(true)")
      close("}")
      emit(src"$cmdStream.clear()")

    case e@FringeSparseLoad(dram,addrStream,dataStream) =>
      val bytesPerWord = e.bT.length / 8 + (if (e.bT.length % 8 != 0) 1 else 0)
      open(src"val $lhs = $addrStream.foreach{addr => ")
        emit(src"$dataStream.enqueue( $dram(addr / $bytesPerWord) )")
      close("}")
      emit(src"$addrStream.clear()")

    case e@FringeSparseStore(dram,cmdStream,ackStream) =>
      val bytesPerWord = e.bT.length / 8 + (if (e.bT.length % 8 != 0) 1 else 0)
      open(src"val $lhs = $cmdStream.foreach{cmd => ")
        emit(src"$dram(cmd._2 / $bytesPerWord) = cmd._1 ")
        emit(src"$ackStream.enqueue(true)")
      close("}")
      emit(src"$cmdStream.clear()")


    case _ => super.emitNode(lhs, rhs)
  }

}
