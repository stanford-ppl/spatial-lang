package spatial

import argon.core.Reporting
import com.typesafe.config.ConfigFactory
import pureconfig._

object SpatialConfig extends Reporting {
  import argon.Config._

  val defaultSpatial = ConfigFactory.parseString("""
spatial {
  fpga = "Default"
  sim = true
  synth = false
  pir = false
  dse = false
  dot = false
  retiming = false
  splitting = false
  arch-dse = false
  naming = false
  tree = false
}
""")

  case class SpatialConf(
    fpga: String,
    sim: Boolean,
    synth: Boolean,
    pir: Boolean,    
    dse: Boolean,
    dot: Boolean,
    retiming: Boolean,
    splitting: Boolean,
    archDSE: Boolean,
    naming: Boolean,
    tree: Boolean
  )

  val mergedSpatialConf = ConfigFactory.load().withFallback(defaultSpatial).resolve()
  val spatialConf = loadConfig[SpatialConf](mergedSpatialConf, "spatial").right.get

  var targetName: String = spatialConf.fpga

  var enableDSE: Boolean = spatialConf.dse
  var enableDot: Boolean = spatialConf.dot

  var enableSim: Boolean = spatialConf.sim
  var enableSynth: Boolean = spatialConf.synth
  var enablePIR: Boolean = spatialConf.pir

  var enableRetiming: Boolean = spatialConf.retiming



  var enableSplitting: Boolean = spatialConf.splitting
  var enableArchDSE: Boolean = spatialConf.archDSE
  var enableNaming: Boolean = spatialConf.naming
  var enableTree: Boolean = spatialConf.tree



  val defaultPlasticine =  ConfigFactory.parseString("""
plasticine {
  s-in = 10
  s-out = 10
  v-in = 4
  v-out = 1
  comp = 10
  rw = 10
  lanes = 16
}
  """)

  case class PlasticineConf(
    sIn: Int,
    sOut:Int,
    vIn: Int,
    vOut: Int,
    comp: Int,
    rw: Int,
    mems: Int,
    lanes: Int
  )

  val mergedPlasticineConf = ConfigFactory.load().withFallback(defaultPlasticine).resolve()
  val plasticineConf = loadConfig[PlasticineConf](mergedPlasticineConf, "plasticine").right.get

  // Plasticine limits TODO: move to somewhere else?
  var sIn: Int = plasticineConf.sIn
  var sOut: Int = plasticineConf.sOut
  var vIn: Int = plasticineConf.vIn
  var vOut: Int = plasticineConf.vOut
  var stages: Int = plasticineConf.comp
  var readWrite: Int = plasticineConf.rw
  var lanes: Int = plasticineConf.lanes
}
