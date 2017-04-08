package spatial

import argon.core.Reporting

object SpatialConfig extends Reporting {
  import argon.Config._

  lazy val HOME = sys.env("SPATIAL_HOME")+"/spatial"

  var targetName: String = getProperty("spatial.fpga", "Default")

  var enableDSE: Boolean = getProperty("spatial.dse", "false").toBoolean
  var enableDot: Boolean = getProperty("spatial.dot", "false").toBoolean
  var enableScala: Boolean = getProperty("spatial.scala", "false").toBoolean
  var enableChisel: Boolean = getProperty("spatial.chisel", "false").toBoolean
  var enableCpp: Boolean = getProperty("spatial.cpp", "false").toBoolean
  var enablePIR: Boolean = getProperty("spatial.pir", "false").toBoolean
  var enableSplitting: Boolean = getProperty("spatial.splitting", "false").toBoolean
  var enableArchDSE: Boolean = getProperty("spatial.archDSE", "false").toBoolean
  var enableNaming: Boolean = getProperty("spatial.naming", "false").toBoolean
  var enableTree: Boolean = getProperty("spatial.tree", "false").toBoolean
  var multifile: Int = getProperty("spatial.multifile", "0").toInt

  // Plasticine limits TODO: move to somewhere else?
  var sIn: Int = getProperty("plasticine.sIn", "8").toInt
  var sbus: Int = getProperty("plasticine.sbus", "4").toInt
  var vIn: Int = getProperty("plasticine.vIn", "4").toInt
  var vOut: Int = getProperty("plasticine.vOut", "1").toInt
  var comp: Int = getProperty("plasticine.comp", "8").toInt
  var readWrite: Int = getProperty("plasticine.rw", "4").toInt
  var mems: Int = getProperty("plasticine.mems", "4").toInt

}
