package spatial.codegen.targets

abstract class FPGATarget {
  def burstSize: Int  // Size of DRAM burst (in bits)
}
