package spatial.codegen.simgen

import argon.codegen.Codegen
import sys.process._
import scala.language.postfixOps
import argon.codegen.FileDependencies
import spatial.SpatialConfig

trait SimCodegen extends Codegen with FileDependencies {

  import IR._
  override val name = "Sim Codegen"
  override val lang: String = "sim"
  override val ext: String = "scala"
  var hw = false

  //RUBEN TODO: We dont support wildcard yet
  dependencies ::= DirDep("simgen", "", "files_list")

  override protected def emitBlock(b: Block[_]): Unit = {
    visitBlock(b)
    if (!hw) emit(src"${b.result}")
  }

  def latencyOf(rhs: Op[_]) = rhs match {
    case _ => 1
  }

  def delay(lhs: Sym[_], rhs: Op[_], func: String) = {
    val name = nameOf(lhs).getOrElse(quote(lhs))
    if (hw) {
      emit(src"""val ${lhs}_reg = Delay("$name", ${latencyOf(rhs)}, () => $func, log = ${nameOf(lhs).isDefined})""")
      emit(src"val $lhs = ${lhs}_reg.value")
    }
    else {
      emit(src"val $lhs = $func // $name")
    }
  }

}
