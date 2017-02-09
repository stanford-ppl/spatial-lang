package spatial

import argon.core.Reporting
import spatial.codegen.targets._

object SpatialConfig extends Reporting {
  import argon.Config._

  lazy val HOME = sys.env("SPATIAL_HOME")

  var targetName: String = getProperty("spatial.fpga", "Default")
  var target: FPGATarget = Targets.targets.find(_.name == targetName).getOrElse{
    warn("No defined target found for " + targetName + ". Using Default.")
    DefaultTarget
  }

  var enableDSE: Boolean = getProperty("spatial.dse", "false") == "true"
  var enableScala: Boolean = getProperty("spatial.scala", "false") == "true"
  var enableChisel: Boolean = getProperty("spatial.chisel", "false") == "true"
  var enableCpp: Boolean = getProperty("spatial.cpp", "false") == "true"
  var enableNaming: Boolean = getProperty("spatial.naming", "false") == "true"
  var enableTree: Boolean = getProperty("spatial.tree", "false") == "true"
  var multifile: Int = getProperty("spatial.multifile", "0").toInt
}