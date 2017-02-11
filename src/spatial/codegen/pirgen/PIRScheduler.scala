package spatial.codegen.pirgen
import spatial.{SpatialExp,SpatialConfig}
import spatial.api._

import scala.collection.mutable

trait PIRScheduler extends PIRTraversal {
  val IR: SpatialExp with PIRCommonExp
  import IR._

  override val name = "PIR Scheduler"
  override val recurse = Always

  val mappingIn  = mutable.HashMap[Symbol, PCU]()
  val mappingOut = mutable.HashMap[Symbol, CU]()

  override def process[S:Staged](b: Block[S]): Block[S] = {
    super.run(b)
    val cuMapping = mappingIn.keys.map{s =>

      dbg(s"${mappingIn(s)} -> ${mappingOut(s)}")

      mappingIn(s).asInstanceOf[ACU] -> mappingOut(s).asInstanceOf[ACU]
    }.toMap

    // Swap dependencies, parents, cchain owners from pcu to cu

    swapCUs(mappingOut.values, cuMapping)

    for ((k,v) <- cuMapping) {
      dbg(s"$k -> $v")
    }

    for (cu <- mappingOut.values) {
      dbg(s"")
      dbg(s"Generated CU: $cu")
      dbg(s"Counter chains: ")
      cu.cchains.foreach{cchain =>
        dbg(s"  $cchain")
      }

      if (cu.srams.nonEmpty) {
        dbg(s"SRAMs: ")
        for (sram <- cu.srams) {
          dbg(s"""  $sram [${sram.mode}] (sym: ${sram.mem}, reader: ${sram.reader})""")
          dbg(s"""    banking   = ${sram.banking.map(_.toString).getOrElse("N/A")}""")
          dbg(s"""    vector    = ${sram.vector.map(_.toString).getOrElse("N/A")}""")
          dbg(s"""    writeAddr = ${sram.writeAddr.map(_.toString).getOrElse("N/A")}""")
          dbg(s"""    readAddr  = ${sram.readAddr.map(_.toString).getOrElse("N/A")}""")
          dbg(s"""    start     = ${sram.writeStart.map(_.toString).getOrElse("N/A")}""")
          dbg(s"""    end       = ${sram.writeEnd.map(_.toString).getOrElse("N/A")}""")
          dbg(s"""    swapWrite = ${sram.swapWrite.map(_.toString).getOrElse("N/A")}""")
          dbg(s"""    swapRead  = ${sram.swapRead.map(_.toString).getOrElse("N/A")}""")
          dbg(s"""    writeCtrl = ${sram.writeCtrl.map(_.toString).getOrElse("N/A")}""")
        }
      }

      for (srams <- cu.writeStages.keys) {
        dbg(s"Generated write stages ($srams): ")
        cu.writeStages(srams).foreach(stage => dbg(s"  $stage"))
      }
      dbg("Generated compute stages: ")
      cu.computeStages.foreach(stage => dbg(s"  $stage"))

      dbg(s"CU global inputs:")
      globalInputs(cu).foreach{in => dbg(s"  $in") }
    }

    b
  }

  override protected def visit(lhs: Sym[_], rhs: Op[_]) = {
    if (isControlNode(lhs) && mappingIn.contains(lhs))
      schedulePCU(lhs, mappingIn(lhs))
  }

  def schedulePCU(pipe: Symbol, pcu: PCU) {
    val cu = pcu.copyToConcrete()

    dbg(s"\n\n\n")
    dbg(s"Scheduling $pipe CU: $pcu")
    for ((s,r) <- cu.regTable) {
      dbg(s"  $s -> $r")
    }
    dbg(s"  SRAMs:")
    for (sram <- pcu.srams) {
      dbg(s"""    $sram (sym: ${sram.mem}, reader: ${sram.reader})""")
    }
    dbg(s"  Write stages:")
    for ((k,v) <- pcu.writeStages) {
      dbg(s"    Memories: " + k.mkString(", "))
      for (stage <- v._2) dbg(s"      $stage")
    }
    dbg(s"  Compute stages:")
    for (stage <- pcu.computeStages) dbg(s"    $stage")

    val origRegs = cu.regs
    var writeStageRegs = cu.regs

    // --- Schedule write contexts
    for ((srams,grp) <- pcu.writeStages) {
      val writer = grp._1
      val stages = grp._2
      val ctx = WriteContext(cu, writer, srams)
      ctx.init()
      stages.foreach{stage => scheduleStage(stage, ctx) }

      writeStageRegs ++= cu.regs
      cu.regs = origRegs
    }

    // --- Schedule compute context
    val ctx = ComputeContext(cu)
    pcu.computeStages.foreach{stage => scheduleStage(stage, ctx) }
    cu.regs ++= writeStageRegs

    mappingOut += pipe -> cu
  }

  def scheduleStage(stage: PseudoStage, ctx: CUContext) = stage match {
    case DefStage(lhs@Def(rhs), isReduce) =>
      //dbg(s"""$lhs = $rhs ${if (isReduce) "[REDUCE]" else ""}""")
      if (isReduce) reduceNodeToStage(lhs,rhs,ctx)
      else          mapNodeToStage(lhs,rhs,ctx)

    case WriteAddrStage(mem, addr) =>
      //dbg(s"$mem @ $addr [WRITE]")
      writeAddrToStage(mem, addr, ctx)

    case FifoOnWriteStage(mem, start, end) =>
      //dbg(s"$mem [WRITE]")
      fifoOnWriteToStage(mem, start, end, ctx)

    case OpStage(op, ins, out, isReduce) =>
      //dbg(s"""$out = $op(${ins.mkString(",")}) [OP]""")
      opStageToStage(op, ins, out, ctx, isReduce)
  }

  // If addr is a counter or const, just returns that register back. Otherwise returns address wire
  def allocateAddrReg(sram: CUMemory, addr: Symbol, ctx: CUContext, write: Boolean, local: Boolean = false):LocalComponent = {
    val wire = if (write && local) FeedbackAddrReg(sram)
               else if (write)     WriteAddrWire(sram)
               else                ReadAddrWire(sram)

    val addrReg = ctx.reg(addr)
    propagateReg(addr, addrReg, wire, ctx)
  }

  def writeAddrToStage(mem: Symbol, addr: Symbol, ctx: CUContext, local: Boolean = false) {
    //dbg(s"Setting write address for " + ctx.memories(mem).mkString(", "))

    ctx.memories(mem).foreach{sram =>
      sram.writeAddr = Some(allocateAddrReg(sram, addr, ctx, write=true, local=local).asInstanceOf[WriteAddr]) //TODO
    }
  }
  def fifoOnWriteToStage(mem: Symbol, start: Option[Symbol], end: Option[Symbol], ctx: CUContext) {
    //dbg(s"Setting FIFO-on-write for " + ctx.memories(mem).mkString(", "))

    ctx.memories(mem).foreach{sram =>
      if (sram.mode != FIFOMode) sram.mode = FIFOOnWriteMode
      sram.writeAddr = None
      sram.writeCtrl = None
      sram.swapWrite = None

      //val parentCtr = ctx.cu.parent.flatMap{parent => parent.cchains.find{case _:UnitCChain | _:CChainInstance => true; case _ => false}}

      //sram.swapWrite = parentCtr.flatMap{ctr => ctx.cu.cchains.find(_.name == ctr.name) }

      // HACK: Raghu and Yaqi don't like unaligned loads
      //if (!SpatialConfig.checkBounds) {
      if (true) {
        sram.writeStart = None
        sram.writeEnd = None
      }
      else {
        start match {
          case Some(e) => ctx.reg(e) match {
            case x: LocalScalar => sram.writeStart = Some(x)
            case x => throw new Exception(s"Invalid FIFO-on-write start $x")
          }
          case None => // Do nothing
        }
        end match {
          case Some(e) => ctx.reg(e) match {
            case x: LocalScalar => sram.writeEnd = Some(x)
            case x => throw new Exception(s"Invalid FIFO-on-write start $x")
          }
          case None => // No end
        }
      }
    }
  }

  def bufferWrite(mem: Symbol, data: Symbol, isdim: Option[(Seq[Exp[Index]], Seq[Exp[Index]])], ctx: CUContext) {
    if (isReadInPipe(mem, ctx.pipe)) {
      val flatOpt = isdim.map{ case (a, dims) => flattenNDIndices(a, dims) }
      val addr = flatOpt.map(_._1)
      val addrStages = flatOpt.map(_._2).getOrElse(Nil)
      addrStages.foreach{stage => scheduleStage(stage, ctx) }
      addr.foreach{a => writeAddrToStage(mem, a, ctx, local=true) }

      // TODO: Should we allow multiple versions of local accumulator?
      ctx.memories(mem).foreach{sram =>
        propagateReg(data, ctx.reg(data), FeedbackDataReg(sram), ctx)
      }
    }
    // Push data out to global bus (even for local accumulators)
    if (ctx.isUnit) {
      val bus = CUScalar(quote(mem))
      globals += bus
      propagateReg(data, ctx.reg(data), ScalarOut(bus), ctx)
    }
    else {
      val bus = CUVector(quote(mem))
      globals += bus
      propagateReg(data, ctx.reg(data), VectorOut(bus), ctx)
    }
  }

  def allocateFifoPop(lhs: Symbol, fifo: Symbol, ctx: CUContext) = writersOf(fifo).head.ctrlNode match {
    case tx@Def(e:BurstLoad[_]) =>
      val dram = allocateDRAM(tx, e.dram, MemLoad)
      ctx.addReg(lhs, VectorIn(PIRDRAMDataIn(dram)))

    case x => ctx.memOption(fifo, lhs) match {
      case Some(sram) =>
        ctx.addReg(lhs, SRAMReadReg(sram))

      case None =>
        if (isUnitPipe(x)) {
          val bus = CUScalar(quote(fifo))
          globals += bus
          ctx.addReg(lhs, ScalarIn(bus))
        }
        else {
          val bus = CUVector(quote(fifo))
          globals += bus
          ctx.addReg(lhs, VectorIn(bus))
        }
    }
  }

  def allocateSRAMRead(lhs: Symbol, mem: Symbol, dim:Seq[Exp[Index]], is: Seq[Exp[Index]], ctx: CUContext) {
    val sram = ctx.mem(mem, lhs)
    val (addr, addrStages) = flattenNDIndices(is, dim)
    addrStages.foreach{stage => scheduleStage(stage, ctx) }
    sram.readAddr = Some(allocateAddrReg(sram, addr, ctx, write=false, local=true).asInstanceOf[ReadAddr]) //TODO
    ctx.addReg(lhs, SRAMReadReg(sram))
  }

  def allocateFifoPush(fifo: Symbol, data: Symbol, ctx: CUContext) = readersOf(fifo).head.ctrlNode match {
    case tx@Def(e:BurstStore[_]) =>
      val dram = allocateDRAM(tx, e.dram, MemStore)
      propagateReg(data, ctx.reg(data), VectorOut(PIRDRAMDataOut(dram)), ctx)

    case _ => bufferWrite(fifo,data,None,ctx)
  }

  // Cases: 1. Inner Accumulator (read -> write)
  //        2. Outer Accumulator (read -> write)
  //        3. Local register but not accumulator (e.g. write -> read)
  //        4. Scalar output (read globally)
  // (1, 2, and 3 are mutually exclusive)
  // - 1: Ignore. Inner reductions are handled differently
  // - 2: Update producer of data to have accumulator as output
  // - 3: Update reg to map to register of data (for later use in reads)
  // - 4: If any of first 3 options, add bypass data to scalar out, otherwise update producer
  def allocateRegWrite(writer: Symbol, reg: Symbol, data: Symbol, ctx: CUContext) {
    val isLocallyRead    = isReadInPipe(reg, ctx.pipe)
    val isLocallyWritten = isWrittenInPipe(reg, ctx.pipe, Some(writer)) // Always true?
    val isInnerAcc = isInnerAccum(reg) && isLocallyRead && isLocallyWritten
    val isOuterAcc = isAccum(reg) && !isInnerAcc && isLocallyRead && isLocallyWritten
    val isRemotelyRead = isReadOutsidePipe(reg, ctx.pipe)

    val Def(d) = writer

    //dbg(s"[REG WRITE] $writer = $d")
    //dbg(s"  Reg = $reg, data = $data")
    //dbg(s"  localRead:$isLocallyRead, localWrite:$isLocallyWritten, innerAcc:$isInnerAcc, outerAcc:$isOuterAcc, remoteRead:$isRemotelyRead")

    if (isOuterAcc) { // Case 2
      val out = ctx.cu.getOrElse(reg){ allocateLocal(reg, ctx.pipe) }
      propagateReg(data, ctx.reg(data), out, ctx)
    }
    else if (!isInnerAcc) { // Case 3
      ctx.addReg(reg, ctx.reg(data))
    }
    if (isRemotelyRead) {
      // Handle registers for cross-lane accumulation specially
      val start = if (isInnerAcc) ctx.reg(data) else ctx.reg(reg)
      if (isArgOut(reg)) {
        val bus = OutputArg(quote(reg))
        globals += bus
        propagateReg(reg, start, ScalarOut(bus), ctx)
      }
      else if (ctx.isUnit || isInnerAcc) {
        val bus = CUScalar(quote(reg))
        globals += bus
        propagateReg(reg, start, ScalarOut(bus), ctx)
      }
      else {
        val bus = CUVector(quote(reg))
        globals += bus
        propagateReg(reg, start, VectorOut(bus), ctx)
      }
    }
  }


  def mapNodeToStage(lhs: Symbol, rhs: Def, ctx: CUContext) = rhs match {
    // --- Reads
    case FIFODeq(fifo, en, zero)     => allocateFifoPop(lhs, fifo, ctx)
    //case Par_pop_fifo(EatAlias(fifo), en) => allocateFifoPop(lhs, fifo, ctx)

    case SRAMLoad(mem, dim, is, ofs)     => allocateSRAMRead(lhs, mem, dim, is, ctx)
    //case Par_sram_load(EatAlias(mem), addr) => allocateSRAMRead(lhs, mem, addr, ctx)

    case ListVector(elems) => ctx.addReg(lhs, ctx.reg(elems.head))
    case VectorApply(vec, idx) => //TODO: is VecApply still a thing?
      if (idx != 0) throw new Exception("Expected parallelization of 1 in inner loop in PIR gen")
      ctx.addReg(lhs, ctx.reg(vec))

    case RegRead(reg) =>
      val input = ctx.cu.getOrElse(reg){ allocateLocal(reg, ctx.pipe, read=Some(lhs)) }
      ctx.addReg(lhs, input)

    // --- Writes
    // Only for the data, not for the address, unless the memory is a local accumulator
    // TODO: Support enables!
    case FIFOEnq(fifo, data, en)          => allocateFifoPush(fifo, data, ctx)
    //case Par_push_fifo(EatAlias(fifo), datas, ens, _) => allocateFifoPush(fifo, datas, ctx)

    case SRAMStore(mem, dims, is, ofs, data, en)        => bufferWrite(mem,data,Some(is, dims),ctx)
    //case ParSRAMStore(mem), addrs, datas, ens) => bufferWrite(mem,datas,Some(addrs),ctx)

    case RegWrite(reg, data, en) => allocateRegWrite(lhs, reg, data, ctx)

    // --- Constants
    case c if isConstant(lhs) => ctx.cu.getOrElse(lhs){ allocateLocal(lhs, ctx.pipe) }

    // --- All other ops
    case d => nodeToOp(d) match {
      case Some(op) =>
        val inputs = syms(rhs)
        opStageToStage(op, inputs, lhs, ctx, false)

      case None => stageWarn(s"No ALU operation known for $lhs = $rhs")
    }
  }

  def reduceNodeToStage(lhs: Symbol, rhs: Def, ctx: CUContext) = nodeToOp(rhs) match {
    case Some(op) => opStageToStage(op, syms(rhs), lhs, ctx, true)
    case _ => stageWarn(s"No ALU reduce operation known for $lhs = $rhs")
  }

  def stageWarn(msg:String) = {
    throw new Exception(s"$msg")
  }

  def opStageToStage(op: PIROp, ins: List[Symbol], out: Symbol, ctx: CUContext, isReduce: Boolean) {
    if (isReduce) {
      // By convention, the inputs to the reduction tree is the first argument to the node
      // This input must be in the previous stage's reduction register
      // Ensure this either by adding a bypass register for raw inputs or changing the output
      // of the previous stage from a temporary register to the reduction register
      //dbg(s"[REDUCE] $op, ins = $ins, out = $out")

      val input = ins.head
      val accum = ins.last //TODO
      //val accum = aliasOf(ins.last)
      val inputReg = ctx.reg(input)
      val usedInput = propagateReg(input, inputReg, ReduceReg(), ctx)
      val zero = accum match {
        case Def(RegRead(reg)) => ConstReg(extractConstant(resetValue(reg)))
        case _ => ConstReg("0l")
      }
      val acc = ReduceReg()
      val stage = ReduceStage(op, zero, ctx.refIn(usedInput), acc)
      ctx.addReg(out, acc)
      ctx.addStage(stage)
    }
    else if (op == PIRBypass) {
      assert(ins.length == 1)
      propagateReg(ins.head, ctx.reg(ins.head), ctx.reg(out), ctx)
    }
    else {
      val inputRegs = ins.map{in => ctx.reg(in) }
      val isControlStage  = inputRegs.nonEmpty && !inputRegs.exists{reg => !isControl(reg) }
      val hasControlLogic = inputRegs.nonEmpty && inputRegs.exists{reg => isControl(reg) }

      if (isControlStage) {
        val n = ctx.controlStageNum
        val inputs = inputRegs.map{reg => ctx.refIn(reg, n) }
        val output = ctx.cu.getOrElse(out){ ControlReg() }
        val stage = MapStage(op, inputs, List(ctx.refOut(output, n)))
        ctx.addControlStage(stage)
      }
      // HACK: Skip control logic generation for now...
      else if (hasControlLogic && op == PIRALUMux) {
        val skip = inputRegs.drop(1).find{case _:ConstReg => false; case _ => true}
        ctx.addReg(out, skip.getOrElse(inputRegs.drop(1).head))
      }
      else if (hasControlLogic) {
        throw new Exception("Cannot skip control logic...")
      }
      else {
        val inputs = inputRegs.map{reg => ctx.refIn(reg) }
        val output = ctx.cu.getOrElse(out){ TempReg() }
        val stage = MapStage(op, inputs, List(ctx.refOut(output)))
        ctx.addStage(stage)
      }
    }
  }
}
