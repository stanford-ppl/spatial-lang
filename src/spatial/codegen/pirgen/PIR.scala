package spatial.codegen.pirgen

import scala.collection.mutable

// TODO: This is VERY redundant with PIR
trait PIR {
  type Symbol
  def isConstant(x: Symbol): Boolean
  def extractConstant(x: Symbol): String
  def str(x: Symbol): String

  // --- Memory controller modes
  sealed abstract class OffchipMemoryMode
  case object MemLoad extends OffchipMemoryMode { override def toString = "TileLoad" }
  case object MemStore extends OffchipMemoryMode { override def toString = "TileStore" }
  case object MemScatter extends OffchipMemoryMode { override def toString = "Scatter" }
  case object MemGather extends OffchipMemoryMode { override def toString = "Gather" }


  // --- Local memory banking
  sealed abstract class SRAMBanking
  case class Strided(stride: Int) extends SRAMBanking
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
  case object UnitCU extends CUStyle
  case object UnitStreamCU extends CUStyle
  //case object BurstTransfer extends CUStyle
  //case object RandomTransfer extends CUStyle

  // --- Local memory modes
  sealed abstract class LocalMemoryMode
  case object SRAMMode extends LocalMemoryMode
  case object FIFOMode extends LocalMemoryMode
  case object FIFOOnWriteMode extends LocalMemoryMode


  // --- Global buses
  sealed abstract class GlobalComponent(val name: String)
  case class OffChip(override val name: String) extends GlobalComponent(name)
  case class MemoryController(override val name: String, dram: OffChip, mode: OffchipMemoryMode) extends GlobalComponent(name)

  sealed abstract class GlobalBus(override val name: String) extends GlobalComponent(name)
  sealed abstract class VectorBus(override val name: String) extends GlobalBus(name)
  sealed abstract class ScalarBus(override val name: String) extends GlobalBus(name)

  case class CUVector(override val name: String) extends VectorBus(name) {
    override def toString = s"v$name"
  }
  case class CUScalar(override val name: String) extends ScalarBus(name) {
    override def toString = s"s$name"
  }

  case object LocalVectorBus extends VectorBus("LocalVector")
  case class InputArg(override val name: String) extends ScalarBus(name) {
    override def toString = s"ain$name"
  }
  case class OutputArg(override val name: String) extends ScalarBus(name) {
    override def toString = s"aout$name"
  }

  trait PIRDRAMBus
  case class PIRDRAMOffset(mc: MemoryController) extends ScalarBus(mc.name + "_ofs") with PIRDRAMBus {
    override def toString = s"s$name"
  }
  case class PIRDRAMLength(mc: MemoryController) extends ScalarBus(mc.name + "_len") with PIRDRAMBus {
    override def toString = s"s$name"
  }
  case class PIRDRAMAddress(mc: MemoryController) extends VectorBus(mc.name + "_addr") with PIRDRAMBus {
    override def toString = s"v$name"
  }
  case class PIRDRAMDataOut(mc: MemoryController) extends VectorBus(mc.name + "_dataOut") with PIRDRAMBus {
    override def toString = s"v$name"
  }
  case class PIRDRAMDataIn(mc: MemoryController) extends VectorBus(mc.name + "_dataIn") with PIRDRAMBus {
    override def toString = s"v$name"
  }

  case class BusGroups(args: Iterable[ScalarBus], scalars: Iterable[ScalarBus], vectors: Iterable[VectorBus])

  def groupBuses(x: Iterable[GlobalBus]) = {
    val args    = x.collect{case arg:InputArg => arg}
    val scalars = x.collect{case b:ScalarBus if !b.isInstanceOf[InputArg] => b}
    val vectors = x.collect{case b:VectorBus => b}
    BusGroups(args, scalars, vectors)
  }


  // --- Local registers / wires
  sealed abstract class LocalComponent { final val id = {LocalComponent.id += 1; LocalComponent.id} }
  object LocalComponent { var id = 0 }

  sealed trait LocalScalar extends LocalComponent
  sealed trait ReadAddr extends LocalComponent
  sealed trait WriteAddr extends LocalComponent

  sealed abstract class LocalMem[T<:LocalComponent] extends LocalComponent {
    def eql(that: T): Boolean = this.id == that.id
    def canEqual(that: Any): Boolean
    override final def equals(a: Any) = a match {
      case that: LocalMem[_] if this.canEqual(that) => eql(that.asInstanceOf[T])
      case _ => false
    }
  }

  case class ConstReg(const: String) extends LocalMem[ConstReg] with LocalScalar with ReadAddr with WriteAddr {
    override def eql(that: ConstReg) = this.const == that.const
    override def toString = const
  }
  case class CounterReg(cchain: CUCChain, idx: Int) extends LocalMem[CounterReg] with ReadAddr with WriteAddr {
    override def eql(that: CounterReg) = this.cchain == that.cchain && this.idx == that.idx
    override def toString = cchain+s"($idx)"
  }

  case class ControlReg() extends LocalMem[ControlReg] {
    override def toString = s"cr$id"
  }
  case class ValidReg(cchain: CUCChain, idx: Int) extends LocalMem[ValidReg] {
    override def eql(that: ValidReg) = this.cchain == that.cchain && this.idx == that.idx
    override def toString = cchain.name+s"($idx).valid"
  }

  sealed abstract class SRAMPort[T<:LocalComponent] extends LocalMem[T]
  case class ReadAddrWire(mem: CUMemory) extends SRAMPort[ReadAddrWire] with ReadAddr {
    override def eql(that: ReadAddrWire) = this.mem == that.mem
    override def toString = mem.name + ".readAddr"
  }
  case class WriteAddrWire(mem: CUMemory) extends SRAMPort[WriteAddrWire] with WriteAddr {
    override def eql(that: WriteAddrWire) = this.mem == that.mem
    override def toString = mem.name + ".writeAddr"
  }
  case class FeedbackAddrReg(mem: CUMemory) extends SRAMPort[FeedbackAddrReg] with WriteAddr {
    override def eql(that: FeedbackAddrReg) = this.mem == that.mem
    override def toString = mem.name + ".feedbackAddr"
  }
  case class FeedbackDataReg(mem: CUMemory) extends SRAMPort[FeedbackDataReg] {
    override def eql(that: FeedbackDataReg) = this.mem == that.mem
    override def toString = mem.name + ".feedbackData"
  }
  case class SRAMReadReg(mem: CUMemory) extends SRAMPort[SRAMReadReg] {
    override def eql(that: SRAMReadReg) = this.mem == that.mem
    override def toString = mem.name + ".readPort"
  }

  sealed abstract class ReduceMem[T<:LocalComponent] extends LocalMem[T]
  case class ReduceReg() extends ReduceMem[ReduceReg] {
    override def toString = s"rr$id"
  }
  case class AccumReg(init: ConstReg) extends ReduceMem[AccumReg] {
    override def toString = s"ar$id"
  }

  case class TempReg() extends LocalMem[TempReg] {
    override def toString = s"tr$id"
  }


  sealed abstract class LocalPort[T<:LocalComponent] extends LocalMem[T] {
    def bus: GlobalBus
  }
  case class ScalarIn(bus: ScalarBus) extends LocalPort[ScalarIn] with LocalScalar {
    override def eql(that: ScalarIn) = this.bus == that.bus
    override def toString = bus.toString + ".sIn"
  }
  case class ScalarOut(bus: ScalarBus) extends LocalPort[ScalarOut] {
    override def eql(that: ScalarOut) = this.bus == that.bus
    override def toString = bus.toString + ".sOut"
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
  case class CUCounter(var start: LocalScalar, var end: LocalScalar, var stride: LocalScalar) {
    val name = s"ctr${CUCounter.nextId()}"
  }
  object CUCounter { var id: Int = 0; def nextId(): Int = {id += 1; id} }

  sealed abstract class CUCChain(val name: String)
  case class CChainInstance(override val name: String, counters: Seq[CUCounter]) extends CUCChain(name) {
    override def toString = name
  }
  case class CChainCopy(override val name: String, inst: CUCChain, var owner: AbstractComputeUnit) extends CUCChain(name) {
    override def toString = s"$owner.copy($name)"
  }
  case class UnitCChain(override val name: String) extends CUCChain(name) {
    override def toString = name
  }


  // --- Compute unit memories
  case class CUMemory(name: String, size: Int, mem: Symbol, reader: Symbol) {
    var mode: LocalMemoryMode = SRAMMode
    var bufferDepth: Int = 1
    var banking: Option[SRAMBanking] = None
    var vector: Option[GlobalBus] = None

    var readAddr: Option[ReadAddr] = None
    var writeAddr: Option[WriteAddr] = None

    var writeStart: Option[LocalScalar] = None
    var writeEnd: Option[LocalScalar] = None

    var swapWrite: Option[CUCChain] = None
    var swapRead: Option[CUCChain] = None
    var writeCtrl: Option[CUCChain] = None

    override def toString = name

    def copyMem(name: String) = {
      val copy = CUMemory(name, size, mem, reader)
      copy.mode = this.mode
      copy.bufferDepth = this.bufferDepth
      copy.banking = this.banking
      copy.vector = this.vector
      copy.readAddr = this.readAddr
      copy.writeAddr = this.writeAddr
      copy.writeStart = this.writeStart
      copy.swapWrite = this.swapWrite
      copy.swapRead = this.swapRead
      copy.writeCtrl = this.writeCtrl
      copy
    }
  }


  // --- Pre-scheduling stages
  sealed abstract class PseudoStage { def output: Option[Symbol] }
  case class DefStage(op: Symbol, isReduce: Boolean = false) extends PseudoStage {
    def output = Some(op)
    override def toString = s"DefStage(${str(op)}" + (if (isReduce) " [REDUCE]" else "") + ")"
  }
  case class OpStage(op: PIROp, inputs: Seq[Symbol], out: Symbol, isReduce: Boolean = false) extends PseudoStage {
    def output = Some(out)
  }
  case class WriteAddrStage(mem: Symbol, addr: Symbol) extends PseudoStage { def output = None }
  case class FifoOnWriteStage(mem: Symbol, start: Option[Symbol], end: Option[Symbol]) extends PseudoStage { def output = None }


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
  case class ReduceStage(op: PIROp, init: ConstReg, in: LocalRef, acc: ReduceReg) extends Stage {
    def inputMems = List(in.reg, acc)
    def outputMems = List(acc)
    def inputRefs = List(in)
    def outputRefs = Nil
  }


  // --- Compute units
  type ACU = AbstractComputeUnit
  abstract class AbstractComputeUnit {
    val name: String
    val pipe: Symbol
    var style: CUStyle
    var parent: Option[AbstractComputeUnit] = None
    def isUnit = style == UnitCU || style == UnitStreamCU
    def isMemoryUnit = false //cuType == BurstTransfer || cuType == RandomTransfer

    var cchains: Set[CUCChain] = Set.empty
    var srams: Set[CUMemory] = Set.empty
    var regs: Set[LocalComponent] = Set.empty
    var deps: Set[AbstractComputeUnit] = Set.empty

    val regTable: mutable.HashMap[Symbol,LocalComponent] = mutable.HashMap[Symbol, LocalComponent]()
    val expTable: mutable.HashMap[LocalComponent,List[Symbol]] = mutable.HashMap[LocalComponent, List[Symbol]]()

    def iterators = regTable.iterator.collect{case (exp, reg: CounterReg) => (exp,reg) }
    def valids    = regTable.iterator.collect{case (exp, reg: ValidReg) => (exp,reg) }

    def innermostIter(cc: CUCChain) = {
      val iters = iterators.flatMap{case (e,CounterReg(`cc`,i)) => Some((e,i)); case _ => None}
      if (iters.isEmpty) None  else Some(iters.reduce{(a,b) => if (a._2 > b._2) a else b}._1)
    }

    def addReg(exp: Symbol, reg: LocalComponent) {
      regs += reg
      regTable += exp -> reg
      if (expTable.contains(reg)) expTable += reg -> (expTable(reg) :+ exp)
      else                        expTable += reg -> List(exp)
    }
    def get(x: Symbol): Option[LocalComponent] = {
      if (regTable.contains(x)) regTable.get(x)
      else if (isConstant(x)) {
        val c = ConstReg(extractConstant(x))
        addReg(x, c)
        Some(c)
      }
      else None
    }
    def getOrElse(x: Symbol)(func: => LocalComponent) = this.get(x) match {
      case Some(reg) if regs.contains(reg) => reg // On return this mapping if it is valid
      case _ =>
        val reg = func
        addReg(x, reg)
        reg
    }
  }

  type CU = ComputeUnit
  case class ComputeUnit(name: String, pipe: Symbol, var style: CUStyle) extends AbstractComputeUnit {
    val writeStages   = mutable.HashMap[List[CUMemory], mutable.ArrayBuffer[Stage]]()
    val computeStages = mutable.ArrayBuffer[Stage]()
    val controlStages = mutable.ArrayBuffer[Stage]()

    def parentCU: Option[CU] = parent.flatMap{case cu: CU => Some(cu); case _ => None}

    def allStages: Iterator[Stage] = writeStages.valuesIterator.flatMap(_.iterator) ++ computeStages.iterator ++
                                     controlStages.iterator
    var isDummy: Boolean = false
  }

  type PCU = PseudoComputeUnit
  case class PseudoComputeUnit(name: String, pipe: Symbol, var style: CUStyle) extends AbstractComputeUnit {
    val writeStages = mutable.HashMap[List[CUMemory], (Symbol, Seq[PseudoStage])]()
    val computeStages = mutable.ArrayBuffer[PseudoStage]()

    def copyToConcrete(): ComputeUnit = {
      val cu = ComputeUnit(name, pipe, style)
      cu.parent = this.parent
      cu.cchains ++= this.cchains
      cu.srams ++= this.srams
      cu.regs ++= this.regs
      cu.deps ++= this.deps
      cu.regTable ++= this.regTable
      cu.expTable ++= this.expTable
      cu
    }
  }


  sealed abstract class PIROp
  case object PIRALUMux extends PIROp { override def toString() = "Mux" }
  case object PIRBypass extends PIROp
  case object PIRFixAdd extends PIROp
  case object PIRFixSub extends PIROp
  case object PIRFixMul extends PIROp
  case object PIRFixDiv extends PIROp
  case object PIRFixMod extends PIROp
  case object PIRFixLt  extends PIROp
  case object PIRFixLeq extends PIROp
  case object PIRFixEql extends PIROp
  case object PIRFixNeq extends PIROp
  case object PIRFixMin extends PIROp
  case object PIRFixMax extends PIROp
  case object PIRFixNeg extends PIROp

  case object PIRFltAdd extends PIROp
  case object PIRFltSub extends PIROp
  case object PIRFltMul extends PIROp
  case object PIRFltDiv extends PIROp
  case object PIRFltLt  extends PIROp
  case object PIRFltLeq extends PIROp
  case object PIRFltEql extends PIROp
  case object PIRFltNeq extends PIROp
  case object PIRFltExp extends PIROp
  case object PIRFltLog extends PIROp
  case object PIRFltSqrt extends PIROp
  case object PIRFltAbs extends PIROp
  case object PIRFltMin extends PIROp
  case object PIRFltMax extends PIROp
  case object PIRFltNeg extends PIROp

  case object PIRBitAnd extends PIROp
  case object PIRBitOr  extends PIROp
}
