package spatial.codegen.pirgen

import argon.core._
import argon.nodes._
import spatial.aliases._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._

import scala.collection.mutable

class PIRScheduler(implicit val codegen:PIRCodegen) extends PIRTraversal {
  override val name = "PIR Scheduler"
  override val recurse = Always
  var IR = codegen.IR

  def cus = mappingOf.values.flatten.collect{ case cu:CU => cu }.toList

  override protected def process[S:Type](b: Block[S]): Block[S] = {
    val block = super.process(b)
    dbgs(s"\n\n//----------- Finishing Scheduling ------------- //")
    cus.foreach(dbgcu)
    dbgs(s"globals:${globals}")
    block
  }

  override protected def postprocess[S:Type](b: Block[S]): Block[S] = {
    dbgs(s"\n\n//----------- Finishing Scheduling ------------- //")
    dbgs(s"globals:${globals}")
    super.postprocess(b)
  }


  override protected def visit(lhs: Sym[_], rhs: Op[_]) = {
    mappingOf.getT[CU](lhs).foreach { cus => 
      schedulePCU(lhs, cus)
    }
  }

  def schedulePCU(exp: Expr, cus: Iterable[CU]):Unit = {
    cus.foreach { cu =>
      mappingOf(exp) = dbgblk(s"Scheduling $exp CU: $cu") {
        val ctx = ComputeContext(cu)
        cu.pseudoStages.foreach{stage => scheduleStage(stage, ctx) }
        cu
      }

    }
  }

  def scheduleStage(stage: PseudoStage, ctx: CUContext):Unit = dbgblk(s"scheduleStage(${quote(stage)})") {
    stage match {
      case DefStage(lhs@Def(rhs), isReduce) =>
        //dbgs(s"""$lhs = $rhs ${if (isReduce) "[REDUCE]" else ""}""")
        if (isReduce)   reduceNodeToStage(lhs,rhs,ctx)
        else            mapNodeToStage(lhs,rhs,ctx)

      case AddrStage(dmem, addr) =>
        //dbgs(s"$mem @ $addr [WRITE]")
        //addrToStage(dmem, addr, ctx)

      case OpStage(op, ins, out, isReduce) =>
        //dbgs(s"""$out = $op(${ins.mkString(",")}) [OP]""")
        opStageToStage(op, ins, out, ctx, isReduce)
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
      decompose(lhs).zip(elems.flatMap(decompose)).foreach { case (lhs, elem) =>
        ctx.addReg(lhs, ctx.reg(elem))
      }

    case VectorApply(vec, idx) =>
      if (idx != 0) throw new Exception(s"Expected parallelization of 1 in inner loop in PIRgen idx=$idx")
      decompose(vec).zip(decompose(lhs)).foreach { case (vec, lhs) =>
        ctx.addReg(lhs, ctx.reg(vec))
      }

    case VectorSlice(vector, end, start) =>
      /*  -------  --------------  --------
       *          e              s
       *  0000000  111111111111111111111111
       *  >> s (right shift by s)
       * */
      val vec = ctx.reg(vector)
      val output = allocateLocal(ctx.cu, lhs)
      val maskString = "0" * (spec.wordWidth - end) + "1" * end
      val maskInt = Integer.parseInt(maskString, 2)
      val mask = ConstReg(maskInt)
      dbgblk(s"VectorSlice($vector, end=$end, start=$start)") {
        dbgs(s"maskString=$maskString")
        dbgs(s"maskInt=$maskInt")
        val midOutput = if (start!=0) allocateLocal(ctx.cu, fresh[Int32]) else output
        val maskStage = MapStage(PIRBitAnd, List(ctx.refIn(vec), ctx.refIn(mask)), List(ctx.refOut(midOutput)))
        ctx.addStage(maskStage)
        dbgs(s"addStage: ctx=${ctx.cu.name}, stage=$maskStage")
        dbgs(s"")
        if (start != 0) {
          val amt = ConstReg(start)
          val shiftStage = MapStage(PIRFixSra, List(ctx.refIn(midOutput), ctx.refIn(amt)), List(ctx.refOut(output)))
          ctx.addStage(shiftStage)
          dbgs(s"addStage: ctx=${ctx.cu.name}, stage=$shiftStage")
        }
      }
      
    case VectorConcat(vectors) if lhs.tp.asInstanceOf[VectorType[_]].width <= spec.wordWidth  =>

    case VectorConcat(vectors) if lhs.tp.asInstanceOf[VectorType[_]].width > spec.wordWidth  =>
      val width = lhs.tp.asInstanceOf[VectorType[_]].width
      error(s"Plasticine cannot support VectorConcat more than ${spec.wordWidth} bits. vector width = $width")

    case SimpleStruct(elems) => decompose(lhs).foreach { elem => allocateLocal(ctx.cu, elem) }

    case DataAsBits(a) =>
      ctx.addReg(lhs, ctx.reg(a))

    case BitsAsData(a, mT) =>
      ctx.addReg(lhs, ctx.reg(a))

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
      // By convention, the inputs to tLANEShe reduction tree is the first argument to the node
      // This input must be in the previous stage's reduction register
      // Ensure this either by adding a bypass register for raw inputs or changing the output
      // of the previous stage from a temporary register to the reduction register
      //dbgs(s"[REDUCE] $op, ins = $ins, out = $out")

      val inputs = mutable.ListBuffer[Expr]()
      val accums = mutable.ListBuffer[Expr]()
      ins.foreach {
        case in@Def(RegRead(reg)) if isAccum(reg) & isWrittenInPipe(reg, mappingOf(ctx.cu)) => accums += in
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
      val accParents = mappingOf.to[CU](parentOf(accumReg).get)
      assert(accParents.size==1)
      val accParent = accParents.head
      val stage = ReduceStage(op, zero, ctx.refIn(usedInput), acc, accParent=accParent)
      ctx.addReg(out, acc)
      dbgs(s"addReg: ctx=${ctx.cu.name}, reg=$out -> $acc")
      ctx.addStage(stage)
      dbgs(s"addStage: ctx=${ctx.cu.name}, stage=$stage")
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
        error("Could not skip control logic in operation: ")
        error(s"$out = $op(" + ins.mkString(", ") + ") [reduce = " + isReduce + "]")
        error(s"Control registers: " + inputRegs.filter(isControl).mkString(", "))
        sys.exit(-1)
      }
      else {
        val inputs = inputRegs.map{reg => ctx.refIn(reg) }
        val output = allocateLocal(ctx.cu, out)
        val stage = MapStage(op, inputs, List(ctx.refOut(output)))
        ctx.addStage(stage)
        dbgs(s"addStage: ctx=${ctx.cu.name}, stage=$stage")
      }
    }
  }
}
