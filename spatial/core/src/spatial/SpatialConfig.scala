package spatial

import com.typesafe.config.ConfigFactory
import pureconfig._
import argon.util.Report
import spatial.targets.FPGATarget

class SpatialConfig extends argon.core.Config {

  case class SpatialConf(
    fpga: String,
    sim: Boolean,
    synth: Boolean,
    interpret: Boolean,
    inputs: Seq[String],
    pir: Boolean,
    dse: Boolean,
    dot: Boolean,
    retiming: Boolean,
    splitting: Boolean,
    archDSE: Boolean,
    naming: Boolean,
    tree: Boolean,
    bbs: Boolean    // Use basic blocks?
  )

  case class PlasticineConf(
    scu_sin:Int,
    scu_sout:Int,
    scu_stages:Int,
    scu_regs:Int,
    pcu_vin:Int,
    pcu_vout:Int,
    pcu_sin:Int,
    pcu_sout:Int,
    pcu_stages:Int,
    pcu_regs:Int,
    pmu_vin:Int,
    pmu_vout:Int,
    pmu_sin:Int,
    pmu_sout:Int,
    pmu_stages:Int,
    pmu_regs:Int,
    lanes: Int,
    wordWidth: Int
  )

  var useBasicBlocks: Boolean = false

  var targetName: String = _
  var target: FPGATarget = targets.DefaultTarget

  var enableDSE: Boolean = _
  var heuristicDSE: Boolean = true
  var bruteForceDSE: Boolean = false
  var experimentDSE: Boolean = false

  var enableDot: Boolean = _

  //Interpreter 
  var inputs: Array[String] = Array()
  var enableInterpret: Boolean = _

  var enableSim: Boolean = _
  var enableSynth: Boolean = _
  var enablePIR: Boolean = _
  var enablePIRSim: Boolean = false
  lazy val PIR_HOME: String = sys.env.getOrElse("PIR_HOME", {Report.error("Please set the PIR_HOME environment variable."); sys.exit()})
  var pirsrc: String = s"$PIR_HOME/pir/apps/src"

  var enableRetiming: Boolean = _

  var enableSplitting: Boolean = _
  var enableArchDSE: Boolean = _

  var enableSyncMem: Boolean = _
  var enableInstrumentation: Boolean = _
  var useCheapFifos: Boolean = _
  var enableTree: Boolean = _

  def enableBufferCoalescing: Boolean = !enablePIR
  def removeParallelNodes: Boolean = enablePIR
  def rewriteLUTs: Boolean = enablePIR

  var plasticineSpec:PlasticineConf = _

  var threads: Int = 8

  override def init(): Unit = {
    super.init()

    val defaultSpatial = ConfigFactory.parseString("""
spatial {
  fpga = "Default"
  interpret = false
  sim = true
  inputs = ["0", "1", "2", "3", "4"]
  synth = false
  pir = false
  dse = false
  dot = false
  retiming = false
  splitting = false
  arch-dse = false
  naming = false
  tree = true
  bbs = false
}
""")

    val mergedSpatialConf = ConfigFactory.load().withFallback(defaultSpatial).resolve()
    loadConfig[SpatialConf](mergedSpatialConf, "spatial") match {
      case Right(spatialConf) =>
        //targetName = spatialConf.fpga
        enableDSE = spatialConf.dse
        enableDot = spatialConf.dot

        inputs = spatialConf.inputs.toArray
        enableInterpret = spatialConf.interpret
        enableSim = spatialConf.sim
        enableSynth = spatialConf.synth
        enablePIR = spatialConf.pir

        enableRetiming = spatialConf.retiming

        enableSplitting = spatialConf.splitting
        enableArchDSE = spatialConf.archDSE
        enableNaming = spatialConf.naming
        enableTree = spatialConf.tree

        useBasicBlocks = spatialConf.bbs

      case Left(failures) =>
        Report.error("Unable to read spatial configuration")
        Report.error(failures.head.description)
        failures.tail.foreach{x => Report.error(x.description) }
        sys.exit(-1)
    }

    val defaultPlasticine =  ConfigFactory.parseString("""
plasticine {
  scu_sin = 10
  scu_sout = 2
  scu_stages = 5
  scu_regs = 16
  pcu_vin = 4
  pcu_vout = 2
  pcu_sin = 6
  pcu_sout = 2
  pcu_stages = 7
  pcu_regs = 16
  pmu_vin = 4
  pmu_vout = 1
  pmu_sin = 4
  pmu_sout = 1
  pmu_stages = 0
  pmu_regs = 16
  lanes = 16
  wordWidth = 32
}
  """)

    val mergedPlasticineConf = ConfigFactory.load().withFallback(defaultPlasticine).resolve()

    plasticineSpec = loadConfig[PlasticineConf](mergedPlasticineConf, "plasticine") match {
      case Right(plasticineConf) => plasticineConf
      case Left(failures) =>
        throw new Exception(s"Unable to read Plasticine configuration")
        sys.exit(-1)
    }
  }

}
