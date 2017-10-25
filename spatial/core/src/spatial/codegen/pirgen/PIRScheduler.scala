package spatial.codegen.pirgen

import argon.core._
import argon.nodes._
import spatial.aliases._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._

import scala.collection.mutable

trait PIRScheduler extends PIRTraversal {
  override val name = "PIR Scheduler"
  override val recurse = Always

  def mappingIn:mutable.Map[Expr, List[PCU]]
  def mappingOut:mutable.Map[Expr, List[CU]]

  override protected def postprocess[S:Type](block: Block[S]): Block[S] = {
    val cuMapping:Map[ACU, ACU] = mappingIn.keys.flatMap{s =>

      dbgs(s"${mappingIn(s)} -> ${mappingOut(s)}")

      mappingIn(s).zip(mappingOut(s)).map { case (pcu, cu) =>
        pcu.asInstanceOf[ACU] -> cu.asInstanceOf[ACU]
      }
    }.toMap

    // Swap dependencies, parents, cchain owners from pcu to cu

    swapCUs(cuMapping)

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

  def schedulePCU(sym: Expr, pcus: List[PCU]):Unit = {
    mappingOut += sym -> pcus.map { pcu =>
      dbgblk(s"Scheduling $sym CU: $pcu") {
        dbgpcu(pcu)

        val cu = pcu.copyToConcrete()

        val origRegs = cu.regs
        var writeStageRegs = cu.regs
        var readStageRegs = cu.regs

        // --- Schedule write contexts
        val wctx = WriteContext(cu)
        wctx.init()
        pcu.writeStages.foreach{stage => scheduleStage(stage, wctx) }

        writeStageRegs ++= cu.regs
        cu.regs = origRegs
        // --- Schedule read contexts
        val rctx = ReadContext(cu)
        rctx.init()
        pcu.readStages.foreach{stage => scheduleStage(stage, rctx) }

        readStageRegs ++= cu.regs //TODO: should writeStageRegs been removed?
        cu.regs = origRegs

        // --- Schedule compute context
  // If addr is a counter or const, just returns that register back. Otherwise returns address wire
  //def allocateAddrReg(sram: CUMemory, addr: Expr, ctx: CUContext, local: Boolean = false):LocalComponent = {
  //}

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
        //dbgs(s"""$lhs = $rhs ${if (isReduce) "[REDUCE]" else ""}""")
        if (isReduce)   reduceNodeToStage(lhs,rhs,ctx)
        else            mapNodeToStage(lhs,rhs,ctx)

      case AddrStage(dmem, addr) =>
        //dbgs(s"$mem @ $addr [WRITE]")
        addrToStage(dmem, addr, ctx)

      case OpStage(op, ins, out, isReduce) =>
        //dbgs(s"""$out = $op(${ins.mkString(",")}) [OP]""")
        opStageToStage(op, ins, out, ctx, isReduce)
    }
  }

  def addrToStage(dmem: Expr, addr: Expr, ctx: CUContext) {
    ctx.memories(dmem).foreach { sram =>
      val wire = ctx match {
        case WriteContext(cu) => WriteAddrWire(sram)
        case ReadContext(cu) => ReadAddrWire(sram) 
      }
      val addrReg = addr match {
        case bound:Bound[_] => ctx.reg(addr)
        case const:Const[_] => ctx.reg(addr)
        case lhs@Def(rhs) => 
          mapNodeToStage(lhs, rhs, ctx)
          ctx.reg(addr)
      }
      val reg = propagateReg(addr, addrReg, wire, ctx)
      ctx match {
        case WriteContext(cu) => 
          dbgs(s"Setting write address for ${ctx.memories(dmem).mkString(", ")} to $reg")
          sram.writeAddr += reg
        case ReadContext(cu) => 
          dbgs(s"Setting read address for ${ctx.memories(dmem).mkString(", ")} to $reg")
          sram.readAddr += reg
      }
    }
  }

  def mapNodeToStage(lhs: Expr, rhs: Def, ctx: CUContext) = rhs match {
    // --- Reads
    case ParLocalReader(reads) =>
      if (usersOf(lhs).nonEmpty) {
        decompose(lhs).foreach { dreader =>
          assert(ctx.getReg(dreader).nonEmpty, s"reader: ${qdef(dreader)} was not allocated in ${ctx.cu} during allocation")
        }
      }

    case GetDRAMAddress(dram) => // equivalent to RegRead 

    case ParLocalWriter(writes) =>
      val (mem, value, addrs, ens) = writes.head 
      value.map(_.head).foreach { data =>
        dbgs(s"data:$data ddata:[${decompose(data).mkString(",")}] writer:$lhs dwriters:[${decompose(lhs).mkString(",")}]")
        decompose(data).zip(decompose(lhs)).foreach { case (ddata, dwriter) =>
          if (getRemoteReaders(mem, lhs).nonEmpty || isArgOut(mem)) {
            assert(ctx.getReg(dwriter).nonEmpty, s"writer: ${qdef(dwriter)} was not allocated in ${ctx.cu} during allocation")
            val ddataReg = allocateLocal(ctx.cu, ddata)
            dbgs(s"Propogating $ddataReg to $dwriter")
            propagateReg(ddata, ddataReg, ctx.reg(dwriter), ctx)
          }
        }
      }

    case ListVector(elems) => 
      assert(elems.size==1, s"ListVector elems size is not 1! elems:[${elems.mkString(",")}]")
      //ctx.addReg(lhs, ctx.reg(elems.head)) //TODO: is size of elems always be 1 for pir?
      decompose(lhs).zip(elems.flatMap(decompose)).foreach { case (lhs, elem) =>
        ctx.addReg(lhs, ctx.reg(elem))
      }

    case VectorApply(vec, idx) =>
      if (idx != 0) throw new Exception(s"Expected parallelization of 1 in inner loop in PIRgen idx=$idx")
      decompose(vec).zip(decompose(lhs)).foreach { case (vec, lhs) =>
        ctx.addReg(lhs, ctx.reg(vec))
      }

    case SimpleStruct(elems) =>
      decompose(lhs).foreach { elem => allocateLocal(ctx.cu, elem) }

    case FieldApply(struct, fieldName) =>
      val ele = lookupField(struct, fieldName).getOrElse(
        throw new Exception(s"Cannot lookup struct:$struct with fieldName:$fieldName in ${qdef(lhs)}"))
      ctx.addReg(lhs, ctx.reg(ele))

    // --- Constants
    case c if isConstant(lhs) => ctx.cu.getOrElseUpdate(lhs){ extractConstant(lhs) }

    case FixConvert(x) => ctx.addReg(lhs, ctx.reg(x))

    case FltConvert(x) =>
      if (lhs.tp==x.tp) ctx.addReg(lhs, ctx.reg(x))
      else throw new Exception(s"TODO: add FltConvert in hardware lhs:$lhs lhs.tp:${lhs.tp}, x:$x, x.tp:${x.tp}")

    // --- All other ops
    case d => nodeToOp(d) match {
      case Some(op) =>
        val inputs = rhs.expInputs
        opStageToStage(op, inputs, lhs, ctx, false)

      case None => warn(s"No ALU operation known for $lhs = $rhs")
    }
  }

  def reduceNodeToStage(lhs: Expr, rhs: Def, ctx: CUContext) = nodeToOp(rhs) match {
    case Some(op) => opStageToStage(op, syms(rhs), lhs, ctx, true)
    case _ => warn(s"No ALU reduce operation known for $lhs = $rhs")
  }

  def opStageToStage(op: PIROp, ins: Seq[Expr], out: Expr, ctx: CUContext, isReduce: Boolean) {
    if (isReduce) {
      // By convention, the inputs to the reduction tree is the first argument to the node
      // This input must be in the previous stage's reduction register
      // Ensure this either by adding a bypass register for raw inputs or changing the output
      // of the previous stage from a temporary register to the reduction register
      //dbgs(s"[REDUCE] $op, ins = $ins, out = $out")

      val inputs = mutable.ListBuffer[Expr]()
      val accums = mutable.ListBuffer[Expr]()
      ins.foreach {
        case in@Def(RegRead(reg)) if isAccum(reg) & isWrittenInPipe(reg, ctx.cu.pipe) => accums += in
        case in => inputs += in
      }
      assert(accums.size==1, s"accums:[${accums.mkString(",")}]")
      assert(inputs.size==1, s"inputs:[${inputs.mkString(",")}]")
      val accum = accums.head 
      val input = inputs.head

      val inputReg = ctx.reg(input)
      val usedInput = propagateReg(input, inputReg, ReduceReg(), ctx)
      val Def(RegRead(accumReg)) = accum
      val zero = extractConstant(resetValue(accumReg))
      val acc = ReduceReg()
      val accParents = mappingIn(parentOf(accumReg).get)
      assert(accParents.size==1)
      val accParent = accParents.head
      val stage = ReduceStage(op, zero, ctx.refIn(usedInput), acc, accParent=accParent)
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
        val skip = inputRegs.drop(1).find{case _:ConstReg[_] => false; case _ => true}
        ctx.addReg(out, skip.getOrElse(inputRegs.drop(1).head))
      }
      else if (hasControlLogic && op == PIRBitAnd) {
        ctx.addReg(out, inputRegs.find{reg => !isControl(reg)}.get)
      }
      else if (hasControlLogic) {
        bug("Could not skip control logic in operation: ")
        bug(s"$out = $op(" + ins.mkString(", ") + ") [reduce = " + isReduce + "]")
        bug(s"Control registers: " + inputRegs.filter(isControl).mkString(", "))
        sys.exit(-1)
      }
      else {
        val inputs = inputRegs.map{reg => ctx.refIn(reg) }
        val output = allocateLocal(ctx.cu, out)
        val stage = MapStage(op, inputs, List(ctx.refOut(output)))
        ctx.addStage(stage)
      }
    }
  }
}
