package spatial.codegen.scalagen

import spatial.SpatialExp
import spatial.api.FIFOExp

trait ScalaGenFIFO extends ScalaGenMemories {
  val IR: SpatialExp
  import IR._

  override protected def remap(tp: Type[_]): String = tp match {
    case tp: FIFOType[_] => src"scala.collection.mutable.Queue[${tp.child}]"
    case _ => super.remap(tp)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case op@FIFONew(size)    => emitMem(lhs, src"$lhs = new scala.collection.mutable.Queue[${op.mT}] // size: $size")
    case FIFOEnq(fifo,v,en)  => emit(src"val $lhs = if ($en) $fifo.enqueue($v)")
    case FIFOEmpty(fifo)  => emit(src"val $lhs = $fifo.isEmpty")
    case FIFOAlmostEmpty(fifo)  => 
      // Find rPar
      val rPar = readersOf(fifo).map{ r => r.node match {
        case Def(ParFIFODeq(_,ens)) => ens.length
        case _ => 1
      }}.head
      emit(src"val $lhs = $fifo.size === $rPar")
    case FIFOAlmostFull(fifo)  => 
      val Def(FIFONew(size)) = fifo
      // Find wPar
      val wPar = writersOf(fifo).map{ r => r.node match {
        case Def(ParFIFOEnq(_,ens,_)) => ens.length
        case _ => 1
      }}.head
      emit(src"val $lhs = $fifo.size === ${size} - $wPar")
    case FIFONumel(fifo)  => emit(src"val $lhs = $fifo.size")
    case FIFOFull(fifo)  => 
      val Def(FIFONew(size)) = fifo 
      emit(src"val $lhs = ${fifo}.size >= $size ")
    case op@FIFODeq(fifo,en) => emit(src"val $lhs = if ($en && $fifo.nonEmpty) $fifo.dequeue() else ${invalid(op.mT)}")
    case _ => super.emitNode(lhs, rhs)
  }
}
