package spatial.codegen.cppgen

import spatial.api.DRAMExp
import spatial.SpatialConfig
import spatial.analysis.SpatialMetadataExp
import scala.collection.mutable.HashMap


trait CppGenDRAM extends CppGenSRAM {
  val IR: DRAMExp with SpatialMetadataExp
  import IR._

  var dramMap = HashMap[String, (String, String)]() // Map for tracking defs of nodes and if they get redeffed anywhere, we map it to a suffix

  override def quote(s: Exp[_]): String = {
    if (SpatialConfig.enableNaming) {
      s match {
        case lhs: Sym[_] =>
          lhs match {
            case Def(e: DRAMNew[_])=> s"x${lhs.id}_dram" 
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
    // case tp: DRAMType[_] => src"DRAM"
    case _ => super.remap(tp)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case op@DRAMNew(dims) => 
      val length = dims.map{i => src"${i}"}.mkString("*")
      if (dramMap.size == 0)  {
        dramMap += (src"$lhs" -> ("0", length))
      } else if (!dramMap.contains(src"$lhs")) {
        val start = dramMap.values.map{ _._2 }.mkString{" + "}
        dramMap += (src"$lhs" -> (start, length))
      } else {
        log(s"dram $lhs used multiple times")
      }

      emit(src"""uint64_t ${lhs} = c1->malloc(2 * ${dims.map(quote).mkString("*")});""")
      // emit(src"""uint64_t ${lhs} = (uint64_t) ${lhs}_void;""")


    // case Gather(dram, local, addrs, ctr, i)  => emit("// Do what?")
    // case Scatter(dram, local, addrs, ctr, i) => emit("// Do what?")
    // case BurstLoad(dram, fifo, ofs, ctr, i)  => emit("//found load")
    // case BurstStore(dram, fifo, ofs, ctr, i) => emit("//found store")
    case _ => super.emitNode(lhs, rhs)
  }

  override protected def emitFileFooter() {
    super.emitFileFooter()
  }


}
