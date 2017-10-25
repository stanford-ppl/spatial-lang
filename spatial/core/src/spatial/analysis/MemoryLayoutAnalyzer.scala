package spatial.analysis

import argon.core._
import argon.traversal.CompilerPass
import spatial.aliases._
import spatial.banking.MemoryConfigurer
import spatial.metadata._
import spatial.utils._
import spatial.nodes._

case class MemoryLayoutAnalyzer(var IR: State, localMems: () => Seq[Exp[_]]) extends CompilerPass {
  override val name = "Memory Layout Analyzer"

  override protected def process[S:Type](block: Block[S]): Block[S] = {
    run()
    shouldWarn = false // Don't warn user after first run (avoid duplicate warnings)
    block
  }

  def run(): Unit = {
    // Reset metadata prior to running memory analysis
    metadata.clearAll[AccessDispatch]
    metadata.clearAll[PortIndex]

    val memories = localMems()
    memories.foreach{mem =>
      val manager = MemoryConfigurer(mem)
      val instances = bankAccesses(mem)

    }
  }





  // TODO: Other cases for coalescing?
  def coalesceMemories(mem: Exp[_], instances: Seq[InstanceGroup]): Seq[InstanceGroup] = {
    val writers = writersOf(mem).toSet
    val readers = readersOf(mem).toSet








/*


    }*/
  }






}
