package spatial

import argon.core.Reporting
import spatial.targets._

object SpatialConfig extends Reporting {
  import argon.Config._

  lazy val HOME = sys.env("SPATIAL_HOME")

  var targetName: String = getProperty("spatial.fpga", "Default")

  var enableDSE: Boolean = getProperty("spatial.dse", "false") == "true"
  var enableScala: Boolean = getProperty("spatial.scala", "false") == "true"
  var enableChisel: Boolean = getProperty("spatial.chisel", "false") == "true"
  var enablePIR: Boolean = getProperty("spatial.pir", "false") == "true"
  var enableSplitting: Boolean = getProperty("spatial.splitting", "false") == "true"
  var enableArchDSE: Boolean = getProperty("spatial.archDSE", "false") == "true"
  var enableCpp: Boolean = getProperty("spatial.cpp", "false") == "true"
  var enableNaming: Boolean = getProperty("spatial.naming", "false") == "true"
  var enableTree: Boolean = getProperty("spatial.tree", "false") == "true"
  var multifile: Int = getProperty("spatial.multifile", "0").toInt

  // Plasticine limits TODO: move to somewhere else?
  var sIn: Int = getProperty("plasticine.sIn", "8").toInt
  var sbus: Int = getProperty("plasticine.sbus", "4").toInt
  var vIn: Int = getProperty("plasticine.vIn", "4").toInt
  var vOut: Int = getProperty("plasticine.vOut", "1").toInt
  var comp: Int = getProperty("plasticine.comp", "8").toInt
  var readWrite: Int = getProperty("plasticine.rw", "4").toInt
  var mems: Int = getProperty("plasticine.mems", "4").toInt
  var numLanes: Int = 16 

}
