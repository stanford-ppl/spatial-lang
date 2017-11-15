package spatial.codegen.pirgen

import argon.core._
import forge._

import scala.collection.mutable
import spatial.metadata._

// --- Memory controller modes
sealed abstract class OffchipMemoryMode
case object TileLoad extends OffchipMemoryMode 
case object TileStore extends OffchipMemoryMode 
case object Gather extends OffchipMemoryMode 
case object Scatter extends OffchipMemoryMode 


// --- Local memory banking
sealed abstract class SRAMBanking
case class Strided(stride: Int, banks:Int) extends SRAMBanking
case class Diagonal(stride1: Int, stride2: Int) extends SRAMBanking
case object NoBanks extends SRAMBanking { override def toString = "NoBanking()" }
case object Duplicated extends SRAMBanking { override def toString = "Duplicated()" }
//case object Fanout extends SRAMBanking { override def toString = "Fanout()" }


// --- Compute types
sealed abstract class CUStyle
case object TopCU extends CUStyle
case object PipeCU extends CUStyle
case object SequentialCU extends CUStyle
case object MetaPipeCU extends CUStyle
case object StreamCU extends CUStyle
case object MemoryCU extends CUStyle
case class FringeCU(dram:OffChip, mode:OffchipMemoryMode) extends CUStyle

// --- Local memory modes
sealed abstract class LocalMemoryType
case object SRAMType extends LocalMemoryType
case object ControlFIFOType extends LocalMemoryType
case object ScalarFIFOType extends LocalMemoryType
case object VectorFIFOType extends LocalMemoryType
case object ScalarBufferType extends LocalMemoryType

sealed abstract class LocalMemoryMode
case object SRAMMode extends LocalMemoryMode { override def toString = "SramMode" }
case object FIFOMode extends LocalMemoryMode { override def toString = "FifoMode" }

sealed abstract class PIROp
case object PIRALUMux  extends PIROp { override def toString = "MuxOp"    }
case object PIRBypass  extends PIROp { override def toString = "Bypass" }
case object PIRFixAdd  extends PIROp { override def toString = "FixAdd" }
case object PIRFixSub  extends PIROp { override def toString = "FixSub" }
case object PIRFixMul  extends PIROp { override def toString = "FixMul" }
case object PIRFixDiv  extends PIROp { override def toString = "FixDiv" }
case object PIRFixMod  extends PIROp { override def toString = "FixMod" }
case object PIRFixLt   extends PIROp { override def toString = "FixLt"  }
case object PIRFixLeq  extends PIROp { override def toString = "FixLeq" }
case object PIRFixEql  extends PIROp { override def toString = "FixEql" }
case object PIRFixNeq  extends PIROp { override def toString = "FixNeq" }
case object PIRFixSla  extends PIROp { override def toString = "FixSla" }
case object PIRFixSra  extends PIROp { override def toString = "FixSra" }
case object PIRFixMin  extends PIROp { override def toString = "FixMin" }
case object PIRFixMax  extends PIROp { override def toString = "FixMax" }
case object PIRFixNeg  extends PIROp { override def toString = "FixNeg" }

case object PIRFltAdd  extends PIROp { override def toString = "FltAdd" }
case object PIRFltSub  extends PIROp { override def toString = "FltSub" }
case object PIRFltMul  extends PIROp { override def toString = "FltMul" }
case object PIRFltDiv  extends PIROp { override def toString = "FltDiv" }
case object PIRFltLt   extends PIROp { override def toString = "FltLt"  }
case object PIRFltLeq  extends PIROp { override def toString = "FltLeq" }
case object PIRFltEql  extends PIROp { override def toString = "FltEql" }
case object PIRFltNeq  extends PIROp { override def toString = "FltNeq" }
case object PIRFltExp  extends PIROp { override def toString = "FltExp" }
case object PIRFltLog  extends PIROp { override def toString = "FltLog" }
case object PIRFltSqrt extends PIROp { override def toString = "FltSqr" }
case object PIRFltAbs  extends PIROp { override def toString = "FltAbs" }
case object PIRFltMin  extends PIROp { override def toString = "FltMin" }
case object PIRFltMax  extends PIROp { override def toString = "FltMax" }
case object PIRFltNeg  extends PIROp { override def toString = "FltNeg" }

case object PIRBitAnd  extends PIROp { override def toString = "BitAnd" }
case object PIRBitOr   extends PIROp { override def toString = "BitOr"  }
