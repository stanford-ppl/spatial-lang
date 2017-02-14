package spatial.codegen.scalagen

import spatial.api.{DRAMExp, UnrolledExp}

trait ScalaGenDRAM extends ScalaGenSRAM {
  val IR: DRAMExp
  import IR._

  override protected def remap(tp: Staged[_]): String = tp match {
    case tp: DRAMType[_] => src"Array[${tp.child}]"
    case _ => super.remap(tp)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case op@DRAMNew(dims) => emit(src"""val $lhs = new Array[${op.mA}](${dims.map(quote).mkString("*")})""")
    case Gather(dram, local, addrs, ctr, i)  =>
      emit(src"/** BEGIN GATHER $lhs **/")
      open(src"val $lhs = {")
      open(src"$ctr.foreach{case (is,vs) => is.zip(vs).foreach{case ($i,v) => if (v) {")
      emit(src"$local.update($i, $dram.apply($addrs.apply($i)) )")
      close("}}}")
      close("}")
      emit(src"/** END GATHER $lhs **/")

    case Scatter(dram, local, addrs, ctr, i) =>
      emit(src"/** BEGIN SCATTER $lhs **/")
      open(src"val $lhs = {")
      open(src"$ctr.foreach{case (is,vs) => is.zip(vs).foreach{case ($i,v) => if (v) {")
      emit(src"$dram.update($addrs.apply($i), $local.apply($i))")
      close("}}}")
      close("}")
      emit(src"/** END SCATTER $lhs **/")

    case BurstLoad(dram, fifo, ofs, ctr, i)  =>
      emit(src"/** BEGIN BURST LOAD $lhs **/")
      open(src"val $lhs = {")
      open(src"$ctr.foreach{case (is,vs) => is.zip(vs).foreach{case ($i,v) => if (v) {")
      emit(src"$fifo.enqueue( $dram.apply($ofs + $i) )")
      close("}}}")
      close("}")
      emit(src"/** END BURST LOAD $lhs **/")

    case BurstStore(dram, fifo, ofs, ctr, i) =>
      emit(src"/** BEGIN BURST STORE $lhs **/")
      open(src"val $lhs = {")
      open(src"$ctr.foreach{case (is,vs) => is.zip(vs).foreach{case ($i,v) => if (v) {")
      emit(src"$dram.update($ofs + $i, $fifo.dequeue() )")
      close("}}}")
      close("}")
      emit(src"/** END BURST STORE $lhs **/")

    case _ => super.emitNode(lhs, rhs)
  }

}
