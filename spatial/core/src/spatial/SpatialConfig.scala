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
  s-in-pcu = 10
  s-out-pcu = 10
  v-in-pcu = 4
  v-out-pcu = 1
  comp = 10
  s-in-pmu = 10
  s-out-pmu = 10
  v-in-pmu = 4
  v-out-pmu = 1
  rw = 10
  lanes = 16
}
  """)

  case class PlasticineConf(
    sInPCU: Int,
    sOutPCU:Int,
    vInPCU: Int,
    vOutPCU: Int,
    comp: Int,
    sInPMU: Int,
    sOutPMU:Int,
    vInPMU: Int,
    vOutPMU: Int,
    rw: Int,
    mems: Int,
    lanes: Int
  )

  val mergedPlasticineConf = ConfigFactory.load().withFallback(defaultPlasticine).resolve()
  val plasticineConf = loadConfig[PlasticineConf](mergedPlasticineConf, "plasticine").right.get

  // Plasticine limits TODO: move to somewhere else?
  var sIn_PCU: Int = plasticineConf.sInPCU
  var sOut_PCU: Int = plasticineConf.sOutPCU
  var vIn_PCU: Int = plasticineConf.vInPCU
  var vOut_PCU: Int = plasticineConf.vOutPCU
  var stages: Int = plasticineConf.comp
  var sIn_PMU: Int = plasticineConf.sInPMU
  var sOut_PMU: Int = plasticineConf.sOutPMU
  var vIn_PMU: Int = plasticineConf.vInPMU
  var vOut_PMU: Int = plasticineConf.vOutPMU
  var readWrite: Int = plasticineConf.rw
  var lanes: Int = plasticineConf.lanes
}
