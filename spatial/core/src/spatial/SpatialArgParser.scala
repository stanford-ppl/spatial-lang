package spatial

import spatial.aliases._

import argon.ArgonArgParser
import argon.util.Report._

class SpatialArgParser(spatialConfig: SpatialConfig) extends ArgonArgParser(spatialConfig) {

  override def scriptName = "spatial"
  override def description = "CLI for spatial"

  parser.opt[Unit]("synth").action{ (_,_) =>
    spatialConfig.enableSynth = true
    spatialConfig.enableSim = false
    spatialConfig.enableInterpret = false
  }.text("enable codegen to chisel + cpp (Synthesis) (disable sim) [false]")

  parser.opt[Unit]("retiming").action{ (_,_) =>
    spatialConfig.enableRetiming = true
  }.text("enable retiming [false]")

  parser.opt[Unit]("retime").action{ (_,_) =>
    spatialConfig.enableRetiming = true
  }.text("enable retiming [false]")

  parser.opt[Unit]("sim").action { (_,_) =>
    spatialConfig.enableSim = true
    spatialConfig.enableInterpret = false
    spatialConfig.enableSynth = false
  }.text("enable codegen to Scala (Simulation) (disable synth) [true]")

  parser.opt[Unit]("interpreter").action { (_,_) =>
    spatialConfig.enableInterpret = true
    spatialConfig.enableSim = false
    spatialConfig.enableSynth = false
  }.text("enable interpreter")

  parser.arg[String]("args...").unbounded().optional().action( (x, _) => {
    spatialConfig.inputs = Array(x) ++ spatialConfig.inputs
  }).text("args inputs for the interpreter")
  
  parser.opt[String]("fpga").action( (x,_) =>
    spatialConfig.targetName = x
  ).text("Set name of FPGA target [Default]")

  parser.opt[Unit]("dse").action( (_,_) =>
    spatialConfig.enableDSE = true
  ).text("enables design space exploration [false]")

  parser.opt[Unit]("bruteforce").action { (_, _) =>
    spatialConfig.enableDSE = true
    spatialConfig.heuristicDSE = false
    spatialConfig.bruteForceDSE = true
    spatialConfig.experimentDSE = false
  }
  parser.opt[Unit]("heuristic").action { (_, _) =>
    spatialConfig.enableDSE = true
    spatialConfig.heuristicDSE = true
    spatialConfig.bruteForceDSE = false
    spatialConfig.experimentDSE = false
  }
  parser.opt[Unit]("experiment").action { (_, _) =>
    spatialConfig.enableDSE = true
    spatialConfig.heuristicDSE = true
    spatialConfig.bruteForceDSE = false
    spatialConfig.experimentDSE = true
  }

  parser.opt[Unit]("retiming").action( (_,_) =>
    spatialConfig.enableRetiming = true
  ).text("enables inner pipeline retiming [false]")

  parser.opt[Unit]("retime").action( (_,_) =>
    spatialConfig.enableRetiming = true
  ).text("enables inner pipeline retiming [false]")

  parser.opt[Unit]("naming").action( (_,_) =>
    spatialConfig.enableNaming = true
  ).text("generates the debug name for all syms, rather than \"x${s.id}\" only'")

  parser.opt[Unit]("syncMem").action { (_,_) => // Must necessarily turn on retiming
    spatialConfig.enableSyncMem = true
    spatialConfig.enableRetiming = true
  }.text("Turns all SRAMs into fringe.SRAM (i.e. latched read addresses)")

  parser.opt[Unit]("instrumentation").action { (_,_) => // Must necessarily turn on retiming
    spatialConfig.enableInstrumentation = true
  }.text("Turns on counters for each loop to assist in balancing pipelines")

  parser.opt[Unit]("instrument").action { (_,_) => // Must necessarily turn on retiming
    spatialConfig.enableInstrumentation = true
  }.text("Turns on counters for each loop to assist in balancing pipelines")

  parser.opt[Unit]("cheapFifo").action { (_,_) => // Must necessarily turn on retiming
    spatialConfig.useCheapFifos = true
  }.text("Turns on cheap fifos where accesses must be multiples of each other and not have lane-enables")
  parser.opt[Unit]("cheapFifos").action { (_,_) => // Must necessarily turn on retiming
    spatialConfig.useCheapFifos = true
  }.text("Turns on cheap fifos where accesses must be multiples of each other and not have lane-enables")

  parser.opt[Unit]("tree").action( (_,_) =>
    spatialConfig.enableTree = true
  ).text("enables logging of controller tree for visualizing app structure")

  parser.opt[Unit]("dot").action( (_,_) =>
    spatialConfig.enableDot = true
  ).text("enables dot generation")

  parser.opt[Unit]("pir").action { (_,_) =>
    spatialConfig.enableSim = false
    spatialConfig.enableSynth = false
    spatialConfig.enablePIR = true
  }.text("enables PIR generation")

  parser.opt[String]("pirsrc").action { (x, c) =>
    spatialConfig.pirsrc = Some(x)
  }.text("copy directory for generated pir source")

  parser.opt[Unit]("cgra+").action{ (_,_) =>
    spatialConfig.enableSim = false
    spatialConfig.enableSynth = false
    spatialConfig.enablePIR = true
    spatialConfig.enableSplitting = true
  }

  parser.opt[Unit]("cgra*").action{ (_,_) =>
    spatialConfig.enableSim = false
    spatialConfig.enableSynth = false
    spatialConfig.enablePIR = true
    spatialConfig.enableArchDSE = true
    spatialConfig.enableSplitting = true
  }

  parser.opt[Unit]("pirsim").action{ (_,_) =>
    warn("Here be dragons.")
    spatialConfig.enableSim = false
    spatialConfig.enableSynth = true
    spatialConfig.enablePIR = false
    spatialConfig.enablePIRSim = true
    spatialConfig.enableSplitting = true
  }

  parser.opt[Int]("threads").action{ (t,_) =>
    spatialConfig.threads = t
  }

  parser.opt[Unit]("fast").action{ (_,_) =>
    spatialConfig.useBasicBlocks = true
  }.text("[EXPERIMENTAL] Use basic blocks")

  parser.opt[Unit]("xfast").action{ (_,_) =>
    spatialConfig.useBasicBlocks = true
    spatialConfig.verbosity = -2
  }.text("[EXPERIMENTAL] Use basic blocks")


  parser.opt[Unit]("affine").action{ (_,_) =>
    spatialConfig.useAffine = true
  }

}
