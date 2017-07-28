package spatial.codegen.pirgen

import argon.core._
import spatial.utils._
import spatial.metadata._
import scala.collection.mutable

trait PIRGenController extends PIRCodegen with PIRTraversal {

  def cus: mutable.Map[Expr,List[List[ComputeUnit]]]
  var allocatedReduce: Set[ReduceReg] = Set.empty
  val genControlLogic = false

  override protected def preprocess[S:Type](block: Block[S]): Block[S] = {
    val blk = super.preprocess(block) // generateHeader
    generateGlobals()
    blk
  }

  def generateGlobals() {
    val (mcs, buses) = globals.partition{case mc:MemoryController => true; case _ => false}
    buses.filterNot(_.isInstanceOf[PIRDRAMBus]).foreach(emitComponent _)
    mcs.foreach(emitComponent _)
  }

  def emitAllStages(cu: ComputeUnit) {
    def emitStages(stages: Iterable[Stage], prefix:String="") = stages.foreach{
      case MapStage(op,inputs,outputs) =>
        val ins = inputs.map(quote).mkString(", ")
        val outs = outputs.map(quote).mkString(", ")
        emit(s"""${prefix}Stage(operands=List($ins), op=$op, results=List($outs))""")

      case ReduceStage(op,init,in,acc, accParent) =>
        emit(s"""val (_, ${quote(acc)}) = Stage.reduce(op=$op, init=${quote(init)}, accumParent="${accParent.name}")""")
        allocatedReduce += acc
    }

    //emit(s"var stage: List[Stage] = Nil")

    if (cu.controlStages.nonEmpty && genControlLogic) {
      emitStages(cu.controlStages)
    }
    for ((srams,stages) <- cu.writeStages if stages.nonEmpty) {
      emitStages(stages,"WA")
    }
    for ((srams,stages) <- cu.readStages if stages.nonEmpty) {
      emitStages(stages,"RA")
    }
    if (cu.computeStages.nonEmpty) {
      emitStages(cu.computeStages)
    }
  }
  //override def quote(s: Exp[_]): String

  def cuDeclaration(cu: CU) = {
    val decs = mutable.ListBuffer[String]()
    decs += s"""name="${cu.name}""""
    val parent = cu.parent.map(_.name).getOrElse("top")

    //TODO: refactor this
    if (cu.style.isInstanceOf[MemoryCU]) {
      decs += s"""parent="$parent"""" // MemoryPipeline's parent might be declared later 
    } else {
      decs += s"""parent=$parent"""
    }
    cu.style match {
      case FringeCU(dram, mode) =>
        decs += s"""offchip=${quote(dram)}, mctpe=$mode"""
      case _ =>
    }
    s"${quote(cu)}(${decs.mkString(",")})"
  }

  def preallocateRegisters(cu: CU) = cu.regs.foreach{
    case reg:TempReg        => emit(s"val ${quote(reg)} = CU.temp")
    //case reg@AccumReg(init) => emit(s"val ${quote(reg)} = CU.accum(init = ${quote(init)})")
    case reg:ControlReg if genControlLogic => emit(s"val ${quote(reg)} = CU.ctrl")
    case _ => // No preallocation
  }

  def preallocateFeedbackRegs(cu: CU) = cu.regs.foreach{
    case reg@FeedbackAddrReg(mem) => emit(s"val ${quote(reg)} = CU.wtAddr(${quote(mem)})")
    case _ => //nothing
  }

  override def emitCU(lhs: Exp[_], cu: CU): Unit = {
    val Def(rhs) = lhs
    //emit(s"""// Def($lhs) = $rhs [isControlNode=${isControlNode(lhs)}]""")

    val srams = cu.srams // Memories with address calculation
    val mems = cu.mems.diff(srams) // Without address calculation
    dbgs(s"$cu.mems=${mems}")

    open(s"val ${cu.name} = ${cuDeclaration(cu)} { implicit CU => ")
    preallocateRegisters(cu)                // Includes scalar inputs/outputs, temps, accums
    mems.foreach(emitComponent(_))      // Declare mems without addr calculation first. 
                                        // Counter bounds might depends on scalarBuffer
    cu.cchains.foreach(emitComponent(_))    // Allocate all counterchains
    srams.foreach(emitComponent(_))      // Allocate all SRAMs. address calculation might depends on counters
    emitFringeVectors(cu)
    preallocateFeedbackRegs(cu)             // Local write addresses

    cu.style match {
      case PipeCU => emitAllStages(cu)
      case MemoryCU(i) => emitAllStages(cu)
      case _ => 
    }

    close("}")
  }

  def quoteInCounter(reg: LocalScalar) = reg match {
    case reg@MemLoadReg(mem) => s"$mem.load"
    case reg:ConstReg[_] => s"""${quote(reg)}"""
  }

  def emitComponent(x: Any): Unit = x match {
    case CChainCopy(name, inst, owner) =>
      emit(s"""val $name = CounterChain.copy("${owner.name}", "$name")""")

    case CChainInstance(name, sym, ctrs) =>
      for (ctr <- ctrs) emitComponent(ctr)
      val ctrList = ctrs.map(_.name).mkString(", ")
      val iter = nIters(sym)
      emit(s"""val $name = CounterChain(name = "$name", $ctrList).iter(${iter})""")

    case UnitCChain(name) =>
      emit(s"""val $name = CounterChain(name = "$name", Counter(Const(0), Const(1), Const(1), par=1)).iter(1l)""")

    case ctr@CUCounter(start, end, stride, par) =>
      emit(s"""val ${ctr.name} = Counter(min=${quoteInCounter(start)}, max=${quoteInCounter(end)}, step=${quoteInCounter(stride)}, par=$par) // Counter""")

    case mem: CUMemory =>
      dbgs(s"Emitting mem:$mem")
      val decl = mutable.ListBuffer[String]()
      val lhs = s"val ${mem.name} =" 
      if (mem.cu.style.isInstanceOf[FringeCU]) {
        decl += s"""name="${getField(mem.mem).get}""""
      }
      if (mem.mode!=ScalarBufferMode) {
        decl += s"""size=${mem.size}"""
      }

      mem.banking match {
        case Some(banking) if mem.mode==SRAMMode => decl += s"banking = $banking"
        case Some(_) =>
        case None => //ScalarBuffer doesn't have banking
      }

      var ports = ""

      mem.writePort match {
        case Some(LocalVectorBus) => // Nothing?
        case Some(LocalReadBus(vfifo)) => ports += s".wtPort(${quote(vfifo)}.readPort)" 
        case Some(vec) => ports += s""".wtPort(${quote(vec)})"""
        case None => ports += s""".wtPort(None)"""
        //case None => throw new Exception(s"Memory $mem has no writePort defined")
      }
      mem.readPort match {
        case Some(LocalVectorBus) => // Nothing?
        case Some(vec) => ports += s""".rdPort(${quote(vec)})"""
        case None if mem.mode==SRAMMode => throw new Exception(s"Memory $mem has no readPort defined")
        case None => 
      }
      mem.readAddr match {
        case Some(_:CounterReg | _:ConstReg[_]) => ports += s""".rdAddr(${quote(mem.readAddr.get)})"""
        case Some(_:ReadAddrWire) =>
        case None if mem.mode != SRAMMode => // ok
        case addr => ports += s""".rdAddr($addr)"""
        //case addr => throw new Exception(s"Disallowed memory read address in $mem: $addr") //TODO
      }
      mem.writeAddr match {
        case Some(_:CounterReg | _:ConstReg[_]) => ports += s""".wtAddr(${quote(mem.writeAddr.get)})"""
        case Some(_:WriteAddrWire | _:FeedbackAddrReg) =>
        case None if mem.mode != SRAMMode => // ok
        case addr => ports += s""".wtAddr($addr)""" //TODO
        //case addr => throw new Exception(s"Disallowed memory write address in $mem: $addr")
      }
      if (mem.mode != SRAMMode) {
        mem.writeStart match {
          case Some(start) => ports += s""".wtStart(${quoteInCounter(start)})"""
          case _ =>
        }
        mem.writeEnd match {
          case Some(end) => ports += s""".wtEnd(${quoteInCounter(end)})"""
          case _ =>
        }
      }

      emit(s"""$lhs ${quote(mem.mode)}(${decl.mkString(",")})$ports""")

    case mc@MemoryController(name,region,mode,parent) =>
      emit(s"""val ${quote(mc)} = MemoryController($mode, ${quote(region)}).parent("${cus(parent).head.head.name}")""")

    case mem: OffChip   => emit(s"""val ${quote(mem)} = OffChip("${mem.name}")""")
    case bus: InputArg  => 
      emit(s"""val ${quote(bus)} = ArgIn("${bus.name}")${boundOf.get(compose(bus.dmem)).fold("") { b => s".bound($b)" }}""")
    case bus: DramAddress  => 
      emit(s"""val ${quote(bus)} = DRAMAddress("${bus.name}", "${bus.dram.name.getOrElse(quote(bus.dram))}")${boundOf.get(compose(bus.mem)).fold("") { b => s".bound($b)" }}""")
    case bus: OutputArg => emit(s"""val ${quote(bus)} = ArgOut("${bus.name}")""")
    case bus: ScalarBus => emit(s"""val ${quote(bus)} = Scalar("${bus.name}")""")
    case bus: VectorBus => emit(s"""val ${quote(bus)} = Vector("${bus.name}")""")

    case _ => throw new Exception(s"Don't know how to generate PIR component $x")
  }

  def emitFringeVectors(cu:ComputeUnit) = {
    if (isFringe(cu.pipe)) {
      cu.fringeVectors.foreach { case (field, vec) =>
        emit(s"""CU.newVout("$field", ${quote(vec)})""")
      }
    }
  }

  def quote(mode: LocalMemoryMode): String = mode match {
    case SRAMMode => "SRAM"
    case VectorFIFOMode => "VectorFIFO"
    case FIFOOnWriteMode => "SemiFIFO"
    case ScalarBufferMode => "ScalarBuffer"
    case ScalarFIFOMode => "ScalarFIFO"
  }

  def quote(mem: CUMemory): String = mem.name

  def quote(x: GlobalComponent): String = x match {
    case OffChip(name)       => s"${name}_oc"
    case mc:MemoryController => s"${mc.name}_mc"
    case DramAddress(name, _, _)      => s"${name}_da"
    case InputArg(name, _)      => s"${name}_argin"
    case OutputArg(name)     => s"${name}_argout"
    case LocalVectorBus      => "local"
    case PIRDRAMDataIn(mc)      => s"${quote(mc)}.data"
    case PIRDRAMDataOut(mc)     => s"${quote(mc)}.data"
    case PIRDRAMOffset(mc)      => s"${quote(mc)}.ofs"
    case PIRDRAMLength(mc)      => s"${quote(mc)}.len"
    case PIRDRAMAddress(mc)     => s"${quote(mc)}.addrs"
    case bus:ScalarBus       => s"${bus.name}_s"
    case bus:VectorBus       => s"${bus.name}_v"
  }

  def quote(cu: CU): String = cu.style match {
    case StreamCU if cu.allStages.isEmpty && !cu.isDummy => "StreamController"
    case PipeCU => "Pipeline"
    case MetaPipeCU   => "MetaPipeline"
    case SequentialCU => "Sequential"
    case MemoryCU(i)     => "MemoryPipeline"
    case FringeCU(dram, mode)     => "MemoryController"
  }

  def quote(reg: LocalComponent): String = reg match {
    case ConstReg(c)             => s"""Const($c)"""              // Constant
    case CounterReg(cchain, idx) => s"${cchain.name}($idx)"         // Counter
    case ValidReg(cchain,idx)    => s"${cchain.name}.valids($idx)"  // Counter valid

    case WriteAddrWire(mem)      => s"${quote(mem)}.writeAddr"      // Write address wire
    case ReadAddrWire(mem)       => s"${quote(mem)}.readAddr"       // Read address wire
    case FeedbackAddrReg(mem)    => s"wr${reg.id}"                  // Local write address register
    case FeedbackDataReg(mem)    => quote(mem)                      // Local write data register
    case MemLoadReg(mem)        => quote(mem)                      // SRAM read

    case reg:ReduceReg           => s"rr${reg.id}"                  // Reduction register
    case reg:AccumReg            => s"ar${reg.id}"                  // After preallocation
    case reg:TempReg             => reg.toString                  // Temporary register
    case reg:ControlReg          => s"cr${reg.id}"                  // Control register

    case ScalarIn(bus)           => quote(bus)                      // Scalar input
    case ScalarOut(bus)          => quote(bus)                      // Scalar output
    case VectorIn(bus)           => quote(bus)                      // Vector input
    case VectorOut(bus)          => quote(bus)                      // Vector output
  }

  def quote(ref: LocalRef): String = ref match {
    case LocalRef(stage, reg: ConstReg[_])   => quote(reg)
    case LocalRef(stage, reg: CounterReg) => s"CU.ctr(${quote(reg)})"
    case LocalRef(stage, reg: ValidReg)   => quote(reg)

    case LocalRef(stage, wire: WriteAddrWire)  => quote(wire)
    case LocalRef(stage, wire: ReadAddrWire)   => quote(wire)
    case LocalRef(stage, reg: FeedbackAddrReg) => s"CU.wtAddr(stage($stage), ${quote(reg)})"
    case LocalRef(stage, reg: FeedbackDataReg) => s"CU.store(stage($stage), ${quote(reg)})"

    case LocalRef(stage, reg: ReduceReg) if allocatedReduce.contains(reg) => quote(reg)
    case LocalRef(stage, reg: ReduceReg)   => s"CU.reduce"
    case LocalRef(stage, reg: AccumReg)    => s"CU.accum(${quote(reg)})"
    case LocalRef(stage, reg: TempReg)     => s"${quote(reg)}"
    case LocalRef(stage, reg: ControlReg)  => s"CU.ctrl(${quote(reg)})"
    case LocalRef(stage, reg: MemLoadReg) => s"CU.load(${quote(reg)})"

    case LocalRef(stage, reg: ScalarIn)  => s"CU.scalarIn(${quote(reg)})"
    case LocalRef(stage, reg: ScalarOut) => s"CU.scalarOut(${quote(reg)})"
    case LocalRef(stage, reg: VectorIn)  => s"CU.vecIn(${quote(reg)})"
    case LocalRef(stage, reg: VectorOut) => s"CU.vecOut(${quote(reg)})"
  }
}
