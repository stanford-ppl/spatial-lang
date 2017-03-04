package spatial.codegen.cppgen

import argon.codegen.cppgen.CppCodegen
import spatial.api.ControllerExp
import spatial.SpatialConfig
import spatial.analysis.SpatialMetadataExp

trait CppGenController extends CppCodegen {
  val IR: ControllerExp with SpatialMetadataExp
  import IR._


  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case Hwblock(func,isForever) =>
      // Skip everything inside
      toggleEn()
      emitBlock(func)
      toggleEn()
      emit(s"time_t tstart = time(0);")
      val memlist = if (setMems.length > 0) {s""", ${setMems.mkString(",")}"""} else ""
      emit(s"Top_run(&interface, ${setMems.length} ${memlist});")
      emit(s"time_t tend = time(0);")
      emit(s"double elapsed = difftime(tend, tstart);")
      emit(s"""std::cout << "Kernel done, test run time = " << elapsed << " ms" << std::endl;""")

    case _ => super.emitNode(lhs, rhs)
  }
}

   
