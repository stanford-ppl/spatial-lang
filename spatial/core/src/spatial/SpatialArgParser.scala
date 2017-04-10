package spatial

import argon.ArgonArgParser

import scopt._

class SpatialArgParser extends ArgonArgParser {

  override val scriptName = "spatial"
  override val description = "CLI for spatial"
  //not sur yet if we must optional()

  parser.opt[String]('t', "target").action( (x,_) =>
    SpatialConfig.switchTarget(x)
  ).text("chose codegen target")

}
