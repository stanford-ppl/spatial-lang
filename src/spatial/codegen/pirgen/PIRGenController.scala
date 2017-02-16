package spatial.codegen.pirgen

import argon.codegen.pirgen.PIRCodegen
import spatial.api.{ControllerExp, CounterExp, UnrolledExp}
import spatial.SpatialConfig
import spatial.analysis.SpatialMetadataExp
import spatial.SpatialExp
import scala.collection.mutable.Map
import argon.Config

trait PIRGenController extends PIRTraversal with PIRCodegen {
  val IR: SpatialExp with PIRCommonExp
  import IR._

  val genControlLogic = false
  var allocatedReduce: Set[ReduceReg] = Set.empty

  lazy val allocater = new PIRAllocation{val IR: PIRGenController.this.IR.type = PIRGenController.this.IR}
  lazy val scheduler = new PIRScheduler{val IR: PIRGenController.this.IR.type = PIRGenController.this.IR}
  lazy val optimizer = new PIROptimizer{val IR: PIRGenController.this.IR.type = PIRGenController.this.IR}
  lazy val splitter  = new PIRSplitter{val IR: PIRGenController.this.IR.type = PIRGenController.this.IR}
  lazy val hacks     = new PIRHacks{val IR: PIRGenController.this.IR.type = PIRGenController.this.IR}
  lazy val dse       = new PIRDSE{val IR: PIRGenController.this.IR.type = PIRGenController.this.IR}

  val cus = Map[Exp[Any],List[ComputeUnit]]()

  private def emitNestedLoop(cchain: Exp[CounterChain], iters: Seq[Bound[Index]])(func: => Unit): Unit = {
    for (i <- iters.indices)
      open(src"$cchain($i).foreach{case (is,vs) => is.zip(vs).foreach{case (${iters(i)},v) => if (v) {")

    func

    iters.indices.foreach{_ => close("}}}") }
  }

  override protected def preprocess[S:Staged](block: Block[S]): Block[S] = {
    // -- CU allocation
    allocater.run(block)
    // -- CU scheduling
    scheduler.mappingIn ++= allocater.mapping
    scheduler.globals ++= allocater.globals
    scheduler.run(block)
    // -- Optimization
    optimizer.globals ++= scheduler.globals
    optimizer.mapping ++= scheduler.mappingOut
    optimizer.run(block)

    if (SpatialConfig.enableSplitting) {
      splitter.globals ++= optimizer.globals
      splitter.mappingIn ++= optimizer.mapping
      splitter.run(block)

      hacks.mappingIn ++= splitter.mappingOut
      hacks.globals ++= splitter.globals
    }
    else {
      for ((s,cu) <- optimizer.mapping) hacks.mappingIn(s) = List(cu)
      hacks.globals ++= optimizer.globals
    }
    hacks.run(block)

    cus ++= hacks.mappingOut
    globals ++= hacks.globals


    if (SpatialConfig.enableArchDSE) {
      dse.globals ++= optimizer.globals
      dse.mappingIn ++= optimizer.mapping
      dse.run(block)
    }

    msg("Starting traversal PIR Generation")
    generateHeader()
    generateGlobals()
    block
  }

  def generateHeader() {
    emit("import pir.graph")
    emit("import pir.graph._")
    emit("import pir.graph.enums._")
    emit("import pir.codegen._")
    emit("import pir.plasticine.config._")
    emit("import pir.Design")
    emit("import pir.misc._")
    emit("import pir.PIRApp")
    emit("")
    open(s"""object ${Config.name}Design extends PIRApp {""")
    //emit(s"""override val arch = SN_4x4""")
    open(s"""def main(args: String*)(top:Top) = {""")
  }

  def generateFooter() {
    emit(s"")
    close("}")
    close("}")
  }

  def generateGlobals() {
    val (mcs, buses) = globals.partition{case mc:MemoryController => true; case _ => false}
    buses.filterNot(_.isInstanceOf[PIRDRAMBus]).foreach(emitComponent _)
    mcs.foreach(emitComponent _)
  }

  override protected def postprocess[S:Staged](block: Block[S]): Block[S] = {
    generateFooter()
    msg("Done.")
    val nCUs = cus.values.flatten.filter{cu => cu.allStages.nonEmpty || cu.isDummy }.size
    msg(s"NUMBER OF CUS: $nCUs")
    //sys.exit()
    block
  }

  def emitAllStages(cu: CU) {
    var i = 1
    var r = 1
    def emitStages(stages: Iterable[Stage]) = stages.foreach{
      case MapStage(op,inputs,outputs) =>
        val ins = inputs.map(quote(_)).mkString(", ")
        val outs = outputs.map(quote(_)).mkString(", ")
        emit(s"""Stage(stage($i), operands=List($ins), op=$op, results=List($outs))""")
        i += 1

      case ReduceStage(op,init,in,acc) =>
        emit(s"""val (rs$r, ${quote(acc)}) = Stage.reduce(op=$op, init=${quote(init)})""")
        allocatedReduce += acc
        r += 1
    }

    emit(s"var stage: List[Stage] = Nil")

    if (cu.controlStages.nonEmpty && genControlLogic) {
      i = 0
      val nCompute = cu.controlStages.length
      emit(s"stage = ControlStages(${nCompute})")
      emitStages(cu.controlStages)
    }
    for ((srams,stages) <- cu.writeStages if stages.nonEmpty) {
      i = 1
      val nWrites  = stages.filter{_.isInstanceOf[MapStage]}.length
      emit(s"stage = stage0 +: WAStages(${nWrites}, ${srams.map(quote(_))})")
      emitStages(stages)
    }
    if (cu.computeStages.nonEmpty) {
      i = 1
      val nCompute = cu.computeStages.filter{_.isInstanceOf[MapStage]}.length
      emit(s"stage = stage0 +: Stages(${nCompute})")
      emitStages(cu.computeStages)
    }
  }
  //override def quote(s: Exp[_]): String = {

  def cuDeclaration(cu: CU) = {
    val parent = cu.parent.map(_.name).getOrElse("top")
    val deps = cu.deps.map{dep => dep.name }.mkString("List(", ", ", ")")

    s"""${quote(cu)}(name = "${cu.name}", parent=$parent, deps=$deps)"""
  }

  def preallocateRegisters(cu: CU) = cu.regs.foreach{
    case reg:TempReg        => emit(s"val ${quote(reg)} = CU.temp")
    case reg@AccumReg(init) => emit(s"val ${quote(reg)} = CU.accum(init = ${quote(init)})")
    case reg:ControlReg if genControlLogic => emit(s"val ${quote(reg)} = CU.ctrl")
    case _ => // No preallocation
  }

  def preallocateFeedbackRegs(cu: CU) = cu.regs.foreach{
    case reg@FeedbackAddrReg(mem) => emit(s"val ${quote(reg)} = CU.wtAddr(${quote(mem)})")
    case _ => //nothing
  }

  def generateCU(pipe: Exp[Any], cu: CU, suffix: String = "") {
    open(s"val ${cu.name} = ${cuDeclaration(cu)} { implicit CU => ")
    emit(s"val stage0 = CU.emptyStage")
    preallocateRegisters(cu)                // Includes scalar inputs/outputs, temps, accums
    cu.cchains.foreach(emitComponent(_))    // Allocate all counterchains
    cu.srams.foreach(emitComponent(_))      // Allocate all SRAMs
    preallocateFeedbackRegs(cu)             // Local write addresses

    emitAllStages(cu)

    close("}")
  }

  private def emitController(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    //case isControlNode(lhs) && cus.contains(lhs) =>
    case rhs if isControlNode(lhs) => //generateCU(lhs, cu)
      //emit(s"$lhs = $rhs")
    case _ => 
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = {
    emitController(lhs, rhs)
    rhs match {
      case Hwblock(func) =>
        emitBlock(func)

      case UnitPipe(en, func) =>

      case ParallelPipe(en, func) => 
        emitBlock(func)

      case OpForeach(cchain, func, iters) =>
        emitNestedLoop(cchain, iters){ emitBlock(func) }

      case OpReduce(cchain, accum, map, load, reduce, store, ident, fold, rV, iters) =>
        emitNestedLoop(cchain, iters){
          visitBlock(map)
          visitBlock(load)
          visitBlock(reduce)
          emitBlock(store)
        }

      case OpMemReduce(cchainMap,cchainRed,accum,map,loadRes,loadAcc,reduce,storeAcc,ident,fold,rV,itersMap,itersRed) =>
        emitNestedLoop(cchainMap, itersMap){
          visitBlock(map)
          emitNestedLoop(cchainRed, itersRed){
            visitBlock(loadRes)
            visitBlock(loadAcc)
            visitBlock(reduce)
            visitBlock(storeAcc)
          }
        }

      case _ => super.emitNode(lhs, rhs)
    }
  }

  def quoteInCounter(reg: LocalScalar) = reg match {
    case reg:ScalarIn => s"CU.scalarIn(stage0, ${quote(reg)}).out"
    case reg:ConstReg => s"""${quote(reg)}.out"""
  }

  def emitComponent(x: Any): Unit = x match {
    case CChainCopy(name, inst, owner) =>
      emit(s"""val $name = CounterChain.copy(${owner.name}, "$name")""")

    case CChainInstance(name, ctrs) =>
      for (ctr <- ctrs) emitComponent(ctr)
      val ctrList = ctrs.map(_.name).mkString(", ")
      emit(s"""val $name = CounterChain(name = "$name", $ctrList)""")

    case UnitCChain(name) =>
      emit(s"""val $name = CounterChain(name = "$name", (Const("0i"), Const("1i"), Const("1i")))""")

    case ctr@CUCounter(start, end, stride) =>
      emit(s"""val ${ctr.name} = (${quoteInCounter(start)}, ${quoteInCounter(end)}, ${quoteInCounter(stride)}) // Counter""")

    case sram: CUMemory =>
      var decl = s"""val ${sram.name} = ${quote(sram.mode)}(size = ${sram.size}"""

      sram.writeCtrl match {
        case Some(cchain) => decl += s""", writeCtr = ${cchain.name}(0)"""
        case None if sram.mode != SRAMMode => // Ok
        case None => throw new Exception(s"No write controller defined for $sram")
      }

      sram.banking match {
        case Some(banking) => decl += s", banking = $banking"
        case None => throw new Exception(s"No banking defined for $sram")
      }

      if (sram.bufferDepth > 1 && sram.mode != FIFOMode) {
        var buffering = s", buffering = MultiBuffer(${sram.bufferDepth}"
        sram.swapRead match {
          case Some(cchain) => buffering += s", swapRead = ${cchain.name}(0)"
          case None => throw new Exception(s"No swap read controller defined for $sram")
        }

        sram.swapWrite match {
          case Some(cchain) => buffering += s", swapWrite = ${cchain.name}(0)"
          case None if sram.mode != SRAMMode => // Ok
          case None => throw new Exception(s"No swap write controller defined for $sram")
        }
        decl += s"$buffering)"
      }
      else if (sram.mode != FIFOMode) {
        decl += ", buffering = SingleBuffer()"
      }
      decl += ")"

      sram.vector match {
        case Some(LocalVectorBus) => // Nothing?
        case Some(vec) => decl += s""".wtPort(${quote(vec)})"""
        case None => throw new Exception(s"Memory $sram has no vector defined")
      }
      sram.readAddr match {
        case Some(_:CounterReg | _:ConstReg) => decl += s""".rdAddr(${quote(sram.readAddr.get)})"""
        case Some(_:ReadAddrWire) =>
        case None if sram.mode == FIFOMode => // ok
        case addr => throw new Exception(s"Disallowed memory read address in $sram: $addr")
      }
      sram.writeAddr match {
        case Some(_:CounterReg | _:ConstReg) => decl += s""".wtAddr(${quote(sram.writeAddr.get)})"""
        case Some(_:WriteAddrWire | _:FeedbackAddrReg) =>
        case None if sram.mode != SRAMMode => // ok
        case addr => throw new Exception(s"Disallowed memory write address in $sram: $addr")
      }
      if (sram.mode != SRAMMode) {
        sram.writeStart match {
          case Some(start) => decl += s""".wtStart(${quoteInCounter(start)})"""
          case _ =>
        }
        sram.writeEnd match {
          case Some(end) => decl += s""".wtEnd(${quoteInCounter(end)})"""
          case _ =>
        }
      }


      emit(decl)

    case mc@MemoryController(name,region,mode) =>
      emit(s"val ${quote(mc)} = MemoryController($mode, ${quote(region)})")

    case mem: OffChip   => emit(s"""val ${quote(mem)} = OffChip("${mem.name}")""")
    case bus: InputArg  => emit(s"""val ${quote(bus)} = ArgIn("${bus.name}")""")
    case bus: OutputArg => emit(s"""val ${quote(bus)} = ArgOut("${bus.name}")""")
    case bus: ScalarBus => emit(s"""val ${quote(bus)} = Scalar("${bus.name}")""")
    case bus: VectorBus => emit(s"""val ${quote(bus)} = Vector("${bus.name}")""")

    case x => throw new Exception(s"Don't know how to generate PIR component $x")
  }

  override def quote(x: Symbol):String = s"$x"

  def quote(mode: LocalMemoryMode): String = mode match {
    case SRAMMode => "SRAM"
    case FIFOMode => "FIFO"
    case FIFOOnWriteMode => "SemiFIFO"
  }

  def quote(sram: CUMemory): String = sram.name

  def quote(x: GlobalComponent): String = x match {
    case OffChip(name)       => s"${name}_oc"
    case mc:MemoryController => s"${mc.name}_mc"
    case InputArg(name)      => s"${name}_argin"
    case OutputArg(name)     => s"${name}_argout"
    case LocalVectorBus      => "local"
    case PIRDRAMDataIn(mc)      => s"${quote(mc)}.vdata"
    case PIRDRAMDataOut(mc)     => s"${quote(mc)}.vdata"
    case PIRDRAMOffset(mc)      => s"${quote(mc)}.ofs"
    case PIRDRAMLength(mc)      => s"${quote(mc)}.len"
    case PIRDRAMAddress(mc)     => s"${quote(mc)}.addrs"
    case bus:ScalarBus       => s"${bus.name}_scalar"
    case bus:VectorBus       => s"${bus.name}_vector"
  }

  def quote(cu: CU): String = cu.style match {
    case UnitCU if cu.allStages.isEmpty && !cu.isDummy => "Sequential" // outer unit is "Sequential"
    case UnitCU       => "UnitPipeline"
    case StreamCU if cu.allStages.isEmpty && !cu.isDummy => "StreamController"
    case StreamCU     => "StreamPipeline"
    case UnitStreamCU if cu.allStages.isEmpty && !cu.isDummy => "StreamController" // TODO
    case UnitStreamCU => "StreamPipeline" // TODO
    case PipeCU       => "Pipeline"
    case MetaPipeCU   => "MetaPipeline"
    case SequentialCU => "Sequential"
  }

  def quote(reg: LocalComponent): String = reg match {
    case ConstReg(c)             => s"""Const("$c")"""              // Constant
    case CounterReg(cchain, idx) => s"${cchain.name}($idx)"         // Counter
    case ValidReg(cchain,idx)    => s"${cchain.name}.valids($idx)"  // Counter valid

    case WriteAddrWire(mem)      => s"${quote(mem)}.writeAddr"      // Write address wire
    case ReadAddrWire(mem)       => s"${quote(mem)}.readAddr"       // Read address wire
    case FeedbackAddrReg(mem)    => s"wr${reg.id}"                  // Local write address register
    case FeedbackDataReg(mem)    => quote(mem)                      // Local write data register
    case SRAMReadReg(mem)        => quote(mem)                      // SRAM read

    case reg:ReduceReg           => s"rr${reg.id}"                  // Reduction register
    case reg:AccumReg            => s"ar${reg.id}"                  // After preallocation
    case reg:TempReg             => s"tr${reg.id}"                  // Temporary register
    case reg:ControlReg          => s"cr${reg.id}"                  // Control register

    case ScalarIn(bus)           => quote(bus)                      // Scalar input
    case ScalarOut(bus)          => quote(bus)                      // Scalar output
    case VectorIn(bus)           => quote(bus)                      // Vector input
    case VectorOut(bus)          => quote(bus)                      // Vector output
  }

  def quote(ref: LocalRef): String = ref match {
    case LocalRef(stage, reg: ConstReg)   => quote(reg)
    case LocalRef(stage, reg: CounterReg) => if (stage >= 0) s"CU.ctr(stage($stage), ${quote(reg)})" else quote(reg)
    case LocalRef(stage, reg: ValidReg)   => quote(reg)

    case LocalRef(stage, wire: WriteAddrWire)  => quote(wire)
    case LocalRef(stage, wire: ReadAddrWire)   => quote(wire)
    case LocalRef(stage, reg: FeedbackAddrReg) => s"CU.wtAddr(stage($stage), ${quote(reg)})"
    case LocalRef(stage, reg: FeedbackDataReg) => s"CU.store(stage($stage), ${quote(reg)})"

    case LocalRef(stage, reg: ReduceReg) if allocatedReduce.contains(reg) => quote(reg)
    case LocalRef(stage, reg: ReduceReg)   => s"CU.reduce(stage($stage))"
    case LocalRef(stage, reg: AccumReg)    => s"CU.accum(stage($stage), ${quote(reg)})"
    case LocalRef(stage, reg: TempReg)     => s"CU.temp(stage($stage), ${quote(reg)})"
    case LocalRef(stage, reg: ControlReg)  => s"CU.ctrl(stage($stage), ${quote(reg)})"
    case LocalRef(stage, reg: SRAMReadReg) => if (stage >= 0) s"CU.load(stage($stage), ${quote(reg)})" else s"${quote(reg)}.load"

    case LocalRef(stage, reg: ScalarIn)  => s"CU.scalarIn(stage($stage), ${quote(reg)})"
    case LocalRef(stage, reg: ScalarOut) => s"CU.scalarOut(stage($stage), ${quote(reg)})"
    case LocalRef(stage, reg: VectorIn)  => s"CU.vecIn(stage($stage), ${quote(reg)})"
    case LocalRef(stage, reg: VectorOut) => s"CU.vecOut(stage($stage), ${quote(reg)})"
  }

  override protected def quoteConst(c: Const[_]): String = (c.tp, c) match {
    case _ => s"Const($c)"
  }
}
