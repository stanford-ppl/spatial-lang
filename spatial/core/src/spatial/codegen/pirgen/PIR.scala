package spatial.codegen.pirgen

import argon.core._
import forge._

import scala.collection.mutable
import spatial.metadata._


// --- Memory controller modes
sealed abstract class OffchipMemoryMode
case object MemLoad extends OffchipMemoryMode { override def toString = "TileLoad" }
case object MemStore extends OffchipMemoryMode { override def toString = "TileStore" }
case object MemGather extends OffchipMemoryMode { override def toString = "Gather" }
case object MemScatter extends OffchipMemoryMode { override def toString = "Scatter" }


// --- Local memory banking
sealed abstract class SRAMBanking
case class Strided(stride: Int, banks:Int) extends SRAMBanking
case class Diagonal(stride1: Int, stride2: Int) extends SRAMBanking
case object NoBanks extends SRAMBanking { override def toString = "NoBanking()" }
case object Duplicated extends SRAMBanking { override def toString = "Duplicated()" }
//case object Fanout extends SRAMBanking { override def toString = "Fanout()" }


// --- Compute types
sealed abstract class CUStyle
case object PipeCU extends CUStyle
case object SequentialCU extends CUStyle
case object MetaPipeCU extends CUStyle
case object StreamCU extends CUStyle
case object MemoryCU extends CUStyle
case class FringeCU(dram:OffChip, mode:OffchipMemoryMode) extends CUStyle

// --- Local memory modes
sealed abstract class LocalMemoryMode
case object SRAMMode extends LocalMemoryMode
case object BitFIFOMode extends LocalMemoryMode
case object ScalarFIFOMode extends LocalMemoryMode
case object VectorFIFOMode extends LocalMemoryMode
case object ScalarBufferMode extends LocalMemoryMode

// --- Global buses
sealed abstract class GlobalComponent(val name: String)
case class OffChip(override val name: String) extends GlobalComponent(name)

sealed abstract class GlobalBus(override val name: String) extends GlobalComponent(name) {
  globals += this
}
sealed abstract class VectorBus(override val name: String) extends GlobalBus(name)
sealed abstract class ScalarBus(override val name: String) extends GlobalBus(name)
sealed abstract class BitBus(override val name: String) extends GlobalBus(name)

case class CUVector(override val name: String) extends VectorBus(name) {
  override def toString = s"v$name"
}
case class CUScalar(override val name: String) extends ScalarBus(name) {
  override def toString = s"s$name"
}
case class CUBit(override val name: String) extends BitBus(name) {
  override def toString = s"b$name"
}

case class LocalReadBus(mem:CUMemory) extends VectorBus(s"$mem.localRead")
case class InputArg(override val name: String, dmem:Expr) extends ScalarBus(name) {
  override def toString = s"ain$name"
}
case class OutputArg(override val name: String) extends ScalarBus(name) {
  override def toString = s"aout$name"
}
case class DramAddress(override val name: String, dram:Expr, mem:Expr) extends ScalarBus(name) {
  override def toString = s"dramAddr$name"
  // DramAddress of the same dram are the same
  override def equals(that: Any): Boolean =
    that match {
      case that: DramAddress => that.dram == dram 
      case _ => false
    }
  override def hashCode: Int = {
    return dram.hashCode
  }
}

// --- Local registers / wires
sealed abstract class LocalComponent { final val id = {LocalComponent.id += 1; LocalComponent.id} }
object LocalComponent { var id = 0 }

sealed abstract class LocalMem[T<:LocalComponent] extends LocalComponent {
  def eql(that: T): Boolean = this.id == that.id
  def canEqual(that: Any): Boolean
  override final def equals(a: Any) = a match {
    case that: LocalMem[_] if this.canEqual(that) => eql(that.asInstanceOf[T])
    case _ => false
  }
}

case class ConstReg[T<:AnyVal](const: T) extends LocalMem[ConstReg[T]] {
  override def eql(that: ConstReg[T]) = this.const == that.const
  override def toString = const.toString
}
case class CounterReg(cchain: CUCChain, counterIdx:Int, parIdx:Int) extends LocalMem[CounterReg] {
  override def eql(that: CounterReg) = 
    this.cchain == that.cchain && 
    this.counterIdx == that.counterIdx && 
    this.parIdx == that.parIdx
  override def toString = cchain+s"($counterIdx)"
}

case class ControlReg() extends LocalMem[ControlReg] {
  override def toString = s"cr$id"
}
case class ValidReg(cchain: CUCChain, counterIdx:Int, validIdx:Int) extends LocalMem[ValidReg] {
  override def eql(that: ValidReg) = 
    this.cchain == that.cchain && 
    this.counterIdx == that.counterIdx && 
    this.validIdx == that.validIdx
}
case class ReadAddrWire(mem: CUMemory) extends LocalMem[ReadAddrWire] {
  override def eql(that: ReadAddrWire) = this.mem == that.mem
}
case class WriteAddrWire(mem: CUMemory) extends LocalMem[WriteAddrWire] {
  override def eql(that: WriteAddrWire) = this.mem == that.mem
}
case class MemLoadReg(mem: CUMemory) extends LocalMem[MemLoadReg] {
  override def eql(that: MemLoadReg) = this.mem == that.mem
}
case class MemNumel(mem: CUMemory) extends LocalMem[MemLoadReg] {
  override def eql(that: MemLoadReg) = this.mem == that.mem
}

sealed abstract class ReduceMem[T<:LocalComponent] extends LocalMem[T]
case class ReduceReg() extends ReduceMem[ReduceReg] {
  override def toString = s"rr$id"
}
case class AccumReg(init: ConstReg[_<:AnyVal]) extends ReduceMem[AccumReg] {
  override def toString = s"ar$id"
}

case class TempReg(x:Expr, init:Option[Any]) extends LocalMem[TempReg] {
  override def eql(that: TempReg) = (this.x == that.x) && (this.init==that.init)
  override def toString = s"$x"
}

sealed abstract class LocalPort[T<:LocalComponent] extends LocalMem[T] {
  def bus: GlobalBus
}
case class ScalarIn(bus: ScalarBus) extends LocalPort[ScalarIn] {
  override def eql(that: ScalarIn) = this.bus == that.bus
  override def toString = bus.toString + ".sIn"
}
case class ScalarOut(bus: ScalarBus) extends LocalPort[ScalarOut] {
  override def eql(that: ScalarOut) = this.bus == that.bus
  override def toString = bus.toString + ".sOut"
}
case class BitIn(bus: BitBus) extends LocalPort[BitIn] {
  override def eql(that: BitIn) = this.bus == that.bus
  override def toString = bus.toString + ".bIn"
}
case class BitOut(bus: BitBus) extends LocalPort[BitOut] {
  override def eql(that: BitOut) = this.bus == that.bus
  override def toString = bus.toString + ".bOut"
}
case class VectorIn(bus: VectorBus) extends LocalPort[VectorIn] {
  override def eql(that: VectorIn) = this.bus == that.bus
  override def toString = bus.toString + ".vIn"
}
case class VectorOut(bus: VectorBus) extends LocalPort[VectorOut] {
  override def eql(that: VectorOut) = this.bus == that.bus
  override def toString = bus.toString + ".vOut"
}

// --- Counter chains
case class CUCounter(var start: LocalComponent, var end: LocalComponent, var stride: LocalComponent, var par:Int) {
  val name = s"ctr${CUCounter.nextId()}"
}
object CUCounter {
  var id: Int = 0
  def nextId(): Int = {id += 1; id}
}

sealed abstract class CUCChain(val name: String) { def longString: String }
case class CChainInstance(override val name: String, sym:Expr, counters: Seq[CUCounter]) extends CUCChain(name) {
  override def toString = name
  def longString: String = s"$name (" + counters.mkString(", ") + ")"
}
case class CChainCopy(override val name: String, inst: CUCChain, var owner: AbstractComputeUnit) extends CUCChain(name) {
  override def toString = s"$owner.copy($name)"
  def longString: String = this.toString
  val iterIndices = mutable.Map[Int, Int]() // CtrIdx -> IterIdx
}
case class UnitCChain(override val name: String) extends CUCChain(name) {
  override def toString = name
  def longString: String = this.toString + " [unit]"
}


// --- Compute unit memories
case class CUMemory(name: String, mem: Expr, cu:AbstractComputeUnit) {
  var mode: LocalMemoryMode = _ 
  var bufferDepth: Int = 1
  var banking: Option[SRAMBanking] = None
  var size = 1

  // writePort either from bus or for sram can be from a vector FIFO
  //val writePort = mutable.ListBuffer[GlobalBus]()
  var writePort = mutable.ListBuffer[GlobalBus]()
  var readPort: Option[GlobalBus] = None
  var readAddr = mutable.ListBuffer[LocalComponent]()
  var writeAddr = mutable.ListBuffer[LocalComponent]()

  var writeStart: Option[LocalComponent] = None
  var writeEnd: Option[LocalComponent] = None

  var producer:Option[PseudoComputeUnit] = None
  var consumer:Option[PseudoComputeUnit] = None

  override def toString = name

  def copyMem(name: String) = {
    val copy = CUMemory(name, mem, cu)
    copy.mode = this.mode
    copy.bufferDepth = this.bufferDepth
    copy.banking = this.banking
    copy.size = this.size
    //copy.writePort.clear
    //copy.writePort ++= this.writePort
    copy.writePort.clear
    copy.writePort ++= this.writePort
    copy.readPort = this.readPort
    copy.readAddr.clear
    copy.readAddr ++= this.readAddr
    copy.writeAddr.clear
    copy.writeAddr ++= this.writeAddr
    copy.writeStart = this.writeStart
    copy.producer = this.producer
    copy.consumer = this.consumer
    copy
  }

  def isSRAM = mode == SRAMMode
}


// --- Pre-scheduling stages
sealed abstract class PseudoStage { def output: Option[Expr] }
case class DefStage(op: Expr, isReduce: Boolean = false) extends PseudoStage {
  def output = Some(op)
  override def toString = s"DefStage($op" + (if (isReduce) " [REDUCE]" else "") + ")"
}
case class OpStage(op: PIROp, inputs: List[Expr], out: Expr, isReduce: Boolean = false) extends PseudoStage {
  def output = Some(out)
}
case class AddrStage(mem: Expr, addr: Expr) extends PseudoStage { def output = None }
case class FifoOnWriteStage(mem: Expr, start: Option[Expr], end: Option[Expr]) extends PseudoStage { def output = None }


// --- Scheduled stages
case class LocalRef(stage: Int, reg: LocalComponent)

sealed abstract class Stage {
  def inputMems: Seq[LocalComponent]
  def outputMems: Seq[LocalComponent]
  def inputRefs: Seq[LocalRef]
  def outputRefs: Seq[LocalRef]
}
case class MapStage(op: PIROp, var ins: Seq[LocalRef], var outs: Seq[LocalRef]) extends Stage {
  def inputMems = ins.map(_.reg)
  def outputMems = outs.map(_.reg)
  def inputRefs = ins
  def outputRefs = outs
}
case class ReduceStage(op: PIROp, init: ConstReg[_<:AnyVal], in: LocalRef, acc: ReduceReg, var accParent:AbstractComputeUnit) extends Stage {
  def inputMems = List(in.reg, acc)
  def outputMems = List(acc)
  def inputRefs = List(in)
  def outputRefs = Nil
}

// --- Compute units
abstract class AbstractComputeUnit {
  val name: String
  val pipe: Expr
  var style: CUStyle
  var parent: Option[AbstractComputeUnit] = None

  var cchains: Set[CUCChain] = Set.empty
  val memMap: mutable.Map[Any, CUMemory] = mutable.Map.empty
  var regs: Set[LocalComponent] = Set.empty
  var deps: Set[AbstractComputeUnit] = Set.empty

  val regTable = mutable.HashMap[Expr, LocalComponent]()
  val expTable = mutable.HashMap[LocalComponent, List[Expr]]()
  val switchTable = mutable.HashMap[BitBus, AbstractComputeUnit]()

  def iterators = regTable.iterator.collect{case (exp, reg: CounterReg) => (exp,reg) }
  def valids    = regTable.iterator.collect{case (exp, reg: ValidReg) => (exp,reg) }

  def mems:Set[CUMemory] = memMap.values.toSet
  def srams:Set[CUMemory] = mems.filter {_.mode==SRAMMode}
  def fifos:Set[CUMemory] = mems.filter { mem => mem.mode==VectorFIFOMode || mem.mode==ScalarFIFOMode}

  val fringeGlobals = mutable.Map[String, GlobalBus]()

  var innerPar:Int = _
  //def innermostIter(cc: CUCChain) = {
    //val iters = iterators.flatMap{case (e,CounterReg(`cc`,i)) => Some((e,i)); case _ => None}
    //if (iters.isEmpty) None  else Some(iters.reduce{(a,b) => if (a._2 > b._2) a else b}._1)
  //}

  def addReg(exp: Expr, reg: LocalComponent) {
    regs += reg
    regTable += exp -> reg
    if (expTable.contains(reg)) expTable += reg -> (expTable(reg) :+ exp)
    else                        expTable += reg -> List(exp)
  }
  @stateful def get(x: Expr): Option[LocalComponent] = {
    regTable.get(x).orElse {
      if (isConstant(x)) { val reg = extractConstant(x); addReg(x, reg); Some(reg) } else None
    }
  }
  @stateful def getOrElseUpdate(x: Expr)(func: => LocalComponent): LocalComponent = this.get(x) match {
    case Some(reg) if regs.contains(reg) => reg // On return this mapping if it is valid
    case _ =>
      val reg = func
      addReg(x, reg)
      reg
  }
}


case class ComputeUnit(name: String, pipe: Expr, var style: CUStyle) extends AbstractComputeUnit {
  val writeStages   = mutable.ArrayBuffer[Stage]()
  val readStages    = mutable.ArrayBuffer[Stage]()
  val computeStages = mutable.ArrayBuffer[Stage]()
  val controlStages = mutable.ArrayBuffer[Stage]()

  def parentCU: Option[CU] = parent.flatMap{case cu: CU => Some(cu); case _ => None}

  def allStages: Iterator[Stage] = writeStages.iterator ++
                                   readStages.iterator ++
                                   computeStages.iterator ++
                                   controlStages.iterator
  var isDummy: Boolean = false

  def lanes: Int = innerPar
  def allParents: Iterable[CU] = parentCU ++ parentCU.map(_.allParents).getOrElse(Nil)
  def isPMU = style == MemoryCU
  def isPCU = !isPMU && !style.isInstanceOf[FringeCU]
}


case class PseudoComputeUnit(name: String, pipe: Expr, var style: CUStyle) extends AbstractComputeUnit {
  val writeStages = mutable.ArrayBuffer[PseudoStage]() // 
  val readStages = mutable.ArrayBuffer[PseudoStage]() // 
  val computeStages = mutable.ArrayBuffer[PseudoStage]()

  def sram = {
    assert(style == MemoryCU, s"Only MemoryCU has sram. cu:$this")
    val srams = mems.filter{ _.mode == SRAMMode }
    assert(srams.size==1, s"Each MemoryCU should only has one sram, srams:[${srams.mkString(",")}]")
    srams.head
  }

  def copyToConcrete(): ComputeUnit = {
    val cu = ComputeUnit(name, pipe, style)
    cu.innerPar = this.innerPar
    cu.parent = this.parent
    cu.cchains ++= this.cchains
    cu.memMap ++= this.memMap
    cu.regs ++= this.regs
    cu.deps ++= this.deps
    cu.regTable ++= this.regTable
    cu.expTable ++= this.expTable
    cu.switchTable ++= this.switchTable
    cu
  }
}


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
