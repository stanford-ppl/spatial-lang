package spatial.codegen.cppgen

import spatial.api.DRAMExp
import spatial.SpatialConfig
import spatial.analysis.SpatialMetadataExp


trait CppGenDRAM extends CppGenSRAM {
  val IR: DRAMExp with SpatialMetadataExp
  import IR._

  var offchipMems: List[Sym[Any]] = List()

  override def quote(s: Exp[_]): String = {
    if (SpatialConfig.enableNaming) {
      s match {
        case lhs: Sym[_] =>
          val Op(rhs) = lhs
          rhs match {
            case e: Gather[_]=> 
              s"x${lhs.id}_gath"
            case e: Scatter[_] =>
              s"x${lhs.id}_scat"
            case e: BurstLoad[_] =>
              s"x${lhs.id}_load"
            case e: BurstStore[_] =>
              s"x${lhs.id}_store"
            case _ =>
              super.quote(s)
          }
        case _ =>
          super.quote(s)
      }
    } else {
      super.quote(s)
    }
  } 

  override protected def remap(tp: Staged[_]): String = tp match {
    case tp: DRAMType[_] => src"Array[${tp.child}]"
    case _ => super.remap(tp)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case op@DRAMNew(dims) => emit(src"""val $lhs = new Array[${op.mA}](${dims.map(quote).mkString("*")})""")
    case Gather(dram, local, addrs, ctr, i)  => emit("// Do what?")
    case Scatter(dram, local, addrs, ctr, i) => emit("// Do what?")
    case BurstLoad(dram, fifo, ofs, ctr, i)  => emit("// Do what?")
    case BurstStore(dram, fifo, ofs, ctr, i) => emit("// Do what?")
    case _ => super.emitNode(lhs, rhs)
  }


}
