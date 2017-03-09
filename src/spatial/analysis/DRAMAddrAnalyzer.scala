package spatial.analysis

import argon.traversal.CompilerPass
import spatial.{SpatialConfig, SpatialExp}

trait ArgMappingAnalyzer extends CompilerPass {
  val IR: SpatialExp
  import IR._

  override val name = "Arg Analyzer"

  def memStreams: Set[Exp[_]]
  def argIns: Set[Exp[_]]
  def argOuts: Set[Exp[_]]

  override protected def process[S: Staged](block: Block[S]) = {
    // Current TileLd/St templates expect that LMem addresses are
    // statically known during graph build time in MaxJ. That can be
    // changed, but until then we have to assign LMem addresses
    // statically. Assigning each DRAM memory a 384MB chunk now

    argIns.toList.distinct.zipWithIndex.foreach{case(a,i) => 
      dbg(u"Mapping $a to $i")
      argMapping(a) = (i, i)
    }
    val ofs = argIns.toList.distinct.length
    memStreams.toList.distinct.zipWithIndex.foreach{case(m,i) => 
      dbg(u"Mapping $m to $i + $ofs")
      argMapping(m) = (i + ofs, i)
    }
    argOuts.toList.distinct.zipWithIndex.foreach{case(a,i) => 
      dbg(u"Mapping $a to $i")
      argMapping(a) = (i, i)
    }

    block
  }

}
