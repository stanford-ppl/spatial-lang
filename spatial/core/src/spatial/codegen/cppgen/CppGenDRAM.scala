package spatial.codegen.cppgen

import argon.internals._
import spatial.compiler._
import spatial.metadata._
import spatial.nodes._
import spatial.SpatialConfig


trait CppGenDRAM extends CppGenSRAM {

  override def quote(s: Exp[_]): String = {
    if (SpatialConfig.enableNaming) {
      s match {
        case lhs: Sym[_] =>
          lhs match {
            case Def(e: DRAMNew[_,_])=> s"""x${lhs.id}_${lhs.name.getOrElse("dram")}"""
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

  override protected def remap(tp: Type[_]): String = tp match {
    // case tp: DRAMType[_] => src"DRAM"
    case _ => super.remap(tp)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case op@DRAMNew(dims,zero) =>
      val rawtype = 
      emit(src"""uint64_t ${lhs} = c1->malloc(sizeof(${remapIntType(lhs.tp.typeArguments.head)}) * ${dims.map(quote).mkString("*")});""")
      emit(src"c1->setArg(${argMapping(lhs)._2}, $lhs, false); // (memstream in: ${argMapping(lhs)._2}, out: ${{argMapping(lhs)._3}})")
      emit(src"""printf("Allocate mem of size ${dims.map(quote).mkString("*")} at %p\n", (void*)${lhs});""")
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
