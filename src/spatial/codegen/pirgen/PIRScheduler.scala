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

    dbgs(s"\n\n//----------- Finishing Scheduling ------------- //")
    for (cu <- mappingOut.values.flatten) {
      dbgcu(cu)
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

  def mapNodeToStage(lhs: Symbol, rhs: Def, ctx: CUContext) = rhs match {
    // --- Reads
    case ParLocalReader(reads) =>
      if (usersOf(lhs).nonEmpty) {
        decompose(lhs).foreach { case dreader =>
          assert(ctx.getReg(lhs).nonEmpty, s"reader: ${qdef(dreader)} was not allocated in ${ctx.cu} during allocation")
        }
      }

    case GetDRAMAddress(dram) => // equivalent to RegRead 

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
