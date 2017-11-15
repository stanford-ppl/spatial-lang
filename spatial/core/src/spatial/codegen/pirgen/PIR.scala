package spatial.codegen.pirgen

import argon.core._
import forge._

import scala.collection.mutable
import spatial.metadata._

trait PIR

trait Component extends PIR
// --- Global buses
sealed abstract class GlobalComponent(val name: String) extends Component
case class OffChip(override val name: String) extends GlobalComponent(name)

sealed abstract class GlobalBus(override val name: String) extends GlobalComponent(name) {
  globals += this
}
sealed abstract class VectorBus(override val name: String) extends GlobalBus(name)
sealed abstract class ScalarBus(override val name: String) extends GlobalBus(name)
sealed abstract class ControlBus(override val name: String) extends GlobalBus(name)

case class CUVector(override val name: String, par:Int) extends VectorBus(name) {
  override def toString = s"v$name"
}
case class CUScalar(override val name: String) extends ScalarBus(name) {
  override def toString = s"s$name"
}
case class CUControl(override val name: String) extends ControlBus(name) {
  override def toString = s"b$name"
}

case class InputArg(override val name: String, dmem:Expr) extends ScalarBus(name)
case class OutputArg(override val name: String) extends ScalarBus(name)
case class DramAddress(override val name: String, dram:Expr, mem:Expr) extends ScalarBus(name) {
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
sealed abstract class LocalComponent extends Component { 
  final val id = {LocalComponent.id += 1; LocalComponent.id}
}
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
case class MemLoad(mem: CUMemory) extends LocalMem[MemLoad] {
  override def eql(that: MemLoad) = this.mem == that.mem
}
case class MemNumel(mem: CUMemory) extends LocalMem[MemLoad] {
  override def eql(that: MemLoad) = this.mem == that.mem
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
case class ControlIn(bus: ControlBus) extends LocalPort[ControlIn] {
  override def eql(that: ControlIn) = this.bus == that.bus
  override def toString = bus.toString + ".bIn"
}
case class ControlOut(bus: ControlBus) extends LocalPort[ControlOut] {
  override def eql(that: ControlOut) = this.bus == that.bus
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
case class CUCounter(var start: LocalComponent, var end: LocalComponent, var stride: LocalComponent, var par:Int) extends PIR {
  val name = s"ctr${CUCounter.nextId()}"
  def longString = s"ctr(${start}, ${end}${end.getClass.getSimpleName}, ${stride}, $par)"
}
object CUCounter {
  var id: Int = 0
  def nextId(): Int = {id += 1; id}
}

sealed abstract class CUCChain(val name: String) extends PIR { def longString: String } 
case class CChainInstance(override val name: String, counters: Seq[CUCounter]) extends CUCChain(name) {
  override def toString = name
  def longString: String = s"$name (${counters.map(_.longString).mkString(",")})"
}
case class CChainCopy(override val name: String, inst: CUCChain, var owner: CU) extends CUCChain(name) {
  override def toString = s"$owner.copy($name)"
  def longString: String = this.toString
  val iterIndices = mutable.Map[Int, Int]() // CtrIdx -> IterIdx
}
case class UnitCChain(override val name: String) extends CUCChain(name) {
  override def toString = name
  def longString: String = this.toString + " [unit]"
}


// --- Compute unit memories
case class CUMemory(name: String, mem: Expr, cu:CU) extends PIR {
  var tpe: LocalMemoryType = _ 
  var mode:LocalMemoryMode = _ 
  var bufferDepth: Option[Int] = None
  var banking: Option[SRAMBanking] = None
  var size = 1

  // writePort either from bus or for sram can be from a vector FIFO
  //val writePort = mutable.ListBuffer[GlobalBus]()
  val writePort = mutable.ListBuffer[(Component, Option[LocalComponent], Option[CU])]() // (data, addr, producer/consumer)
  val readPort = mutable.ListBuffer[(Component, Option[LocalComponent], Option[CU])]()
  //var readAddr = mutable.ListBuffer[LocalComponent]()
  //var writeAddr = mutable.ListBuffer[LocalComponent]()

  override def toString = name

  def isSRAM = tpe == SRAMType
  def isLocalMem = tpe match {
    case SRAMType => false
    case _ => true
  }
  def isRemoteMem = tpe match {
    case SRAMType => true
    case _ => false
  }
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
case class ReduceStage(op: PIROp, init: ConstReg[_<:AnyVal], in: LocalRef, acc: ReduceReg, var accParent:CU) extends Stage {
  def inputMems = List(in.reg, acc)
  def outputMems = List(acc)
  def inputRefs = List(in)
  def outputRefs = Nil
}

// --- Compute units
case class ComputeUnit(name: String, var style: CUStyle) extends PIR {
  var parent: Option[CU] = None

  var cchains: Set[CUCChain] = Set.empty
  val memMap: mutable.Map[Any, CUMemory] = mutable.Map.empty
  var regs: Set[LocalComponent] = Set.empty

  val regTable = mutable.HashMap[Expr, LocalComponent]()
  val expTable = mutable.HashMap[LocalComponent, List[Expr]]()
  val switchTable = mutable.HashMap[ControlBus, CU]()

  def iterators = regTable.iterator.collect{case (exp, reg: CounterReg) => (exp,reg) }
  def valids    = regTable.iterator.collect{case (exp, reg: ValidReg) => (exp,reg) }

  def mems:Set[CUMemory] = memMap.values.toSet
  def srams:Set[CUMemory] = mems.filter {_.tpe==SRAMType}
  def fifos:Set[CUMemory] = mems.filter { mem => mem.tpe==VectorFIFOType || mem.tpe==ScalarFIFOType}
  def remoteMems:Set[CUMemory] = mems.filter { _.isRemoteMem }
  def localMems:Set[CUMemory] = mems.filter { _.isLocalMem }

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

  val pseudoStages = mutable.ArrayBuffer[PseudoStage]()
  val computeStages = mutable.ArrayBuffer[Stage]()
  val controlStages = mutable.ArrayBuffer[Stage]()

  def parentCU: Option[CU] = parent.flatMap{case cu: CU => Some(cu); case _ => None}

  def allStages: Iterator[Stage] = computeStages.iterator ++
                                   controlStages.iterator
  var isDummy: Boolean = false

  def lanes: Int = innerPar
  def allParents: Iterable[CU] = parentCU ++ parentCU.map(_.allParents).getOrElse(Nil)
  def isPMU = style == MemoryCU
  def isPCU = !isPMU && !style.isInstanceOf[FringeCU]

  def sram = {
    assert(style == MemoryCU, s"Only MemoryCU has sram. cu:$this")
    val srams = mems.filter{ _.tpe == SRAMType }
    assert(srams.size==1, s"Each MemoryCU should only has one sram, srams:[${srams.mkString(",")}]")
    srams.head
  }

}

// --- Context for creating/modifying CUs
abstract class CUContext(val cu: ComputeUnit) {
  private val refs = mutable.HashMap[Expr,LocalRef]()
  private var readAccums: Set[AccumReg] = Set.empty

  def stages: mutable.ArrayBuffer[Stage]
  def addStage(stage: Stage): Unit
  def isWriteContext: Boolean
  def init(): Unit

  def stageNum: Int = stages.count{case stage:MapStage => true; case _ => false} + 1
  def controlStageNum: Int = controlStages.length
  def prevStage: Option[Stage] = stages.lastOption
  def mapStages: Iterator[MapStage] = stages.iterator.collect{case stage:MapStage => stage}

  def controlStages: mutable.ArrayBuffer[Stage] = cu.controlStages
  def addControlStage(stage: Stage): Unit = cu.controlStages += stage

  def addReg(x: Expr, reg: LocalComponent) {
    //debug(s"  $x -> $reg")
    cu.addReg(x, reg)
  }
  def addRef(x: Expr, ref: LocalRef) { refs += x -> ref }
  @stateful def getReg(x: Expr): Option[LocalComponent] = cu.get(x)
  @stateful def reg(x: Expr): LocalComponent = {
    cu.get(x).getOrElse(throw new Exception(s"No register defined for $x in $cu"))
  }

  // Add a stage which bypasses x to y
  def bypass(x: LocalComponent, y: LocalComponent) {
    val stage = MapStage(PIRBypass, List(refIn(x)), List(refOut(y)))
    addStage(stage)
  }

  def ref(reg: LocalComponent, out: Boolean, stage: Int = stageNum): LocalRef = reg match {
    // If the previous stage computed the read address for this load, use the registered output
    // of the memory directly. Otherwise, use the previous stage
    case MemLoad(sram) => //TODO: rework out the logic here
      /*debug(s"Referencing SRAM $sram in stage $stage")
      debug(s"  Previous stage: $prevStage")
      debug(s"  SRAM read addr: ${sram.readAddr}")*/
      if (prevStage.isEmpty || sram.tpe == VectorFIFOType || sram.tpe == ScalarFIFOType )
        LocalRef(-1, reg)
      else {
        if (sram.tpe != VectorFIFOType && sram.tpe!= ScalarFIFOType) {
          LocalRef(stage-1,reg)
        }
        else
          throw new Exception(s"No address defined for SRAM $sram")
      }

    case reg: CounterReg if isWriteContext && prevStage.isEmpty =>
      //debug(s"Referencing counter $reg in first write stage")
      LocalRef(-1, reg)

    case reg: AccumReg if isUnreadAccum(reg) =>
      //debug(s"First reference to accumulator $reg in stage $stage")
      readAccums += reg
      LocalRef(stage, reg)
    case _ if out =>
      //debug(s"Referencing output register $reg in stage $stage")
      LocalRef(stage, reg)
    case _ =>
      //debug(s"Referencing input register $reg in stage $stage")
      LocalRef(stage-1, reg)
  }
  def refIn(reg: LocalComponent, stage: Int = stageNum) = ref(reg, out = false, stage)
  def refOut(reg: LocalComponent, stage: Int = stageNum) = ref(reg, out = true, stage)

  def addOutputFor(e: Expr)(prev: LocalComponent, out: LocalComponent): Unit = addOutput(prev, out, Some(e))
  def addOutput(prev: LocalComponent, out: LocalComponent): Unit = addOutput(prev, out, None)
  def addOutput(prev: LocalComponent, out: LocalComponent, e: Option[Expr]): Unit = {
    mapStages.find{stage => stage.outputMems.contains(prev) } match {
      case Some(stage) =>
        stage.outs :+= refOut(out, mapStages.indexOf(stage) + 1)
      case None =>
        bypass(prev, out)
    }
    if (e.isDefined) addReg(e.get, out)
    else cu.regs += out // No mapping, only list
  }

  // Get memory in this CU associated with the given reader
  def mem(mem: Expr): CUMemory = {
    val cuMems = cu.mems.filter{ _.mem == mem }
    assert(cuMems.size==1, s"More than 1 cuMem=${cuMems} allocated for $mem in $cu")
    cuMems.head
  }

  // A CU can have multiple SRAMs for a given mem symbol, one for each local read
  def memories(mem: Expr) = cu.mems.filter(_.mem == mem)


  // HACK: Keep track of first read of accum reg (otherwise can use the wrong stage)
  private def isUnreadAccum(reg: LocalComponent) = reg match {
    case reg: AccumReg => !readAccums.contains(reg)
    case _ => false
  }
}


case class ComputeContext(override val cu: ComputeUnit) extends CUContext(cu) {
  def stages = cu.computeStages
  def addStage(stage: Stage) { cu.computeStages += stage }
  def isWriteContext = false
  def init() = {}
}
//case class WriteContext(override val cu: ComputeUnit) extends CUContext(cu) {
  //def init() { cu.writeStages.clear }
  //def stages = cu.writeStages
  //def addStage(stage: Stage) { cu.writeStages += stage }
  //def isWriteContext = true
//}
//case class ReadContext(override val cu: ComputeUnit) extends CUContext(cu) {
  //def init() { cu.readStages.clear }
  //def stages = cu.readStages
  //def addStage(stage: Stage) { cu.readStages += stage }
  //def isWriteContext = false 
//}
