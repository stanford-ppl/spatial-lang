package spatial.codegen.cppgen

import argon.codegen.cppgen.CppCodegen
import spatial.SpatialExp
import spatial.SpatialConfig
import spatial.analysis.SpatialMetadataExp
import sys.process._
import scala.language.postfixOps

trait CppGenFringeCopy extends CppCodegen {
  val IR: SpatialExp with SpatialMetadataExp
  import IR._

  override def copyDependencies(out: String): Unit = {
    val cppResourcesPath = "cppgen"

    if (IR.target.name == "AWS_F1") {
      dependencies ::= DirDep(cppResourcesPath, "fringeAWS")
    } else {
      dependencies ::= DirDep(cppResourcesPath, "fringeSW")
    }

    super.copyDependencies(out)
  }

}