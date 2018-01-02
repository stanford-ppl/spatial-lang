package spatial.targets

abstract class AlteraDevice extends FPGATarget {
  import AlteraDevice._
  val LFIELDS = Array(RequiresRegs, RequiresInReduce, LatencyOf, LatencyInReduce, BuiltInLatency)
  val FIELDS = Array(LUT3, LUT4, LUT5, LUT6, LUT7, MEM16, MEM32, MEM64, ALMs, Regs, Mregs, DSPs, BRAM, Channels)
  val DSP_CUTOFF = 8 // TODO: Not sure if this is right
}
object AlteraDevice {
  val LUT3 = "LUT3"
  val LUT4 = "LUT4"
  val LUT5 = "LUT5"
  val LUT6 = "LUT6"
  val LUT7 = "LUT7"
  val MEM16 = "MEM16"
  val MEM32 = "MEM32"
  val MEM64 = "MEM64"
  val ALMs  = "ALMs"
  val Regs  = "Regs"
  val Mregs = "Mregs"
  val DSPs  = "DSPs"
  val BRAM  = "BRAM"
  val Channels = "Channels"
  val RequiresRegs = "RequiresRegs"
  val LatencyOf = "LatencyOf"
  val LatencyInReduce = "LatencyInReduce"
  val RequiresInReduce = "RequiresInReduce"
  val BuiltInLatency = "BuiltInLatency"
}