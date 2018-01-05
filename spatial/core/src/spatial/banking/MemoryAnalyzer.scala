package spatial.banking

import argon.core._
import argon.traversal.CompilerPass
import spatial.metadata.LocalMemories
import spatial.nodes._

case class MemoryAnalyzer(var IR: State) extends CompilerPass {
  override val name = "Memory Layout Analyzer"

  override protected def process[S:Type](block: Block[S]): Block[S] = {
    run()
    shouldWarn = false // Don't warn user after first run (to avoid duplicate warnings)
    block
  }

  val strategy: BankingStrategy = new ExhaustiveBanking()

  /**
    * Returns a memory configurer for the given memory.
    * Fails in the wildcard case since the characteristics of an arbitrary memory type are unknown.
    */
  protected def getConfigurer(mem: Exp[_]): MemoryConfigurer = mem.tp match {
    case _:StreamInType[_]    => new MemoryConfigurer(mem,strategy)
    case _:StreamOutType[_]   => new MemoryConfigurer(mem,strategy)
    case _:FIFOType[_]        => new MemoryConfigurer(mem,strategy)
    case _:FILOType[_]        => new MemoryConfigurer(mem,strategy)
    case _:SRAMType[_]        => new MemoryConfigurer(mem,strategy)
    case _:RegType[_]         => new RegConfigurer(mem,strategy)
    case _:RegFileType[_]     => new MemoryConfigurer(mem,strategy)
    case _:LUTType[_]         => new MemoryConfigurer(mem,strategy)
    case _:BufferedOutType[_] => new MemoryConfigurer(mem,strategy)
    case _:LineBufferType[_]  => new LineBufferConfigurer(mem,strategy)
    case tp => throw new Exception(u"Don't know how to bank memory of type $tp")
  }

  def run(): Unit = {


    // Reset metadata prior to running memory analysis
    metadata.clearAll[AccessDispatch]
    metadata.clearAll[PortIndex]
    metadata.clearAll[Duplicates]

    val memories = globaldata[LocalMemories].mems
    memories.foreach{mem =>
      val configurer = getConfigurer(mem)
      configurer.configure()
    }
  }

}
