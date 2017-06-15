package spatial.codegen.cppgen

import argon.codegen.cppgen.CppCodegen
import argon.internals._
import spatial.SpatialConfig


trait CppGenFringeCopy extends CppCodegen {

  override def copyDependencies(out: String): Unit = {
    val cppResourcesPath = "cppgen"

    if (SpatialConfig.target.name == "AWS_F1") {
      dependencies ::= DirDep(cppResourcesPath, "fringeAWS")
    } else {
      dependencies ::= DirDep(cppResourcesPath, "fringeSW")
    }

    super.copyDependencies(out)
  }

}