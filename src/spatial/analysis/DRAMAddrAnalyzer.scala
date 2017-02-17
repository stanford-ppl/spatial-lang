package spatial.analysis

import argon.traversal.CompilerPass
import spatial.{SpatialConfig, SpatialExp}

trait DRAMAddrAnalyzer extends CompilerPass {
  val IR: SpatialExp
  import IR._

  override val name = "DRAM Address Analyzer"

  def memStreams: Set[Exp[_]]

  override protected def process[S: Staged](block: Block[S]) = {
    // Current TileLd/St templates expect that LMem addresses are
    // statically known during graph build time in MaxJ. That can be
    // changed, but until then we have to assign LMem addresses
    // statically. Assigning each DRAM memory a 384MB chunk now
    var nextLMemAddr: Long = target.burstSize * 1024 * 1024
    def getNextLMemAddr(): Long = {
      val addr = nextLMemAddr
      nextLMemAddr += target.burstSize * 1024 * 1024;
      addr
    }
    memStreams.foreach{mem => dramAddr(mem) = getNextLMemAddr() }

    block
  }

}
