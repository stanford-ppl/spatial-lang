package spatial

import argon.ArgonArgParser
import argon.util.Report._

class SpatialArgParser extends ArgonArgParser {

  override def scriptName = "spatial"
  override def description = "CLI for spatial"
  //not sur yet if we must optional() // TODO: ???


  parser.opt[Unit]("synth").action{ (_,_) =>
    SpatialConfig.enableSynth = true
    SpatialConfig.enableSim = false
  }.text("enable codegen to chisel + cpp (Synthesis) (disable sim) [false]")

  parser.opt[Unit]("retiming").action{ (_,_) =>
    SpatialConfig.enableRetiming = true
  }.text("enable retiming [false]")

  parser.opt[Unit]("sim").action { (_,_) =>
    SpatialConfig.enableSim = true
    SpatialConfig.enableSynth = false
  }.text("enable codegen to Scala (Simulation) (disable synth) [true]")

  parser.opt[String]("fpga").action( (x,_) =>
    SpatialConfig.targetName = x
  ).text("Set name of FPGA target [Default]")

  parser.opt[Unit]("dse").action( (_,_) =>
    SpatialConfig.enableDSE = true
  ).text("enables design space exploration [false]")

  parser.opt[Unit]("retiming").action( (_,_) =>
    SpatialConfig.enableRetiming = true
  ).text("enables inner pipeline retiming [false]")

  parser.opt[Unit]("naming").action( (_,_) =>
    SpatialConfig.enableNaming = true
  ).text("generates the debug name for all syms, rather than \"x${s.id}\" only'")

  parser.opt[Unit]("tree").action( (_,_) =>
    SpatialConfig.enableNaming = true
  ).text("enables logging of controller tree for visualizing app structure")

  parser.opt[Unit]("dot").action( (_,_) =>
    SpatialConfig.enableDot = true
  ).text("enables dot generation")

  parser.opt[Unit]("pir").action { (_,_) =>
    SpatialConfig.enableSim = false
    SpatialConfig.enableSynth = false
    SpatialConfig.enablePIR = true
  }.text("enables PIR generation")

  parser.opt[Unit]("cgra+").action{ (_,_) =>
    SpatialConfig.enableSim = false
    SpatialConfig.enableSynth = false
    SpatialConfig.enablePIR = true
    SpatialConfig.enableSplitting = true
  }

  parser.opt[Unit]("cgra*").action{ (_,_) =>
    SpatialConfig.enableSim = false
    SpatialConfig.enableSynth = false
    SpatialConfig.enablePIR = true
    SpatialConfig.enableArchDSE = true
    SpatialConfig.enableSplitting = true
  }

  parser.opt[Unit]("pirsim").action{ (_,_) =>
    warn("Here be dragons.")
    SpatialConfig.enableSim = false
    SpatialConfig.enableSynth = true
    SpatialConfig.enablePIR = false
    SpatialConfig.enablePIRSim = true
    SpatialConfig.enableSplitting = true
  }

  parser.opt[Int]("threads").action{ (t,_) =>
    SpatialConfig.threads = t
  }

}
