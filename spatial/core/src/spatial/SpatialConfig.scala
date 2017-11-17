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
    scuSin:Int,
    scuSout:Int,
    scuStages:Int,
    scuRegs:Int,
    pcuVin:Int,
    pcuVout:Int,
    pcuSin:Int,
    pcuSout:Int,
    var pcuStages:Int, // Can be reset by PIRDSE
    pcuRegs:Int,
    pmuVin:Int,
    pmuVout:Int,
    pmuSin:Int,
    pmuSout:Int,
    var pmuStages:Int,
    pmuRegs:Int,
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
  var pirsrc: Option[String] = None 

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

  override def printer():String = {
    val vars = this.getClass.getDeclaredFields
    var cmd = ""
    for(v <- vars){
      try { 
        cmd = cmd + " --" + v.getName() + "=" + v.get(this)
      } catch {
        case e: java.lang.IllegalAccessException => 
          v.setAccessible(true)
          cmd = cmd + " --" + v.getName() + "=" + v.get(this)
          v.setAccessible(false)
        case _: Throwable => 
      }
    }
    cmd
  }

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
  scu-sin = 10
  scu-sout = 2
  scu-stages = 5
  scu-regs = 16
  pcu-vin = 4
  pcu-vout = 2
  pcu-sin = 6
  pcu-sout = 2
  pcu-stages = 7
  pcu-regs = 16
  pmu-vin = 4
  pmu-vout = 1
  pmu-sin = 4
  pmu-sout = 1
  pmu-stages = 0
  pmu-regs = 16
  lanes = 16
  word-width = 32
}
  """)

    val mergedPlasticineConf = ConfigFactory.load().withFallback(defaultPlasticine).resolve()

    plasticineSpec = loadConfig[PlasticineConf](mergedPlasticineConf, "plasticine") match {
      case Right(plasticineConf) => plasticineConf
      case Left(failures) =>
        throw new Exception(s"Unable to read Plasticine configuration ${failures}")
        sys.exit(-1)
    }
  }

}
