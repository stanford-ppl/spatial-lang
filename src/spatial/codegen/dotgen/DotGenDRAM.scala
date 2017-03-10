package spatial.codegen.dotgen

import spatial.SpatialConfig
import spatial.SpatialExp

trait DotGenDRAM extends DotGenSRAM {
  val IR: SpatialExp
  import IR._

  override protected def remap(tp: Staged[_]): String = tp match {
    case tp: DRAMType[_] => src"Array[${tp.child}]"
    case _ => super.remap(tp)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case op@DRAMNew(dims) => 
    case Gather(dram, local, addrs, ctr, i)  =>
    case Scatter(dram, local, addrs, ctr, i) =>
    case BurstLoad(dram, fifo, ofs, ctr, i)  =>
    case BurstStore(dram, fifo, ofs, ctr, i) =>
    case _ => super.emitNode(lhs, rhs)
  }


  override protected def emitFileFooter() {
    super.emitFileFooter()
  }

}
