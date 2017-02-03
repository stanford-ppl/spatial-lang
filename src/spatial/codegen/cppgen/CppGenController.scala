package spatial.codegen.cppgen

import argon.codegen.cppgen.CppCodegen
import spatial.api.ControllerExp
import spatial.SpatialConfig
import spatial.analysis.SpatialMetadataExp

trait CppGenController extends CppCodegen {
  val IR: ControllerExp with SpatialMetadataExp
  import IR._


  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case Hwblock(func) =>
      // Skip everything inside
      emit(s"uint64_t Top_cycles = 0;")
      emit(s"Interface_t interface;")
      emit(s"interface.cycles = &Top_cycles;")
      toggleEn()
      emitBlock(func)
      toggleEn()
      emit(s"gettimeofday(&t1, 0);")
      emit(s"Top_run(&interface); // kernel_x123(engine, &interface);")
      emit(s"gettimeofday(&t2, 0);")
      emit(s"double elapsed = (t2.tv_sec-t1.tv_sec)*1000000 + t2.tv_usec-t1.tv_usec;")

    case _ => super.emitNode(lhs, rhs)
  }
}
