package spatial

import spatial.codegen.targets._

object SpatialConfig {
  import argon.Config._

  lazy val HOME = sys.env("SPATIAL_HOME")
  var target: FPGATarget = DefaultTarget

  var enableDSE: Boolean = getProperty("spatial.dse", "false") == "true"
}