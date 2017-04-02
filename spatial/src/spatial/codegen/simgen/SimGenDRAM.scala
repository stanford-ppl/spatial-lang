package spatial.codegen.simgen

import spatial.api.DRAMExp

trait SimGenDRAM extends SimCodegen {
  val IR: DRAMExp
  import IR._

  override protected def remap(tp: Type[_]): String = tp match {
    case tp: DRAMType[_] => src"Array[${tp.child}]"
    case _ => super.remap(tp)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case op@DRAMNew(dims) =>
      emit(src"""val $lhs = new Array[${op.mA}](${dims.map(quote).mkString("*")})""")

    case GetDRAMAddress(dram) =>
      emit(src"val $lhs = 0")

    case FringeDenseLoad(dram,cmdStream,dataStream) =>
      open(src"val $lhs = $cmdStream.foreach{cmd => ")
        open(src"for (i <- cmd.offset until cmd.offset+cmd.size) {")
          emit(src"$dataStream.enqueue($dram.apply(i))")
        close("}")
      close("}")
      emit(src"$cmdStream.clear()")

    case FringeDenseStore(dram,cmdStream,dataStream,ackStream) =>
      open(src"val $lhs = $cmdStream.foreach{cmd => ")
        open(src"for (i <- cmd.offset until cmd.offset+cmd.size) {")
          emit(src"val data = $dataStream.dequeue()")
          emit(src"if (data._2) $dram(i) = data._1")
        close("}")
        emit(src"$ackStream.enqueue(true)")
      close("}")
      emit(src"$cmdStream.clear()")

    case FringeSparseLoad(dram,addrStream,dataStream) =>
      open(src"val $lhs = $addrStream.foreach{addr => ")
        emit(src"$dataStream.enqueue( $dram(addr) )")
      close("}")
      emit(src"$addrStream.clear()")

    case FringeSparseStore(dram,cmdStream,ackStream) =>
      open(src"val $lhs = $cmdStream.foreach{cmd => ")
        emit(src"$dram(cmd._2) = cmd._1 ")
        emit(src"$ackStream.enqueue(true)")
      close("}")
      emit(src"$cmdStream.clear()")


    case _ => super.emitNode(lhs, rhs)
  }

}
