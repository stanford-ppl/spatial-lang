package spatial.analysis

import scala.collection.mutable.HashMap
import argon.traversal.CompilerPass
import spatial.{SpatialConfig, SpatialExp}

trait ArgMappingAnalyzer extends CompilerPass {
  val IR: SpatialExp
  import IR._

  override val name = "Arg Analyzer"

  def memStreams: Set[(Exp[_], String)]
  def genericStreams: Set[(Exp[_], String)]
  def argPorts: Set[(Exp[_], String)]

  override protected def process[S: Type](block: Block[S]) = {
    /* NOTE:
        Eventually this needs to map each individual load/store to its own stream, in case people want to do 2
        unique loads or 2 unique stores to the same dram.  Currently, the fringe command signals will interfere
        if someone does this
    */

    // Set for ArgIOs
    argPorts.toList.distinct.filter{_._2 == "bidirectional"}.zipWithIndex.foreach{case((a, dir),i) => 
      dbg(u"Mapping $a ($dir) to ${i}, ${i}, ${i}")
      argMapping(a) = (i, i, i)
    }

    // Set for ArgIns
    var base_in = argPorts.toList.distinct.filter{_._2 == "bidirectional"}.length
    argPorts.toList.distinct.filter{_._2 == "input"}.zipWithIndex.foreach{case((a, dir),i) => 
      dbg(u"Mapping $a ($dir) to $i, $i, -1")
      argMapping(a) = (i, base_in + i, -1)
    }
    
    base_in = argPorts.toList.distinct.filter{_._2 == "input"}.length + base_in
    var p = 0 // Ugly imperative but whatever
    memStreams.map{_._1}.toList.distinct.zipWithIndex.foreach{case(m,i) => 
      val entries = memStreams.toList.distinct.filter{_._1 == m}
      if (entries.length == 1) {
        if (entries.head._2 == "input") {
          dbg(u"Mapping $m to port ${base_in+i}, streams ${base_in+p}, -1")
          argMapping(m) = (i, base_in + p, -1)
        } else if (entries.head._2 == "output") {
          dbg(u"Mapping $m to port ${base_in+i}, streams ${base_in+p}, -1")
          argMapping(m) = (i, base_in + p, -1)          
        }
        p = p + 1
      } else if (entries.length == 2) {
        dbg(u"Mapping $m to port ${i}, streams ${base_in+p}, ${base_in+p+1}")
        argMapping(m) = (i, base_in + p, base_in + p+1)          
        p = p + 2
      }
    }

    var p_out = 0 // Ugly imperative but whatever
    var p_in = 0 // Ugly imperative but whatever
    genericStreams.toList.distinct.zipWithIndex.foreach{case((m,dir),i) => 
      if (dir == "output") {
        dbg(u"Mapping $m ($dir) to -1, $i")
        argMapping(m) = (p_out, -1, i)
        p_out = p_out + 1
      } else if (dir == "input") {
        dbg(u"Mapping $m ($dir) to $i, -1")
        argMapping(m) = (p_in, i, -1)
        p_in = p_in + 1
      }
    }

    var base_out = argPorts.toList.distinct.filter{_._2 == "bidirectional"}.length
    argPorts.toList.distinct.filter{_._2 == "output"}.zipWithIndex.foreach{case((a,dir),i) => 
      dbg(u"Mapping $a ($dir) to -1, $i")
      argMapping(a) = (i, -1, base_out + i)
    }


    block
  }

}
