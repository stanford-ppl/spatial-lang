package spatial.codegen.pirgen
import spatial.SpatialExp
import org.virtualized.SourceContext

import scala.collection.mutable

trait PIRAllocation extends PIRTraversal {
  val IR: SpatialExp with PIRCommonExp
  import IR._

  override val name = "PIR CU Allocation"

  // -- State
  var top: Option[Symbol] = None
  var mapping = mutable.HashMap[Symbol, PCU]()

  // HACK: Skip parallel pipes in PIR gen
  private def parentHack(x: Symbol): Option[Symbol] = parentOf(x) match {
    case Some(pipe@Def(_:ParallelPipe)) => parentHack(pipe)
    case parentOpt => parentOpt
  }
  private def controllersHack(pipe: Symbol): List[Symbol] = pipe match {
    case Def(_:ParallelPipe) => childrenOf(pipe).flatMap{child => controllersHack(child)}
    case _ => List(pipe)
  }
  // Give top controller or first controller below which is not a Parallel
  private def topControllerHack(access: Access, ctrl: Ctrl): Ctrl = ctrl.node match {
    case pipe@Def(ParallelPipe(en, _)) =>
      topControllerHack(access, childContaining(ctrl, access))
    case _ => ctrl
  }

  // ISSUE #2: Assumes linear stage order
  def pipeDependencies(pipe: Symbol): List[Symbol] = parentOf(pipe) match {
    case Some(parent@Def(_:ParallelPipe)) => pipeDependencies(parent)
    case Some(parent) =>
      val childs = childrenOf(parent).map{child => controllersHack(child) }
      val idx = childs.indexWhere(_ contains pipe )
      if (idx > 0) childs(idx-1)
      else Nil
    case None => Nil
  }

  def addIterators(cu: PCU, cc: Exp[CounterChain], inds: Seq[Seq[Exp[Index]]], valids: Seq[Seq[Exp[Bool]]]) {
    val cchain = cu.cchains.find(_.name == quote(cc)).getOrElse(throw new Exception(s"Cannot find counterchain $cc in $cu"))
    inds.zipWithIndex.foreach{case (is, i) =>
      is.foreach{index => cu.addReg(index, CounterReg(cchain, i)) }
    }
    valids.zipWithIndex.foreach{case (es, i) =>
      es.foreach{e => cu.addReg(e, ValidReg(cchain, i)) }
    }
  }

  def allocateCChains(cu: PCU, pipe: Symbol) {
    def allocateCounter(start: Symbol, end: Symbol, stride: Symbol) = {
      val min = cu.getOrElse(start){ allocateLocal(start, pipe) }
      val max = cu.getOrElse(end){ allocateLocal(end, pipe) }
      val step = cu.getOrElse(stride){ allocateLocal(stride, pipe) }
      CUCounter(localScalar(min), localScalar(max), localScalar(step))
    }

    parentHack(pipe).foreach{parent => copyIterators(cu, allocateCU(parent)) }

    val Def(rhs) = pipe
    val ccs = dyns(rhs).collect{
      case cc@Def(CounterChainNew(ctrs)) =>
        val counters = ctrs.collect{case Def(CounterNew(start,end,stride,_)) => allocateCounter(start, end, stride) }
        CChainInstance(quote(cc), counters)
    }
    cu.cchains ++= ccs
  }

  def initCU(cu: PCU, pipe: Symbol) {
    allocateCChains(cu, pipe)
    cu.deps ++= pipeDependencies(pipe).map(allocateCU)
  }

  def allocateComputeUnit(pipe: Symbol): PCU = mapping.getOrElseUpdate(pipe, {
    val Def(d) = pipe
    dbg(s"Allocating CU for $pipe = $d")
    val parent = parentHack(pipe).map(allocateCU)

    val style = pipe match {
      case Def(_:UnitPipe) => UnitCU
      case Def(_:Hwblock)  => UnitCU
      case _ if styleOf(pipe) == SeqPipe && isInnerPipe(pipe) => UnitCU
      case _ => typeToStyle(styleOf(pipe))
    }

    val cu = PseudoComputeUnit(quote(pipe), pipe, style)
    cu.parent = parent
    initCU(cu, pipe)

    pipe match {
      case Def(e:UnrolledForeach)      => addIterators(cu, e.cchain, e.iters, e.valids)
      case Def(e:UnrolledReduce[_,_])  => addIterators(cu, e.cchain, e.iters, e.valids)
      case Def(_:UnitPipe | _:Hwblock) => cu.cchains += UnitCChain(quote(pipe)+"_unitcc")
      case _ =>
    }
    if (top.isEmpty && parent.isEmpty) top = Some(pipe)

    dbg(s"  created CU $cu")

    cu
  })

  def allocateMemoryUnit(pipe: Symbol): PCU = mapping.getOrElseUpdate(pipe, {
    val parent = parentHack(pipe).map(allocateCU)

    val style = UnitCU /*pipe match {
      case Def(_:BurstLoad[_]) => UnitCU
      case Def(_:BurstStore[_]) => UnitCU
      case Def(_:Scatter[_]) => StreamCU
      case Def(_:Gather[_]) => StreamCU
    }*/

    val cu = PseudoComputeUnit(quote(pipe), pipe, style)
    cu.parent = parent
    /*pipe match {
      case Def(_:BurstLoad[_] | _:BurstStore[_]) => cu.cchains += UnitCChain(quote(pipe)+"_unitcc")
      case Def(e:Scatter[_]) =>
      case Def(e:Gather[_]) =>
    }*/

    initCU(cu, pipe)
    cu
  })

  def allocateCU(pipe: Symbol): PCU = pipe match {
    case Def(_:Hwblock)             => allocateComputeUnit(pipe)
    case Def(_:UnrolledForeach)     => allocateComputeUnit(pipe)
    case Def(_:UnrolledReduce[_,_]) => allocateComputeUnit(pipe)
    case Def(_:UnitPipe)            => allocateComputeUnit(pipe)

    /*case Def(_:BurstLoad[_])  => allocateMemoryUnit(pipe)
    case Def(_:BurstStore[_]) => allocateMemoryUnit(pipe)
    case Def(_:Scatter[_])    => allocateMemoryUnit(pipe)
    case Def(_:Gather[_])     => allocateMemoryUnit(pipe)*/

    case Def(d) => throw new Exception(s"Don't know how to generate CU for\n  $pipe = $d")
  }

  def prescheduleRegisterRead(reg: Symbol, reader: Symbol, pipe: Option[Symbol]) = {
    dbg(s"Allocating register read: $reader")
    // Register reads may be used by more than one pipe
    readersOf(reg).filter(_.node == reader).map(_.ctrlNode).foreach{readCtrl =>
      val isCurrentPipe = pipe.exists(_ == readCtrl)
      val isLocallyWritten = isWrittenInPipe(reg, readCtrl)

      if (!isCurrentPipe || !isLocallyWritten) {
        val readerCU = allocateCU(readCtrl)
        dbg(s"  Adding stage $reader of $reg to reader $readerCU")
        readerCU.computeStages += DefStage(reader)
      }
    }
  }

  def allocateWrittenSRAM(writer: Symbol, mem: Symbol, writerCU: PCU, stages: Seq[PseudoStage]) {
    val srams = readersOf(mem).map{reader =>
      val readerCU = allocateCU(reader.ctrlNode)
      copyIterators(readerCU, writerCU)

      val sram = allocateMem(mem, reader.node, readerCU)
      if (readerCU == writerCU) {
        sram.vector = Some(LocalVectorBus)
      }
      else {
        val bus = if (writerCU.isUnit) CUScalar(quote(mem)) else CUVector(quote(mem))
        globals += bus
        sram.vector = Some(bus)
      }

      dbg(s"  Allocating written SRAM $mem")
      dbg(s"    writer   = $writer")
      dbg(s"    writerCU = $writerCU")
      dbg(s"    readerCU = $readerCU")

      (readerCU, sram)
    }

    if (stages.nonEmpty) {
      dbg(s"    Write stages: ")
      stages.foreach{stage => dbg(s"      $stage")}

      val groups = srams.groupBy(_._1).mapValues(_.map(_._2))
      for ((readerCU,srams) <- groups if readerCU != writerCU) {
        dbg(s"""  Adding write stages to $readerCU for SRAMs: ${srams.mkString(", ")}""")
        readerCU.writeStages(srams) = (writerCU.pipe,stages)
      }
    }
  }
  def allocateReadSRAM(reader: Symbol, mem: Symbol, readerCU: PCU) = {
    val sram = allocateMem(mem, reader, readerCU)

    dbg(s"  Allocating read SRAM $mem")
    dbg(s"    reader   = $reader")
    dbg(s"    readerCU = $readerCU")
    sram
  }

  private def initializeSRAM(sram: CUMemory, mem: Symbol, read: Symbol, cu: PCU) {
    val reader = readersOf(mem).find(_.node == read).get

    val instIndex = dispatchOf(reader, mem).head
    val instance = duplicatesOf(mem).apply(instIndex)

    // Find first writer corresponding to this reader
    val writers = writersOf(mem).filter{writer => dispatchOf(writer,mem).contains(instIndex) }
    if (writers.length > 1) {
      throw new Exception(s"$mem: $writers: PIR currently cannot handle multiple writers")
    }
    val writer = writers.headOption

    val writerCU = writer.map{w => allocateCU(w.ctrlNode) }
    val swapWritePipe = writer.flatMap{w => topControllerOf(w, mem, instIndex) }
    val swapReadPipe  = topControllerOf(reader, mem, instIndex)

    val swapWriteCU = (writer, swapWritePipe) match {
      case (Some(write), Some(ctrl)) =>
        val topCtrl = topControllerHack(write, ctrl)
        Some(allocateCU(topCtrl.node))
      case _ => None
    }
    val swapReadCU = swapReadPipe.map{ctrl =>
        val topCtrl = topControllerHack(reader, ctrl)
        allocateCU(topCtrl.node)
    }

    val remoteWriteCtrl = writerCU.flatMap{cu => cu.cchains.find{case _:UnitCChain | _:CChainInstance => true; case _ => false}}
    val remoteSwapWriteCtrl = swapWriteCU.flatMap{cu => cu.cchains.find{case _:UnitCChain | _:CChainInstance => true; case _ => false}}
    val remoteSwapReadCtrl = swapReadCU.flatMap{cu => cu.cchains.find{case _:UnitCChain | _:CChainInstance => true; case _ => false}}

    val readCtrl = cu.cchains.find{case _:UnitCChain | _:CChainInstance => true; case _ => false}
    val writeCtrl = remoteWriteCtrl.flatMap{cc => cu.cchains.find(_.name == cc.name) }
    val swapWrite = remoteSwapWriteCtrl.flatMap{cc => cu.cchains.find(_.name == cc.name) }
    val swapRead  = remoteSwapReadCtrl.flatMap{cc => cu.cchains.find(_.name == cc.name) }

    val writeIter = writeCtrl.flatMap{cc => cu.innermostIter(cc) }
    val readIter  = readCtrl.flatMap{cc => cu.innermostIter(cc) }

    val banking = if (isFIFO(mem)) Strided(1) else {
      val readBanking  = bank(mem, read, cu.isUnit)
      val writeBanking = writer.map{w => bank(mem, w.node, writerCU.get.isUnit) }.getOrElse(NoBanks)
      mergeBanking(writeBanking, readBanking)
    }

    sram.writeCtrl = writeCtrl
    sram.swapWrite = swapWrite
    sram.swapRead  = swapRead
    sram.banking   = Some(banking)
    sram.bufferDepth = instance.depth
    if (isFIFO(mem)) sram.mode = FIFOMode
  }

  def allocateMem(mem: Symbol, reader: Symbol, cu: PCU): CUMemory = {
//    if (!isBuffer(mem))
//      throw new Exception(s"Cannot allocate SRAM for non-buffer symbol $mem")

    cu.srams.find{sram => sram.mem == mem && sram.reader == reader}.getOrElse{
      val name = s"${quote(mem)}_${quote(reader)}"
      val size = mem match {
        case m if isSRAM(m) => dimsOf(m.asInstanceOf[Exp[SRAM[_]]]).product
        case m if isFIFO(m) => sizeOf(m.asInstanceOf[Exp[FIFO[Any]]]) match { case Exact(d) => d.toInt }
      }
      val sram = CUMemory(name, size, mem, reader)
      cu.srams += sram
      sram
    }
  }


  def prescheduleStages(pipe: Symbol, func: Block[Any]) {
    val cu = allocateCU(pipe)

    val remotelyAddedStages = cu.computeStages // Stages added prior to traversing this pipe
    val remotelyAddedStms = remotelyAddedStages.flatMap(_.output).flatMap{
      case s: Sym[_] => Some(stmOf(s))
      case _ => None
    }

    val stms = remotelyAddedStms ++ blockContents(func)
    val stages = stms.map{case TP(lhs,rhs) => lhs}

    var remoteStages: Set[Exp[Any]] = Set.empty   // Stages to ignore (goes on different CU)

    // HACK: Ignore write address for SRAMs written from popping the result of tile loads
    // (Skipping the vector packing/unpacking nonsense in between)
    def useFifoOnWrite(mem: Exp[Any], value: Exp[Any]): Boolean = value match { //TODO how about gather??
      case Def(FIFODeq(fifo, en))     =>
        dbg(s"      $value = pop($fifo) [${writersOf(fifo)}]")
        //writersOf(fifo).forall{writer => writer.node match {case Def(_:BurstLoad[_]) => true; case _ => false }}
        false
      case Def(ParFIFODeq(fifo, ens)) =>
        dbg(s"      $value = pop($fifo) [${writersOf(fifo)}]")
        //writersOf(fifo).forall{writer => writer.node match {case Def(_:BurstLoad[_]) => true; case _ => false }}
        false
      case Def(ListVector(elems))    =>
        dbg(s"      $value = vector")
        useFifoOnWrite(mem, elems.head)
      case Def(VectorApply(vector,index))     =>
        dbg(s"      $value = vec")
        useFifoOnWrite(mem, vector)
      case _ =>
        dbg(s"      $value -> no FIFO-on-write")
        //dbg(s"Written value is $value = $d: FIFO-on-write disallowed")
        false
    }

    // HACK! Determine start and end bounds for enable on FIFO
    def getFifoBounds(en: Option[Exp[Any]]): (Option[Exp[Any]], Option[Exp[Any]]) = en match {
      case Some( Def(And( Def(FixLeq(start,i1)), Def(FixLt(i2,end)) )) ) => (Some(start), Some(end))
      case Some( Def(And(x, y))) =>
        val xBnd = getFifoBounds(Some(x))
        val yBnd = getFifoBounds(Some(y))

        if (xBnd._1.isDefined) xBnd else yBnd

      case Some( Def(ListVector(en) )) => getFifoBounds(Some(en.head))
      case Some( Def(VectorApply(vector,index))) => getFifoBounds(Some(vector))
      case _ => (None, None)
    }

    cu.computeStages.clear() // Clear stages so we don't duplicate existing stages

    foreachSymInBlock(func){
      // NOTE: Writers always appear to occur in the associated writer controller
      // However, register reads may appear outside their corresponding controller
      case writer@LocalWriter(writes) if !isControlNode(writer) =>
        val rhs = writer match {case Def(d) => d; case _ => null }
        dbg(s"$writer = $rhs [WRITER]")

        writes.foreach{case (mem, value, addr, en) =>
          dbg(s"    Checking if $mem write can be implemented as FIFO-on-write:")
          val writeAsFIFO = value.exists{v => useFifoOnWrite(mem, v) }

          if ((isBuffer(mem) || isFIFO(mem)) && writeAsFIFO) {
            // This entire section is a hack to support FIFO-on-write for burst loads
            val enable: Seq[Exp[Any]] = writer match {
              case Def(op:EnabledOp[_]) => op.enables
              case _ => Nil
            }
            val enableComputation = enable.map{e => getScheduleForAddress(stms)(Seq(e)) }
            val enableSyms = enableComputation.map{case TP(s,d) => s}

            val (start,end) = getFifoBounds(enable.headOption)

            val startX = start match {case start@Some(Def(_:RegRead[_])) => start; case _ => None }
            val endX = end match {case end@Some(Def(_:RegRead[_])) => end; case _ => None }

            val remoteWriteStage = FifoOnWriteStage(mem, startX, endX)
            val enStages = startX.map{s => DefStage(s) }.toList ++ endX.map{s => DefStage(s) }.toList

            allocateWrittenSRAM(writer, mem, cu, enStages ++ List(remoteWriteStage))

            //TODO consider parSRAMStore, localWrite addr will be None 
            val indexComputation = addr.map{is => getScheduleForAddress(stms)(is) }.getOrElse(Nil)
            val indexSyms = indexComputation.map{case TP(s,d) => s}
            remoteStages ++= symsOnlyUsedInWriteAddrOrEn(stms)(func.result +: func.effectful, indexSyms ++ enableSyms)
          }
          else if (isBuffer(mem)) {
            val indexComputation = addr.map{is => getScheduleForAddress(stms)(is) }.getOrElse(Nil)
            val indexSyms = indexComputation.map{case TP(s,d) => s }
            val indexStages = indexSyms.map{s => DefStage(s) }
            val flatOpt = addr.map{is => flattenNDIndices(is, stagedDimsOf(mem.asInstanceOf[Exp[SRAM[_]]])) }
            val ad = flatOpt.map(_._1)
            val remoteWriteStage = ad.map{a => WriteAddrStage(mem, a) }
            val addrStages = indexStages ++ flatOpt.map(_._2).getOrElse(Nil) ++ remoteWriteStage

            allocateWrittenSRAM(writer, mem, cu, addrStages)

            val isLocallyRead = isReadInPipe(mem, pipe)
            // Currently have to duplicate if used in both address and compute
            if (indexSyms.nonEmpty && !isLocallyRead) {
              //dbg(s"  Checking if symbols calculating ${addr.get} are used in current scope $pipe")
              remoteStages ++= symsOnlyUsedInWriteAddr(stms)(func.result +: func.effectful, indexSyms)
            }
          }
        }

      case reader@LocalReader(reads) if !isControlNode(reader) =>
        val rhs = reader match {case Def(d) => d; case _ => null }
        dbg(s"  $reader = $rhs [READER]")

        reads.foreach{ case (mem,addr, en) =>
            if (isReg(mem)) {
              prescheduleRegisterRead(mem, reader, Some(pipe))
              val isLocallyRead = isReadInPipe(mem, pipe, Some(reader))
              val isLocallyWritten = isWrittenInPipe(mem, pipe)
              //dbg(s"  isLocallyRead: $isLocallyRead, isLocallyWritten: $isLocallyWritten")
              if (!isLocallyWritten || !isLocallyRead || isInnerAccum(mem)) remoteStages += reader
            }
            else if (isBuffer(mem)) {
              val sram = mem.asInstanceOf[Exp[SRAM[_]]]
              allocateReadSRAM(reader, sram, cu)
            }
        }

      case lhs@Op(rhs) => //TODO
        dbg(s"  $lhs = $rhs [OTHER]")
        visit(lhs, rhs)
      case lhs@Def(rhs) =>
        dbg(s"  $lhs = $rhs [OTHER]")
        visitFat(List(lhs), rhs)
    }

    val localCompute = stages.filter{s => (isPrimitiveNode(s) || isRegisterRead(s) || isGlobal(s) || isVector(s)) && !remoteStages.contains(s) }

    // Sanity check
    val trueComputation = localCompute.filterNot{case Exact(_) => true; case s => isRegisterRead(s)}
    if (isOuterControl(pipe) && trueComputation.nonEmpty) {
      stageWarn(s"Outer control $pipe has compute stages: ")
      trueComputation.foreach{case lhs@Def(rhs) => stageWarn(s"  $lhs = $rhs")}
    }

    cu.computeStages ++= localCompute.map{s => DefStage(s, isReduce = reduceType(s).isDefined) }
  }

  def prescheduleBurstTransfer(pipe: Symbol, mem: Symbol, ofs: Symbol, len: Symbol, mode: OffchipMemoryMode) = {
    // Ofs and len must either be constants or results of reading registers written in another controller
    val ofsWriter = ofs match {case Def(RegRead(reg)) if writersOf(reg).nonEmpty => Some(writersOf(reg).head); case _ => None }
    val lenWriter = len match {case Def(RegRead(reg)) if writersOf(reg).nonEmpty => Some(writersOf(reg).head); case _ => None }

    var ofsCUOpt = ofsWriter.map{writer => allocateCU(writer.ctrlNode)}
    var lenCUOpt = lenWriter.map{writer => allocateCU(writer.ctrlNode)}

    if (ofsCUOpt.isEmpty && lenCUOpt.isEmpty) {
      val cu = allocateCU(pipe)
      cu.deps = Set()
      ofsCUOpt = Some(cu)
      lenCUOpt = Some(cu)
    }
    else if (lenCUOpt.isEmpty && ofsCUOpt.isDefined) lenCUOpt = ofsCUOpt
    else if (lenCUOpt.isDefined && ofsCUOpt.isEmpty) ofsCUOpt = lenCUOpt
    val lenCU = lenCUOpt.get
    val ofsCU = ofsCUOpt.get

    val dram = allocateDRAM(pipe, mem, mode)

    val mcOfs = fresh[Index]
    ofsCU.addReg(mcOfs, ScalarOut(PIRDRAMOffset(dram)))
    val ofsReg = ofs match {case Def(RegRead(reg)) => reg; case ofs => ofs }

    val mcLen = fresh[Index]
    lenCU.addReg(mcLen, ScalarOut(PIRDRAMLength(dram)))
    val lenReg = len match {case Def(RegRead(reg)) => reg; case len => len }

    ofsCU.computeStages += OpStage(PIRBypass, List(ofsReg), mcOfs)
    lenCU.computeStages += OpStage(PIRBypass, List(lenReg), mcLen)

    // HACK- no dependents of ofsCU
    mapping.values.foreach{cu =>
      cu.deps = cu.deps.filterNot{dep => dep == ofsCU }
    }
  }

  def prescheduleGather(pipe: Symbol, mem: Symbol, local: Exp[SRAM[_]], addrs: Symbol, len: Symbol) {
    val cu = allocateCU(pipe)
    val dram = allocateDRAM(pipe, mem, MemGather)

    val n = cu.getOrElse(len){ allocateLocal(len, pipe) }
    val ctr = CUCounter(ConstReg("0i"), localScalar(n), ConstReg("1i"))
    val cc  = CChainInstance(quote(pipe)+"_cc", List(ctr))
    cu.cchains += cc
    val i = CounterReg(cc, 0)
    cu.regs += i


    val addr = allocateReadSRAM(pipe, addrs, cu)
    addr.readAddr = Some(i)

    val addrIn = fresh[Int32]
    cu.addReg(addrIn, SRAMReadReg(addr))

    val addrOut = fresh[Int32]
    cu.addReg(addrOut, VectorOut(PIRDRAMAddress(dram)))

    cu.computeStages += OpStage(PIRBypass, List(addrIn), addrOut)

    readersOf(local).foreach{reader =>
      val readerCU = allocateCU(reader.ctrlNode)
      copyIterators(readerCU, cu)

      val sram = allocateMem(local, reader.node, readerCU)
      sram.mode = FIFOOnWriteMode
      sram.writeAddr = None
      sram.vector = Some(PIRDRAMDataIn(dram))
    }
  }

  def prescheduleScatter(pipe: Symbol, mem: Symbol, local: Exp[SRAM[_]], addrs: Symbol, len: Symbol) {
    val cu = allocateCU(pipe)
    val dram = allocateDRAM(pipe, mem, MemScatter)

    val n = cu.getOrElse(len){ allocateLocal(len, pipe) }
    val ctr = CUCounter(ConstReg("0i"), localScalar(n), ConstReg("1i"))
    val cc  = CChainInstance(quote(pipe)+"_cc", List(ctr))
    cu.cchains += cc
    val i = CounterReg(cc, 0)
    cu.regs += i

    val addr = allocateReadSRAM(pipe, addrs, cu)
    val data = allocateReadSRAM(pipe, local, cu)
    addr.readAddr = Some(i)
    data.readAddr = Some(i)

    val addrIn = fresh[Int32]
    val dataIn = fresh[Int32]
    cu.addReg(addrIn, SRAMReadReg(addr))
    cu.addReg(dataIn, SRAMReadReg(data))

    val addrOut = fresh[Int32]
    val dataOut = fresh[Int32]
    cu.addReg(addrOut, VectorOut(PIRDRAMAddress(dram)))
    cu.addReg(dataOut, VectorOut(PIRDRAMDataOut(dram)))

    cu.computeStages += OpStage(PIRBypass, List(addrIn), addrOut)
    cu.computeStages += OpStage(PIRBypass, List(dataIn), dataOut)
  }


  override protected def visit(lhs: Sym[_], rhs: Op[_]) = rhs match {
    case RegRead(reg) if isArgIn(reg) =>
      prescheduleRegisterRead(reg, lhs, None)

    case Hwblock(func,_) =>
      prescheduleStages(lhs, func)

    case UnitPipe(en, func) =>
      prescheduleStages(lhs, func)

    case UnrolledForeach(en, cchain, func, iters, valids) =>
      prescheduleStages(lhs, func)

    case UnrolledReduce(en, cchain, accum, func, reduce, iters, valids, rV) =>
      prescheduleStages(lhs, func)

    /*case BurstLoad(dram, fifo, ofs, ctr, i) =>
      val Def(CounterNew(start,end,step,par)) = ctr
      prescheduleBurstTransfer(lhs, dram, ofs, end, MemLoad)

    case BurstStore(dram, fifo, ofs, ctr, i) =>
      val Def(CounterNew(start,end,step,par)) = ctr
      prescheduleBurstTransfer(lhs, dram, ofs, end, MemStore)

    case Scatter(dram, local, addrs, ctr, i) =>
      val Def(CounterNew(start,end,step,par)) = ctr
      prescheduleScatter(lhs, dram, local, addrs, end)

    case Gather(dram, local, addrs, ctr, i) =>
      val Def(CounterNew(start,end,step,par)) = ctr
      prescheduleGather(lhs, dram, local, addrs, end)*/

    // Something bad happened if these are still in the IR
    case _:OpForeach => throw new Exception(s"Disallowed compact op $lhs = $rhs")
    case _:OpReduce[_] => throw new Exception(s"Disallowed compact op $lhs = $rhs")
    case _:OpMemReduce[_,_] => throw new Exception(s"Disallowed compact op $lhs = $rhs")
    case _ => super.visit(lhs, rhs)
  }

  override def preprocess[S:Type](b: Block[S]): Block[S] = {
    top = None
    mapping.clear()
    globals = Set.empty
    super.preprocess(b)
  }

  override def postprocess[S:Type](b: Block[S]): Block[S] = {
    val cus = mapping.values
    val owner = cus.flatMap{cu => cu.srams.map{sram =>
      (sram.reader, sram.mem) -> (cu, sram)
    }}.toMap

    for (cu <- cus) {
      var nSRAMs = cu.srams.size
      var prevN = -1
      while (nSRAMs != prevN) {
        prevN = nSRAMs
        addSRAMsReadInWriteStages()
        nSRAMs = cu.srams.size
      }

      def addSRAMsReadInWriteStages() {
        val writeStages = cu.writeStages.values.flatMap(_._2).toList

        val newWrites = writeStages.foreach{
          case DefStage(reader@LocalReader(reads), _) => reads foreach {
            case (mem,addr,en) =>
              if (isBuffer(mem) && !cu.srams.exists{sram => sram.reader == reader && sram.mem == mem}) {
                val (ownerCU,orig) = owner((reader, mem))
                val copy = orig.copyMem(orig.name+"_"+quote(cu.pipe))
                cu.srams += copy

                val key = ownerCU.writeStages.keys.find{key => key.contains(orig)}

                if (key.isDefined) {
                  cu.writeStages += List(copy) -> ownerCU.writeStages(key.get)
                }
                dbg(s"Adding SRAM $copy ($reader, $mem) read in write stage of $cu")
              }
          }
          case _ =>
        }
      }


    }
    for (cu <- cus) {
      for (sram <- cu.srams) {
        initializeSRAM(sram, sram.mem, sram.reader, cu)
      }
    }

    super.postprocess(b)
  }
}
