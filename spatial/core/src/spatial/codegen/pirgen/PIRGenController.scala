package spatial.codegen.pirgen

import argon.core._
import spatial.utils._
import spatial.metadata._
import scala.collection.mutable

trait PIRGenController extends PIRCodegen {

  var allocatedReduce: Set[ReduceReg] = Set.empty
  val genControlLogic = false

  override protected def preprocess[S:Type](block: Block[S]): Block[S] = {
    val blk = super.preprocess(block) // generateHeader
    emitGlobals()
    blk
  }

  def emitGlobals() {
    globals.foreach(emitComponent _)
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
    if (cu.computeStages.nonEmpty) {
      emitStages(cu.computeStages)
    }
  }
  //override def quote(s: Exp[_]): String

  def cuDeclaration(cu: CU) = {
    val decs = mutable.ListBuffer[String]()
    decs += s"""name="${cu.name}""""
    val parent = cu.parent.map(_.name).getOrElse("top")

    decs += s"""parent="$parent""""
    cu.style match {
      case FringeCU(dram, mode) =>
        decs += s"""offchip=${quote(dram)}, mctpe=$mode"""
      case _ =>
    }
    s"${quote(cu)}(${decs.mkString(",")})"
  }

  def preallocateRegisters(cu: CU) = cu.regs.foreach{
    case reg:TempReg        => emit(s"""val ${quote(reg)} = CU.temp(${reg.init}).name("${quote(reg)}")""")
    //case reg@AccumReg(init) => emit(s"val ${quote(reg)} = CU.accum(init = ${quote(init)})")
    case reg:ControlReg if genControlLogic => emit(s"val ${quote(reg)} = CU.ctrl")
    case _ => // No preallocation
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
    emitSwitchTable(cu)

    cu.style match {
      case PipeCU => emitAllStages(cu)
      case MemoryCU => emitAllStages(cu)
      case _ => 
    }

    close("}")
  }

  def emitSwitchTable(cu:CU) = {
    cu.switchTable.foreach { case (bus, caseCU) =>
      //emit(s"""CU.enableWhen(en=$bus, child="${caseCU.name})"""")
    }
  }

  def emitComponent(x: Any): Unit = x match {
    case cp@CChainCopy(name, inst, owner) =>
      val dec = s"""val $name = CounterChain.copy("${owner.name}", "$name")"""
      val iterIndices = cp.iterIndices.map { case (ctrIdx, iterIdx) => s"iterIdx($ctrIdx, $iterIdx)" }.toList
      emit(s"""${(dec :: iterIndices).mkString(".")}""")

    case x@CChainInstance(name, ctrs) =>
      for (ctr <- ctrs) emitComponent(ctr)
      val ctrList = ctrs.map(_.name).mkString(", ")
      val iter = nIters(x, false)
      emit(s"""val $name = CounterChain(name = "$name", $ctrList).iter(${iter})""")

    case UnitCChain(name) =>
      emit(s"""val $name = CounterChain(name = "$name", Counter(Const(0), Const(1), Const(1), par=1)).iter(1l)""")

    case ctr@CUCounter(start, end, stride, par) =>
      emit(s"""val ${ctr.name} = Counter(min=${quote(start)}, max=${quote(end)}, step=${quote(stride)}, par=$par) // Counter""")

    case mem: CUMemory =>
      dbgs(s"Emitting mem:$mem")
      var attrs = mutable.ListBuffer[String]()
      if (mem.tpe!=ScalarBufferType) {
        attrs += s".size(${mem.size})"
      }
      if (mem.tpe == SRAMType) {
        attrs += s".mode(${mem.mode})"
      }

      if (mem.cu.style.isInstanceOf[FringeCU]) {
        attrs += s""".name("${getField(mem.mem).get}")"""
      } else {
        attrs += s""".name("${mem.name}")"""
      }

      mem.banking match {
        case Some(banking) if mem.isSRAM => attrs += s".banking($banking)"
        case Some(_) =>
        case None => //ScalarBuffer doesn't have banking
      }
      
      mem.bufferDepth.foreach { depth => attrs += s".buffering($depth)" }

      mem.writePort.foreach { case (data, addr, top) => 
        attrs += s""".store(${quote(data)}, ${addr.map(quote)}, ${top.map(t => s""""${t.name}"""")})"""
      }
      mem.readPort.foreach { case (data, addr, top) =>
        attrs += s""".load(${quote(data)}, ${addr.map(quote)}, ${top.map(t => s""""${t.name}"""")})"""
      }

      emit(s"""val ${mem.name} = new ${quote(mem.tpe)}()""")
      attrs.foreach { attr => emit(s"  $attr") }

    //case mc@MemoryController(name,region,tpe,parent) =>
      //emit(s"""val ${quote(mc)} = MemoryController($mode, ${quote(region)}).parent("${cus(parent).head.head.name}")""")

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
    if (isFringe(mappingOf(cu))) {
      cu.fringeGlobals.foreach { 
        case (field, bus) => emit(s"""CU.newOut("$field", ${quote(bus)})""")
      }
    }
  }

  def quote(tpe: LocalMemoryType): String = tpe match {
    case SRAMType => "SRAM"
    case ControlFIFOType => "ControlFIFO"
    case ScalarFIFOType => "ScalarFIFO"
    case VectorFIFOType => "VectorFIFO"
    case ScalarBufferType => "ScalarBuffer"
  }

  def quote(mem: CUMemory): String = mem.name

  def quote(cu: CU): String = cu.style match {
    case StreamCU if cu.allStages.isEmpty && !cu.isDummy => "StreamController"
    case PipeCU => "Pipeline"
    case MetaPipeCU   => "MetaPipeline"
    case SequentialCU => "Sequential"
    case MemoryCU     => "MemoryPipeline"
    case FringeCU(dram, tpe)     => "MemoryController"
  }

  def quote(x:Component):String = x match {
    case OffChip(name)       => s"${name}_oc"
    //case mc:MemoryController => s"${mc.name}_mc"
    case DramAddress(name, _, _)      => s"${name}_da"
    case InputArg(name, _)      => s"${name}_argin"
    case OutputArg(name)     => s"${name}_argout"
    case bus:ScalarBus       => s"${bus.name}_s"
    case bus:VectorBus       => s"${bus.name}_v"
    case bus:ControlBus       => s"${bus.name}_b"

    case ConstReg(c)             => s"""Const($c)"""              // Constant
    case CounterReg(cchain, counterIdx, iterIdx) => s"${cchain.name}($counterIdx)"         // Counter TODO
    case ValidReg(cchain, counterIdx, validIdx)    => s"${cchain.name}.valids($counterIdx)"  // Counter valid TODO

    case WriteAddrWire(mem)      => s"${quote(mem)}.writeAddr"      // Write address wire
    case ReadAddrWire(mem)       => s"${quote(mem)}.readAddr"       // Read address wire
    case MemLoad(mem)        => s"${quote(mem)}.readPort"                      // SRAM read
    case MemNumel(mem)        => s"${quote(mem)}.numel"                      // Mem number of element

    case reg:ReduceReg           => s"rr${reg.id}"                  // Reduction register
    case reg:AccumReg            => s"ar${reg.id}"                  // After preallocation
    case reg:TempReg             => s"${quote(reg.x)}"                  // Temporary register
    case reg:ControlReg          => s"cr${reg.id}"                  // Control register

    case ControlIn(bus)              => quote(bus)                      // Scalar output
    case ControlOut(bus)             => quote(bus)                      // Scalar output
    case ScalarIn(bus)           => quote(bus)                      // Scalar input
    case ScalarOut(bus)          => quote(bus)                      // Scalar output
    case VectorIn(bus)           => quote(bus)                      // Vector input
    case VectorOut(bus)          => quote(bus)                      // Vector output
  }

  def quote(ref: LocalRef): String = ref match {
    case LocalRef(stage, reg: ConstReg[_])   => quote(reg)
    case LocalRef(stage, reg: CounterReg) => s"${quote(reg)}"
    case LocalRef(stage, reg: ValidReg)   => quote(reg)

    case LocalRef(stage, wire: WriteAddrWire)  => quote(wire)
    case LocalRef(stage, wire: ReadAddrWire)   => quote(wire)

    case LocalRef(stage, reg: ReduceReg) if allocatedReduce.contains(reg) => quote(reg)
    case LocalRef(stage, reg: ReduceReg)   => s"CU.reduce"
    case LocalRef(stage, reg: AccumReg)    => s"${quote(reg)}"
    case LocalRef(stage, reg: TempReg)     => s"${quote(reg)}"
    case LocalRef(stage, reg: ControlReg)  => s"${quote(reg)}"
    case LocalRef(stage, reg@MemLoad(mem)) => s"${quote(mem)}"

    case LocalRef(stage, reg: ScalarIn)  => s"CU.scalarIn(${quote(reg)})"
    case LocalRef(stage, reg: ScalarOut) => s"CU.scalarOut(${quote(reg)})"
    case LocalRef(stage, reg: VectorIn)  => s"CU.vecIn(${quote(reg)})"
    case LocalRef(stage, reg: VectorOut) => s"CU.vecOut(${quote(reg)})"
  }
}
