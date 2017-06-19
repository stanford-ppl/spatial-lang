package spatial.codegen.cppgen

import argon.codegen.cppgen.CppCodegen
import argon.core._
import spatial.aliases._
import spatial.nodes._

trait CppGenController extends CppCodegen {

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case Hwblock(func,isForever) =>
      // Skip everything inside
      toggleEn()
      emitBlock(func)
      toggleEn()
      emit(s"time_t tstart = time(0);")
      val memlist = if (setMems.nonEmpty) {s""", ${setMems.mkString(",")}"""} else ""
      emit(s"c1->run();")
      emit(s"time_t tend = time(0);")
      emit(s"double elapsed = difftime(tend, tstart);")
      emit(s"""std::cout << "Kernel done, test run time = " << elapsed << " ms" << std::endl;""")

    case _ => super.emitNode(lhs, rhs)
  }
}

   
