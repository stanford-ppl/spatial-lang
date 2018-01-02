package spatial.targets

abstract class XilinxDevice extends FPGATarget {
  import XilinxDevice._
  override val LFIELDS = Array(RequiresRegs, RequiresInReduce, LatencyOf, LatencyInReduce, BuiltInLatency)
  override val FIELDS: Array[String] = Array(RAM18,RAM36,URAM,RAM32X1S,RAM32X1D,RAM32M,RAM64M,RAM64X1S,RAM64X1D,RAM128X1S,RAM128X1D,RAM256X1S,SRLC32E,SRL16E,DSPs,Regs,Mregs,MUX7,MUX8,LUT1,LUT2,LUT3,LUT4,LUT5,LUT6,SLICEL,SLICEM,Slices)
  val DSP_CUTOFF = 16 // TODO: Not sure if this is right
}
object XilinxDevice {
  val RAM18    = "RAM18"
  val RAM36    = "RAM36"
  val BRAM     = "BRAM"
  val URAM     = "URAM"
  val RAM32X1S = "RAM32X1S"
  val RAM32X1D = "RAM32X1D"
  val RAM32M   = "RAM32M"
  val RAM64M   = "RAM64M"
  val RAM64X1S = "RAM64X1S"
  val RAM64X1D = "RAM64X1D"
  val RAM128X1S = "RAM128X1S"
  val RAM128X1D = "RAM128X1D"
  val RAM256X1S = "RAM256X1S"
  val SRLC32E  = "SRLC32E"
  val SRL16E   = "SRL16E"
  val DSPs     = "DSPs"
  val Regs     = "Regs"
  val Mregs    = "Mregs"
  val MUX7     = "MUX7"
  val MUX8     = "MUX8"
  val LUT1     = "LUT1"
  val LUT2     = "LUT2"
  val LUT3     = "LUT3"
  val LUT4     = "LUT4"
  val LUT5     = "LUT5"
  val LUT6     = "LUT6"
  val SLICEL   = "SLICEL"
  val SLICEM   = "SLICEM"
  val Slices   = "Slices"
  val RequiresRegs = "RequiresRegs"
  val LatencyOf = "LatencyOf"
  val LatencyInReduce = "LatencyInReduce"
  val RequiresInReduce = "RequiresInReduce"
  val BuiltInLatency = "BuiltInLatency"
}
