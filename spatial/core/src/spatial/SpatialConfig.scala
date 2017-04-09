package spatial

import argon.core.Reporting
import com.typesafe.config.ConfigFactory
import pureconfig._

object SpatialConfig extends Reporting {
  import argon.Config._

  val defaultSpatial = ConfigFactory.parseString("""
spatial {
  fpga = "Default"
  platform-target = "scala"
  dse = false
  dot = false
  splitting = false
  arch-dse = false
  naming = false
  tree = false
  multifile = 0 
}
""")

  case class SpatialConf(
    fpga: String,
    platformTarget: String,
    dse: Boolean,
    dot: Boolean,
    splitting: Boolean,
    archDSE: Boolean,
    naming: Boolean,
    tree: Boolean,
    multifile: Int
  )

  val mergedSpatialConf = ConfigFactory.load().withFallback(defaultSpatial).resolve()
  val spatialConf = loadConfig[SpatialConf](mergedSpatialConf, "spatial").right.get

  var targetName: String = spatialConf.fpga

  var enableDSE: Boolean = spatialConf.dse
  var enableDot: Boolean = spatialConf.dot

  var enableScala: Boolean = false
  var enableChisel: Boolean = false
  var enableCpp: Boolean = false
  var enablePIR: Boolean = false

  def switchTarget(target:String) = {
    enableScala = false
    enableChisel = false
    enableCpp = false
    enablePIR = false

    target match {
      case "scala" => enableScala = true
      case "chisel" => enableChisel = true
      case "cpp" => enableCpp = true
      case "pir" => enablePIR = true
    }
  }

  switchTarget(spatialConf.platformTarget)

  var enableSplitting: Boolean = spatialConf.splitting
  var enableArchDSE: Boolean = spatialConf.archDSE
  var enableNaming: Boolean = spatialConf.naming
  var enableTree: Boolean = spatialConf.tree
  var multifile: Int = spatialConf.multifile



  val defaultPlasticine =  ConfigFactory.parseString("""
plasticine {
  s-in = 8
  sbus = 4
  v-in = 4
  v-out = 1
  comp = 8
  rw = 4
  mems = 4
}
  """)

  case class PlasticineConf(
    sIn: Int,
    sbus:Int,
    vIn: Int,
    vOut: Int,
    comp: Int,
    rw: Int,
    mems: Int
  )

  val mergedPlasticineConf = ConfigFactory.load().withFallback(defaultPlasticine).resolve()
  val plasticineConf = loadConfig[PlasticineConf](mergedPlasticineConf, "plasticine").right.get

  // Plasticine limits TODO: move to somewhere else?
  var sIn: Int = plasticineConf.sIn
  var sbus: Int = plasticineConf.sbus
  var vIn: Int = plasticineConf.vIn
  var vOut: Int = plasticineConf.vOut
  var comp: Int = plasticineConf.comp
  var readWrite: Int = plasticineConf.rw
  var mems: Int = plasticineConf.mems

}
