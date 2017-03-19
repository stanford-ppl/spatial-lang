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
    val cppResourcesPath = s"${sys.env("SPATIAL_HOME")}/src/spatial/codegen/cppgen/resources"

    if (IR.target.name == "AWS_F1") {
      s"""cp -r $cppResourcesPath/fringeAWS ${out}""".!
    } else {
      s"""cp -r $cppResourcesPath/fringeSW ${out}""".!
    }

    super.copyDependencies(out)
  }

}