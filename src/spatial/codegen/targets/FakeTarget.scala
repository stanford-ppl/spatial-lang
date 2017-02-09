package spatial.codegen.targets

object FakeTarget extends FPGATarget {
  def name = "Fake"
  def burstSize = 32 // bits
}
