package spatial.codegen.cppgen

import argon.codegen.cppgen.CppCodegen
import argon.core._
import spatial.aliases._
import spatial.nodes._

trait CppGenDebugging extends CppCodegen {

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case AssertIf(en, cond, m) => 
    	val str = src"""${m.getOrElse("API assert failed with no message provided")}"""
    	emit(src"""if ($en) { ASSERT($cond, "\n=================\n${str.replace("\"","'")}\n=================\n"); }""")
    case PrintIf(en,x)         => emit(src"""if ($en) { std::cout << $x; }""")
    case PrintlnIf(en,x)       => emit(src"""if ($en) { std::cout << $x << std::endl; }""")
    case BreakpointIf(en)       => ()
    case ExitIf(en)       => ()      
    case _ => super.emitNode(lhs, rhs)
  }

}
