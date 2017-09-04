package spatial.analysis

import argon.core._
import argon.traversal.CompilerPass
import spatial.aliases._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._

trait ArgMappingAnalyzer extends CompilerPass {
  override val name = "Arg Analyzer"

  def memStreams: Set[(Exp[_], Int, Int)]
  def genericStreams: Set[(Exp[_], String)]
  def argPorts: Set[(Exp[_], String)]

  override protected def process[S: Type](block: Block[S]) = {
    /* NOTE:
        Eventually this needs to map each individual load/store to its own stream, in case people want to do 2
        unique loads or 2 unique stores to the same dram.  Currently, the fringe command signals will interfere
        if someone does this
    */

    // ArgMapping returns a tuple of (something that I don't use anymore, argIn_port, argOut_port)
    // Set for ArgIOs
    dbg(u"Working on bidirectional args only, base port = 0: ${argPorts}")
    argPorts.toList.distinct.filter{_._2 == "bidirectional"}.zipWithIndex.foreach{case((a, dir),i) => 
      dbg(u"  - Mapping $a ($dir) to -1, ${i}, ${i}")
      argMapping(a) = (-1, i, i)
    }

    // Set for ArgIns
    var base_in = argPorts.toList.distinct.filter{_._2 == "bidirectional"}.length
    dbg(u"Working on input args only, base port = ${base_in}: ${argPorts}")
    argPorts.toList.distinct.filter{_._2 == "input"}.zipWithIndex.foreach{case((a, dir),i) => 
      dbg(u"  - Mapping $a ($dir) to -1, ${base_in+i}, -1")
      argMapping(a) = (-1, base_in + i, -1)
    }
    
    base_in = argPorts.toList.distinct.filter{_._2 == "input"}.length + base_in
    var p = 0 // Ugly imperative but whatever
    dbg(u"Working on memStreams only, base port = ${base_in}: ${memStreams}")
    memStreams.map{_._1}.toList.distinct.zipWithIndex.foreach{case(m,i) => 
      val numLoads = memStreams.toList.filter{_._1 == m}.head._2
      val numStores = memStreams.toList.filter{_._1 == m}.head._3
      dbg(u"  - Mapping $m to $numLoads, ${base_in+p}, $numStores")
      argMapping(m) = (numLoads, base_in + p, numStores)
      p = p + 1
      // if (entries.length == 1) {
      //   if (entries.head._2 == "input") {
      //     dbg(u"  - Mapping $m to port -1, ${base_in+p}, -1")
      //     argMapping(m) = (i, base_in + p, -1)
      //   } else if (entries.head._2 == "output") {
      //     dbg(u"  - Mapping $m to port -1, ${base_in+p}, -1")
      //     argMapping(m) = (i, base_in + p, -1)          
      //   }
      //   p = p + 1
      // } else if (entries.length == 2) {
      //   dbg(u"  - Mapping $m to -1, ${base_in+p}, -1")
      //   argMapping(m) = (-1, base_in + p, -1)          
      //   p = p + 1
      // }
    }

    var p_out = 0 // Ugly imperative but whatever
    var p_in = 0 // Ugly imperative but whatever
    dbg(u"Working on genericStreams only, base port = ${base_in}: ${genericStreams}")
    genericStreams.toList.distinct.zipWithIndex.foreach{case((m,dir),i) => 
      if (dir == "output") {
        dbg(u"  - Mapping $m ($dir) to ${p_out} -1 $i")
        argMapping(m) = (p_out, -1, i)
        p_out = p_out + 1
      } else if (dir == "input") {
        dbg(u"  - Mapping $m ($dir) to ${p_in} $i -1")
        argMapping(m) = (p_in, i, -1)
        p_in = p_in + 1
      }
    }

    var base_out = argPorts.toList.distinct.filter{_._2 == "bidirectional"}.length
    dbg(u"Working on output args only, base port = ${base_out}: ${argPorts}")
    argPorts.toList.distinct.filter{_._2 == "output"}.zipWithIndex.foreach{case((a,dir),i) => 
      dbg(u"  - Mapping $a ($dir) to $i -1, ${base_out+i}")
      argMapping(a) = (i, -1, base_out + i)
    }


    block
  }

}
