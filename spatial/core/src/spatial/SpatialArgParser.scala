package spatial

import argon.ArgonArgParser

class SpatialArgParser extends ArgonArgParser {
  override val scriptName = "spatial"

  addArg(LongSwitch("DSE","enable design space exploration"){
    SpatialConfig.enableDSE = true })

  addArg(LongSwitch("dot", "enable dot generation"){
    SpatialConfig.enableDot = true})
  addArg(LongSwitch("scala","enable scala generation"){
    SpatialConfig.enableScala = true})
  addArg(LongSwitch("chisel", "enable chisel generation"){
    SpatialConfig.enableChisel = true
    SpatialConfig.enableCpp = true
  })
  addArg(LongSwitch("cpp", "enable cpp generation"){
    SpatialConfig.enableCpp = true })

  addArg(Named("fpga", "set name of FPGA target"){
    arg => SpatialConfig.targetName = arg })

  addArg(LongSwitch("naming", "generate debug name for all syms, rather than id only"){
    SpatialConfig.enableNaming = true})

  /*addArg(LongSwitch("debug", "enable compiler logging"){
    // ???
  })*/

  addArg(LongSwitch("tree", "enable generation of controller tree html visualization"){
    SpatialConfig.enableTree = true})


  /** PIR Options **/
  addArg(LongSwitch("pdse", "enable Plasticine DSE"){
    SpatialConfig.enableArchDSE = true})
  addArg(LongSwitch("pir", "enable PIR generation"){
    SpatialConfig.enablePIR = true})
  /*addArg(LongSwitch("pdebug", "enable PIR debugging output"){
    SpatialConfig.??? = true
  })*/
  addArg(LongSwitch("split", "enable PIR splitting"){
    SpatialConfig.enableSplitting = true})


  addArg(LongSwitch("CGRA+", "enable PIR generation + splitting"){
    SpatialConfig.enableSplitting = true
    SpatialConfig.enablePIR = true
  })

  addArg(LongSwitch("CGRA*", "enable PIR generation + splitting + DSE"){
    SpatialConfig.enableSplitting = true
    SpatialConfig.enablePIR = true
    SpatialConfig.enableArchDSE = true
  })


}
