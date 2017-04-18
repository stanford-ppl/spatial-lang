package spatial.codegen.cppgen

import argon.codegen.cppgen.CppCodegen
import spatial.SpatialExp
import spatial.api.DebuggingExp

trait CppGenDebugging extends CppCodegen {
  val IR: SpatialExp
  import IR._

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case AssertIf(en, cond, m) => emit(src"if ($en) { assert($cond); }")
    case PrintIf(en,x)         => emit(src"""if ($en) { std::cout << $x; }""")
    case PrintlnIf(en,x)       => emit(src"""if ($en) { std::cout << $x << std::endl; }""")
    case _ => super.emitNode(lhs, rhs)
  }

}
