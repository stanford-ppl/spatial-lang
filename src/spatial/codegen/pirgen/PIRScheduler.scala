package spatial.codegen.pirgen
import spatial.{SpatialExp,SpatialConfig}
import spatial.api._

import scala.collection.mutable

trait PIRScheduler extends PIRTraversal {
  val IR: SpatialExp with PIRCommonExp
  import IR.{println => _, _}

  override val name = "PIR Scheduler"
  override val recurse = Always

  implicit val mappingIn  = mutable.HashMap[Symbol, List[PCU]]()
  val mappingOut = mutable.HashMap[Symbol, List[CU]]()

  override protected def postprocess[S:Staged](block: Block[S]): Block[S] = {
    val cuMapping:Map[ACU, ACU] = mappingIn.keys.flatMap{s =>

      dbg(s"${mappingIn(s)} -> ${mappingOut(s)}")

      mappingIn(s).zip(mappingOut(s)).map { case (pcu, cu) =>
        pcu.asInstanceOf[ACU] -> cu.asInstanceOf[ACU]
      }
    }.toMap

    // Swap dependencies, parents, cchain owners from pcu to cu

    swapCUs(mappingOut.values.flatten, cuMapping)

    for ((k,v) <- cuMapping) {
      dbgs(s"$k -> $v")
    }

    for (cu <- mappingOut.values.flatten) {
      dbgblk(s"Generated CU: $cu") {
        dbgblk(s"Counter chains: ") {
          cu.cchains.foreach{cchain => dbgs(s"$cchain") }
        }

        if (cu.srams.nonEmpty) {
          dbgblk(s"SRAMs: ") {
            for (sram <- cu.srams) {
              dbgs(s"""$sram [${sram.mode}] (sym: ${sram.mem}, reader: ${sram.reader})""")
              dbgs(s"""  banking   = ${sram.banking.map(_.toString).getOrElse("N/A")}""")
              dbgs(s"""  writePort    = ${sram.writePort.map(_.toString).getOrElse("N/A")}""")
              dbgs(s"""  readPort    = ${sram.readPort.map(_.toString).getOrElse("N/A")}""")
              dbgs(s"""  writeAddr = ${sram.writeAddr.map(_.toString).getOrElse("N/A")}""")
              dbgs(s"""  readAddr  = ${sram.readAddr.map(_.toString).getOrElse("N/A")}""")
              dbgs(s"""  start     = ${sram.writeStart.map(_.toString).getOrElse("N/A")}""")
              dbgs(s"""  end       = ${sram.writeEnd.map(_.toString).getOrElse("N/A")}""")
              dbgs(s"""  swapWrite = ${sram.swapWrite.map(_.toString).getOrElse("N/A")}""")
              dbgs(s"""  swapRead  = ${sram.swapRead.map(_.toString).getOrElse("N/A")}""")
              dbgs(s"""  writeCtrl = ${sram.writeCtrl.map(_.toString).getOrElse("N/A")}""")
            }
          }
        }

        for (srams <- cu.writeStages.keys) {
          dbgs(s"Generated write stages ($srams): ")
          cu.writeStages(srams).foreach(stage => dbgs(s"  $stage"))
        }
        for (srams <- cu.readStages.keys) {
          dbgs(s"Generated read stages ($srams): ")
          cu.readStages(srams).foreach(stage => dbgs(s"  $stage"))
        }
        dbgs("Generated compute stages: ")
        cu.computeStages.foreach(stage => dbgs(s"  $stage"))

        dbgs(s"CU global inputs:")
        globalInputs(cu).foreach{in => dbgs(s"  $in") }
      }
    }
    block
  }

  override protected def visit(lhs: Sym[_], rhs: Op[_]) = {
    if ((isControlNode(lhs) || isSRAM(lhs) || isFringe(lhs)) && mappingIn.contains(lhs))
      schedulePCU(lhs, mappingIn(lhs))
  }

  def schedulePCU(sym: Symbol, pcus: List[PCU]):Unit = {
    mappingOut += sym -> pcus.map { pcu =>
      dbgblk(s"Scheduling $sym CU: $pcu") {
        dbgpcu(pcu)

        val cu = pcu.copyToConcrete()

        val origRegs = cu.regs
        var writeStageRegs = cu.regs
        var readStageRegs = cu.regs

        // --- Schedule write contexts
        for ((srams, (writer, stages)) <- pcu.writeStages) {
          val ctx = WriteContext(cu, writer, srams)
          ctx.init()
          stages.foreach{stage => scheduleStage(stage, ctx) }

          writeStageRegs ++= cu.regs
          cu.regs = origRegs
        }
        // --- Schedule read contexts
        for ((srams, (reader, stages)) <- pcu.readStages) {
          val ctx = ReadContext(cu, reader, srams)
          ctx.init()
          stages.foreach{stage => scheduleStage(stage, ctx) }

          readStageRegs ++= cu.regs //TODO: should writeStageRegs been removed?
          cu.regs = origRegs
        }

        // --- Schedule compute context
        val ctx = ComputeContext(cu)
        pcu.computeStages.foreach{stage => scheduleStage(stage, ctx) }
        cu.regs ++= writeStageRegs
        cu.regs ++= readStageRegs
        cu
      }

    }
  }

  def scheduleStage(stage: PseudoStage, ctx: CUContext):Unit = dbgblk(s"Scheduling stage:$stage") {
    stage match {
      case DefStage(lhs@Def(rhs), isReduce) =>
        //dbg(s"""$lhs = $rhs ${if (isReduce) "[REDUCE]" else ""}""")
        if (isReduce)   reduceNodeToStage(lhs,rhs,ctx)
        else            mapNodeToStage(lhs,rhs,ctx)

      case AddrStage(mem, addr) =>
        //dbg(s"$mem @ $addr [WRITE]")
        addrToStage(mem, addr, ctx)

      //case FifoOnWriteStage(mem, start, end) =>
        ////dbg(s"$mem [WRITE]")
        //fifoOnWriteToStage(mem, start, end, ctx)

      case OpStage(op, ins, out, isReduce) =>
        //dbg(s"""$out = $op(${ins.mkString(",")}) [OP]""")
        opStageToStage(op, ins, out, ctx, isReduce)
    }
  }

  // If addr is a counter or const, just returns that register back. Otherwise returns address wire
  def allocateAddrReg(sram: CUMemory, addr: Symbol, ctx: CUContext, local: Boolean = false):LocalComponent = {
    val wire = ctx match {
      case WriteContext(cu, pipe, srams) if local => FeedbackAddrReg(sram)
      case WriteContext(cu, pipe, srams) => WriteAddrWire(sram)
      case ReadContext(cu, pipe, srams) => ReadAddrWire(sram)
    }
    val addrReg = ctx.reg(addr)
    propagateReg(addr, addrReg, wire, ctx)
  }

  def addrToStage(mem: Symbol, addr: Symbol, ctx: CUContext, local: Boolean = false) {
    //dbg(s"Setting write address for " + ctx.memories(mem).mkString(", "))
    ctx.memories(mem).foreach{sram =>
      ctx match {
        case _:WriteContext =>
          sram.writeAddr = Some(allocateAddrReg(sram, addr, ctx, local=local).asInstanceOf[WriteAddr])
        case _:ReadContext =>
          sram.readAddr = Some(allocateAddrReg(sram, addr, ctx, local=local).asInstanceOf[ReadAddr])
      }
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

  //def bufferWrite(mem: Symbol, data: Symbol, isdim: Option[(Seq[Exp[Index]], Seq[Exp[Index]])], ctx: CUContext) {
    //if (isReadInPipe(mem, ctx.pipe)) { //FIXME: No longer will be true
      //assert(false, "All reads are remote writes now!")
      //val flatOpt = isdim.map{ case (a, dims) => flattenNDIndices(a, dims) }
      //val addr = flatOpt.map(_._1)
      //val addrStages = flatOpt.map(_._2).getOrElse(Nil)
      //addrStages.foreach{stage => scheduleStage(stage, ctx) }
      //addr.foreach{a => addrToStage(mem, a, ctx, local=true) }

      //// TODO: Should we allow multiple versions of local accumulator?
      //ctx.memories(mem).foreach{sram =>
        //propagateReg(data, ctx.reg(data), FeedbackDataReg(sram), ctx)
      //}
    //}
    // Push data out to global bus (even for local accumulators)
    //mappingIn(mem).zipWithIndex.foreach { case (sramCU, i) =>
      //if (ctx.isUnit) {
        //val bus = CUScalar(s"${quote(mem)}_${i}_wt")
        //propagateReg(data, ctx.reg(data), ScalarOut(bus), ctx)
      //} else {
        //val bus = CUVector(s"${quote(mem)}_${i}_wt") // All writes to sram are using vector bus
        //propagateReg(data, ctx.reg(data), VectorOut(bus), ctx)
      //}
    //}
  //}

  //def allocateSRAMRead(lhs: Symbol, mem: Symbol, dim:Seq[Exp[Index]], is: Seq[Exp[Index]], ctx: CUContext) {
    //val dispatch = dispatchOf(lhs, mem).head
    //if (ctx.isUnit) {
      //val bus = CUScalar(s"${quote(mem)}_${dispatch}_rd")
      //ctx.addReg(lhs, ScalarIn(bus))
      ////propagateReg(lhs, ScalarIn(bus), ctx.reg(lhs), ctx)
    //} else {
      //val bus = CUVector(s"${quote(mem)}_${dispatch}_rd")
      //ctx.addReg(lhs, VectorIn(bus))
      //propagateReg(lhs, VectorIn(bus), ctx.reg(lhs), ctx)
    //}
    //val sram = ctx.mem(mem, lhs)
    //val (addr, addrStages) = flattenNDIndices(is, dim)
    //addrStages.foreach{stage => scheduleStage(stage, ctx) }
    //sram.readAddr = Some(allocateAddrReg(sram, addr, ctx, local=true).asInstanceOf[ReadAddr]) //TODO
    //ctx.addReg(lhs, SRAMReadReg(sram))
  //}

  //def allocateFifoPush(fifo: Symbol, data: Symbol, ctx: CUContext) = readersOf(fifo).head.ctrlNode match {
    ///*case tx@Def(e:BurstStore[_]) =>
      //val dram = allocateDRAM(tx, e.dram, MemStore)
      //propagateReg(data, ctx.reg(data), VectorOut(PIRDRAMDataOut(dram)), ctx)*/

    //case _ => bufferWrite(fifo,data,None,ctx)
  //}

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

    dbg(s"allocateRegWrite${ctx.cu}, $reg readersOf($reg)=${readersOf(reg).mkString(",")}")
    if (isOuterAcc) { // Case 2
      val out = ctx.cu.getOrElseUpdate(reg){ allocateLocal(reg) }
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
      else if (ctx.isUnit) {
        val bus = CUScalar(quote(reg))
        globals += bus
        propagateReg(reg, start, ScalarOut(bus), ctx)
      }
      else if (isInnerAcc) {
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
    case ParLocalReader(reads) =>
      if (usersOf(lhs).nonEmpty) {
        decompose(lhs).foreach { case dreader =>
          assert(ctx.getReg(lhs).nonEmpty, s"reader: ${qdef(dreader)} was not allocated in ${ctx.cu} during allocation")
        }
      }

    case ParLocalWriter(writes) =>
      val (mem, value, addrs, ens) = writes.head 
      value.foreach { data =>
        decompose(data).zip(decompose(lhs)).foreach { case (ddata, dwriter) =>
          assert(ctx.getReg(dwriter).nonEmpty, s"writer: ${qdef(dwriter)} was not allocated in ${ctx.cu} during allocation")
          propagateReg(ddata, ctx.cu.getOrElseUpdate(ddata)(const(ddata)), ctx.reg(dwriter), ctx)
        }
      }

    case ListVector(elems) => 
      ctx.addReg(lhs, ctx.reg(elems.head))

    case VectorApply(vec, idx) =>
      if (idx != 0) throw new Exception("Expected parallelization of 1 in inner loop in PIR gen")
      ctx.addReg(lhs, ctx.reg(vec))

    case SimpleStruct(elems) =>
      decompose(lhs).foreach { elem => ctx.addReg(elem, allocateLocal(lhs)) }

    // --- Constants
    case c if isConstant(lhs) => ctx.cu.getOrElseUpdate(lhs){ ConstReg(extractConstant(lhs)) }

    // --- All other ops
    case d => nodeToOp(d) match {
      case Some(op) =>
        val inputs = rhs.expInputs
        opStageToStage(op, inputs, lhs, ctx, false)

      case None => warn(s"No ALU operation known for $lhs = $rhs")
    }
  }

  def reduceNodeToStage(lhs: Symbol, rhs: Def, ctx: CUContext) = nodeToOp(rhs) match {
    case Some(op) => opStageToStage(op, syms(rhs), lhs, ctx, true)
    case _ => warn(s"No ALU reduce operation known for $lhs = $rhs")
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
        val output = ctx.cu.getOrElseUpdate(out){ ControlReg() }
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
        val output = ctx.cu.getOrElseUpdate(out){ TempReg() }
        val stage = MapStage(op, inputs, List(ctx.refOut(output)))
        ctx.addStage(stage)
      }
    }
  }
}
