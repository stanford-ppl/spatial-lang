package spatial.codegen.pirgen
import spatial.SpatialExp
import org.virtualized.SourceContext

import scala.collection.mutable

trait PIRAllocation extends PIRTraversal {
  val IR: SpatialExp with PIRCommonExp
  import IR.{println => _, _}

  override val name = "PIR CU Allocation"

  // -- State
  var top: Option[Symbol] = None
  var mapping = mutable.Map[Symbol, List[PCU]]()

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

  def addIterators(cu: PCU, cchain: CChainInstance, inds: Seq[Seq[Exp[Index]]], valids: Seq[Seq[Exp[Bool]]]) {
    inds.zipWithIndex.foreach{case (is, i) =>
      is.foreach{index => cu.addReg(index, CounterReg(cchain, i)) }
    }
    valids.zipWithIndex.foreach{case (es, i) =>
      es.foreach{e => cu.addReg(e, ValidReg(cchain, i)) }
    }
  }

  def allocateCChains(pipe: Symbol) = {
    val cchainOpt = pipe match {
      case Def(UnrolledForeach(en, cchain, func, iters, valids)) => Some((cchain, iters, valids))
      case Def(UnrolledReduce(en, cchain, accum, func, reduce, iters, valids, rV)) => Some((cchain, iters, valids))
      case _ => None
    }
    cchainOpt.foreach { case (cchain, iters, valids) =>
      dbgblk(s"Allocate cchain ${qdef(cchain)} for $pipe") {
        val cu = allocateCU(pipe)
        def allocateCounter(start: Symbol, end: Symbol, stride: Symbol) = {
          val min = cu.getOrElseUpdate(start){ const(start) }
          val max = cu.getOrElseUpdate(end){ const(end) }
          val step = cu.getOrElseUpdate(stride){ const(stride) }
          dbgs(s"counter start:${qdef(start)}, end:${qdef(end)}, stride:${qdef(stride)}")
          CUCounter(localScalar(min), localScalar(max), localScalar(step))
        }
        val Def(CounterChainNew(ctrs)) = cchain
        val counters = ctrs.collect{case Def(CounterNew(start,end,stride,_)) => allocateCounter(start, end, stride) }
        val cc = CChainInstance(quote(cchain), counters)
        cu.cchains += cc
        addIterators(cu, cc, iters, valids)
      }
    }
  }

  def allocateCU(pipe: Symbol): PCU = mapping.getOrElseUpdate(pipe, {
    val parent = parentHack(pipe).map(allocateCU)

    val style = pipe match {
      case Def(_:UnitPipe) => UnitCU
      case Def(_:Hwblock)  => UnitCU
      case Def(FringeDenseLoad(dram, _, _))  => FringeCU(allocateDRAM(dram), MemLoad)
      case Def(FringeDenseStore(dram, _, _, _))  => FringeCU(allocateDRAM(dram), MemStore)
      case Def(FringeSparseLoad(dram, _, _))  => FringeCU(allocateDRAM(dram), MemGather)
      case Def(FringeSparseStore(dram, _, _))  => FringeCU(allocateDRAM(dram), MemScatter)
      case _ if styleOf(pipe) == SeqPipe && isInnerPipe(pipe) => UnitCU
      case _ => typeToStyle(styleOf(pipe))
    }

    val cu = PseudoComputeUnit(quote(pipe), pipe, style)
    cu.parent = parent

    if (top.isEmpty && parent.isEmpty) top = Some(pipe)

    dbgs(s"Allocating CU $cu for ${pipe}")
    List(cu)
  }).head

  def allocateMemoryCU(dsram:Symbol):List[PCU] = mapping.getOrElseUpdate(dsram, { 
    val sram = compose(dsram)
    val parentCU = parentOf(sram).map(allocateCU)
    val writer = writerOf(sram)
    dbgblk(s"Allocating memory cu for ${qdef(sram)}, writer:${writer}") {
      readersOf(sram).zipWithIndex.map { case (readAcc, i) => 
        val reader = readAcc.node
        val dreader = getMatchedDecomposed(dsram, reader) 
        val cu = PseudoComputeUnit(s"${quote(dsram)}_dsp${i}", dsram, MemoryCU(i))
        dbgs(s"Allocating MCU duplicates $cu for ${quote(dsram)}, reader:${quote(dreader)}")
        cu.parent = parentCU
        val psram = allocateMem(dsram, dreader, cu)
        cu
      }.toList
    }
  })

  def allocateWrittenSRAM(writer: Symbol, mem: Symbol, writerCU: PCU, 
                          stages: List[PseudoStage]) = dbgblk(s"Allocating written SRAM $mem") {
    val sramCUs = allocateMemoryCU(mem)
    dbgs(s"writer   = $writer")
    dbgs(s"writerCU = $writerCU")
    dbgblk(s"sramCUs = $sramCUs") {
      sramCUs.zipWithIndex.foreach{ case (sramCU, i) => 
        //copyIterators(sramCU, writerCU)
        //val bus = if (writerCU.isUnit) CUScalar(s"${quote(mem)}_${i}_wt") else CUVector(s"${quote(mem)}_${i}_wt")
        val bus = CUVector(s"${quote(mem)}_${i}_wt") //TODO Writes to sram is alwasy using vector bus
        globals += bus
        sramCU.srams.foreach { _.writePort = Some(bus) }
        if (stages.nonEmpty) {
          sramCU.writeStages(sramCU.srams.toList) = (writerCU.pipe,stages)
          dbgblk(s"$sramCU Write stages for [${sramCU.srams.mkString(",")}]: ") {
            stages.foreach{stage => dbgs(s"$stage")}
          }
        }
      }
    }
  }

  def allocateReadSRAM(reader: Symbol, mem: Symbol, 
                       readerCU: PCU, stages:List[PseudoStage]) = dbgblk(s"Allocating read SRAM $mem") {
    dbgs(s"reader   = $reader")
    dbgs(s"readerCU = $readerCU")
    val sramCUs = allocateMemoryCU(mem)
    val dispatch = dispatchOf(reader, mem).head
    val sramCU = sramCUs(dispatch)
    //copyIterators(sramCU, readerCU)
    val bus = CUVector(s"${quote(mem)}_${dispatch}_rd") //TODO Reads to sram is always using vector bus
    globals += bus
    sramCU.srams.foreach{ _.readPort = Some(bus) } //each sramCU should only have a single sram
    if (stages.nonEmpty) {
      dbgblk(s"$sramCU Read stages:") {
        stages.foreach{stage => dbgs(s"$stage")}
      }
      sramCU.readStages(sramCU.srams.toList) = (readerCU.pipe,stages)
    }
    sramCU.srams.head
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
  
  /*
   * @param cu, cu in which read occurs
   * */
  def scheduleRead(reader:Symbol, reads:List[ParLocalRead], stms:Seq[Stm], cu:PCU, pipe:Symbol) = {
    val rhs = reader match {case Def(d) => d; case _ => null }
    dbgblk(s"$reader = $rhs [READER]") {
      reads.foreach{ case (mem,addrs,ens) =>
        val addr = addrs.map{_.head}
        if (isReg(mem) || isFIFO(mem)) {
          prescheduleLocalMemRead(mem, reader)
        }
        else if (isBuffer(mem)) {
          //val addrStages = extractRemoteAddrStages(mem, addr, stms, cu, pipe)
          //allocateReadSRAM(reader, mem, cu, addrStages)
        }
      }
    }
  }

  def scheduleWrite(writer:Symbol, writes:List[ParLocalWrite], stms:Seq[Stm], cu:PCU, pipe:Symbol) = {
    val rhs = writer match {case Def(d) => d; case _ => null }
    dbgblk(s"$writer = $rhs [WRITER]") {
      writes.foreach{case (mem, value, addrs, ens) =>
        dbgs(s"Checking if $mem write can be implemented as FIFO-on-write:")
        val addr = addrs.map{_.head}
        //val writeAsFIFO = value.exists{v => useFifoOnWrite(mem, v) }

        //if ((isBuffer(mem) || isFIFO(mem)) && writeAsFIFO) {
          //// This entire section is a hack to support FIFO-on-write for burst loads
          //val enableComputation = ens.map{e => getScheduleForAddress(stms)(Seq(e)) }.getOrElse(Nil)
          //val enableSyms = enableComputation.map{case TP(s,d) => s} ++ ens
          //dbgs(s"write(mem=$mem, value=$value, addr=$addr, ens=$ens):")
          //dbgs(s"ens:$ens enableSyms:${enableSyms.mkString(",")}")

          //val (start,end) = getFifoBounds(ens)

          //val startX = start match {case start@Some(Def(_:RegRead[_])) => start; case _ => None }
          //val endX = end match {case end@Some(Def(_:RegRead[_])) => end; case _ => None }

          //val remoteWriteStage = FifoOnWriteStage(mem, startX, endX)
          //val enStages = startX.map{s => DefStage(s) }.toList ++ endX.map{s => DefStage(s) }.toList
          //if (enStages.nonEmpty) dbgblk(s"boundStages:") { enStages.foreach { stage => dbgs(s"$stage") } }

          //allocateWrittenSRAM(writer, mem, cu, enStages ++ List(remoteWriteStage))

          ////TODO consider parSRAMStore, localWrite addr will be None 
          //val indexSyms = addr.map { is => symsUsedInCalcExps(stms)(is) }.getOrElse(Nil)
          //dbgs(s"indexSyms:$indexSyms")
          //val remoteSyms = symsOnlyUsedInWriteAddrOrEn(stms)(func.result +: func.effectful, indexSyms ++ enableSyms)
          //dbgs(s"symsOnlyUsedInWriteAddrOrEn: $remoteSyms")
          //cu.remoteStages ++= remoteSyms
        //}
        //else
        //if (isFIFO(mem)) {
        //} else if (isBuffer(mem)) {
          //val addrStages = extractRemoteAddrStages(mem, addr, stms, cu, pipe)
          //allocateWrittenSRAM(writer, mem, cu, addrStages)
        //}
      }
    }
  }

  /*
   * @param cu CU in which the address calculation stages should be pulled out of
   * */
  def extractRemoteAddrStages(dmem:Symbol, addr:Option[Seq[Exp[Index]]], stms:Seq[Stm]):(Option[Symbol],List[PseudoStage])= {
    dbgblk(s"Extracting Remote Addr in for dmem=$dmem addr=$addr") {
      val memCUs = allocateMemoryCU(dmem)
      val flatOpt = addr.map{is => flattenNDIndices(is, stagedDimsOf(compose(dmem).asInstanceOf[Exp[SRAM[_]]])) }
      // Symbols
      val indexSyms = addr.map { is => symsUsedInCalcExps(stms)(is) }.getOrElse(Nil)
      //indexSyms.foreach {
        //case reader@ParLocalReader(reads) if isBuffer(reads.head._1) =>
          //memCUs.foreach { cu => scheduleRead(reader, reads, stms, cu, pipe) }
        //case _ =>
      //}
      val ad = flatOpt.map(_._1) // sym of flatten  addr

      dbgs(s"$dmem addr:[${addr.map(_.mkString(","))}], indexSyms:[${indexSyms.mkString(",")}]")
      memCUs.foreach { memCU => copyBounds(indexSyms, memCU) }

      // PseudoStages
      val indexStages:List[PseudoStage] = indexSyms.map{s => DefStage(s) }
      val flatStages = flatOpt.map(_._2).getOrElse(Nil)
      val remoteAddrStage = ad.map{a => AddrStage(dmem, a) }
      val addrStages = indexStages ++ flatStages ++ remoteAddrStage

      //val isLocallyRead = isReadInPipe(mem, pipe)

      // Currently have to duplicate if used in both address and compute
      //if (indexSyms.nonEmpty && !isLocallyRead) 
      //if (indexSyms.nonEmpty) {
        ////dbg(s"Checking if symbols calculating ${addr.get} are used in current scope $pipe")
        //val symAddr = symsOnlyUsedInWriteAddr(stms)(func.result +: func.effectful, indexSyms)
        //dbgs(s"symsOnlyUsedInAddr:[${symAddr.mkString(",")}]")
        //cu.remoteStages ++= symAddr
      //}
      dbgl(s"addrStages:") {
        addrStages.foreach { stage => dbgs(s"${stage}") }
      }
      (ad, addrStages)
    }
  }

  /* 
   * Find SRAMLoads whose value is not just used for indecies calculation of other loads/stores
   * Handle data depended load/store
   * */
  def nonIndOnlySRAMLoads(func:Block[Any], stms:Seq[Stm]) = {
    var nonIndOnlyLoads:List[Symbol] = Nil 
    var symInAddrCalc:List[Symbol] = Nil
    foreachSymInBlock(func) {
      case writer@ParLocalWriter(writes) if !isControlNode(writer) =>
        writes.foreach{case (mem, value, addrs, ens) =>
          val addr = addrs.map{_.head}
          symInAddrCalc = symInAddrCalc ++ addr.map { is => symsUsedInCalcExps(stms)(is) }.getOrElse(Nil)
        }
      case reader@ParLocalReader(reads) if !isControlNode(reader) =>
        reads.foreach{ case (mem, addrs ,ens) =>
          val addr = addrs.map{_.head}
          symInAddrCalc = symInAddrCalc ++ addr.map { is => symsUsedInCalcExps(stms)(is) }.getOrElse(Nil)
        }
        nonIndOnlyLoads = nonIndOnlyLoads :+ reader
      case _ =>
    }
    //FIXME: Consider the case when a load is used both for data depended load/store and actual
    //computation for output of block
    nonIndOnlyLoads = nonIndOnlyLoads.filter { s => symInAddrCalc.contains(s) }  
    if (nonIndOnlyLoads.nonEmpty) dbgs(s"nonIndOnlyLoads: ${nonIndOnlyLoads.mkString(",")}")
    nonIndOnlyLoads
  }

  def copyBounds(syms:List[Sym[_]], cu:PseudoComputeUnit) = {
    syms.foreach {
      case Def(d) => d.expInputs.foreach {
        case b:Bound[_] => 
          if (cu.get(b).isEmpty) {
            val fromCUs = mapping(parentOf(b).getOrElse(throw new Exception(s"$b doesn't have parent")))
            assert(fromCUs.size==1) // parent of bounds must be a controller in spatial
            val fromCU = fromCUs.head 
            copyIterators(cu, fromCU)
          }
        case _ => 
      }
    }
  }

  def prescheduleStages(pipe: Symbol, func: Block[Any]):PseudoComputeUnit = dbgblk(s"prescheduleStages ${qdef(pipe)}") {
    val cu = allocateCU(pipe)

    val remotelyAddedStages = cu.computeStages // Stages added prior to traversing this pipe
    val remotelyAddedStms = remotelyAddedStages.flatMap(_.output).flatMap{
      case s: Sym[_] => Some(stmOf(s))
      case _ => None
    }


    val stms = remotelyAddedStms ++ blockContents(func)

    cu.computeStages.clear() // Clear stages so we don't duplicate existing stages
    
    // sram loads that are not just used in index calculation 
    //var nonIndOnlyLoads:List[Symbol] = nonIndOnlySRAMLoads(func, stms)

    val localCompute = symsUsedInCalcExps(stms)(Seq(func.result), func.effectful)
    copyBounds(localCompute , cu)

    // Sanity check
    val trueComputation = localCompute.filterNot{case Exact(_) => true; case s => isRegisterRead(s)}
    if (isOuterControl(pipe)) {
      if (trueComputation.nonEmpty) {
        warn(s"Outer control $pipe has compute stages: ")
        trueComputation.foreach{case lhs@Def(rhs) => warn(s"$lhs = $rhs")}
      }
    } else { // Only inner pipes have stages
      cu.computeStages ++= localCompute.map{ s => 
        val isReduce = (s match {
          case Def(RegRead(_)) => false
          case Def(RegWrite(_,_,_)) => false
          case s => reduceType(s).isDefined
        }) && !isBlockReduce(func)
        DefStage(s, isReduce = isReduce)
      }
      dbgl(s"prescheduled stages for $cu:") {
        cu.computeStages.foreach { s =>
          s match {
            case s@DefStage(op, _) => dbgs(s"$s reduceType=${reduceType(op)}")
            case s => dbgs(s"$s")
          }
        }
      }
    }
    cu
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
    mapping.values.foreach{ cus =>
      cus.foreach { cu =>
        cu.deps = cu.deps.filterNot{dep => dep == ofsCU }
      }
    }
  }

  def prescheduleGather(pipe: Symbol, mem: Symbol, local: Exp[SRAM[_]], addrs: Symbol, len: Symbol) {
    val cu = allocateCU(pipe)
    val dram = allocateDRAM(pipe, mem, MemGather)

    val n = cu.getOrElseUpdate(len){ allocateLocal(len) }
    val ctr = CUCounter(ConstReg("0i"), localScalar(n), ConstReg("1i"))
    val cc  = CChainInstance(quote(pipe)+"_cc", List(ctr))
    cu.cchains += cc
    val i = CounterReg(cc, 0)
    cu.regs += i


    val addr = allocateReadSRAM(pipe, addrs, cu, Nil)
    addr.readAddr = Some(i) //TODO: Change to remote remote Addr?

    val addrIn = fresh[Int32]
    cu.addReg(addrIn, MemLoadReg(addr))

    val addrOut = fresh[Int32]
    cu.addReg(addrOut, VectorOut(PIRDRAMAddress(dram)))

    cu.computeStages += OpStage(PIRBypass, List(addrIn), addrOut)

    readersOf(local).foreach{reader =>
      val readerCU = allocateCU(reader.ctrlNode)
      //copyIterators(readerCU, cu)

      val sram = allocateMem(local, reader.node, readerCU)
      sram.mode = FIFOOnWriteMode
      sram.writeAddr = None
      sram.writePort = Some(PIRDRAMDataIn(dram))
    }
  }

  def prescheduleScatter(pipe: Symbol, mem: Symbol, local: Exp[SRAM[_]], addrs: Symbol, len: Symbol) {
    val cu = allocateCU(pipe)
    val dram = allocateDRAM(pipe, mem, MemScatter)

    val n = cu.getOrElseUpdate(len){ allocateLocal(len) }
    val ctr = CUCounter(ConstReg("0i"), localScalar(n), ConstReg("1i"))
    val cc  = CChainInstance(quote(pipe)+"_cc", List(ctr))
    cu.cchains += cc
    val i = CounterReg(cc, 0)
    cu.regs += i

    val addr = allocateReadSRAM(pipe, addrs, cu, Nil)
    val data = allocateReadSRAM(pipe, local, cu, Nil)
    addr.readAddr = Some(i)
    data.readAddr = Some(i)

    val addrIn = fresh[Int32]
    val dataIn = fresh[Int32]
    cu.addReg(addrIn, MemLoadReg(addr))
    cu.addReg(dataIn, MemLoadReg(data))

    val addrOut = fresh[Int32]
    val dataOut = fresh[Int32]
    cu.addReg(addrOut, VectorOut(PIRDRAMAddress(dram)))
    cu.addReg(dataOut, VectorOut(PIRDRAMDataOut(dram)))

    cu.computeStages += OpStage(PIRBypass, List(addrIn), addrOut)
    cu.computeStages += OpStage(PIRBypass, List(dataIn), dataOut)
  }

  def memMode(dmem:Symbol) = compose(dmem) match {
    case mem if isReg(mem) | isGetDRAMAddress(mem) => ScalarBufferMode
    case mem if isStreamIn(mem) => FIFOMode // from Fringe
    case mem if isFIFO(mem) | isStreamOut(mem) => 
      val writer = writerOf(mem)
      if (isUnitPipe(writer.ctrlNode)) ScalarFIFOMode else FIFOMode
    case mem if isSRAM(mem) => SRAMMode
  }

  def allocateMem(dmem: Symbol, dreader: Symbol, cu: PCU): CUMemory =  {
    cu.mems.getOrElseUpdate(dmem, {
      val name = if (isGetDRAMAddress(dmem)) s"${quote(dmem)}"
                 else s"${quote(dmem)}_${quote(dreader)}"
      val size = compose(dmem) match {
        case m if isSRAM(m) => dimsOf(m.asInstanceOf[Exp[SRAM[_]]]).product
        case m if isFIFO(m) => 
          sizeOf(m.asInstanceOf[Exp[FIFO[Any]]]) match { case Exact(d) => d.toInt }
        case m if isReg(m) | isStream(m) | isGetDRAMAddress(m) => 1
      }
      val cuMem = CUMemory(name, size, dmem, dreader)
      cuMem.mode = memMode(dmem)
      dbgs(s"Add $cuMem to $cu")
      cuMem
    })
  }

  def allocateLocalMem(mem:Symbol) = dbgblk(s"allocateLocalMem($mem)"){
    var readers = getReaders(mem) 
    readers.foreach { reader => 
      val localWritten = isLocallyWritten(mem, reader)
      dbgs(s"isLocallyWritten=$localWritten ${qdef(mem)} ${qdef(reader)}")
      dbgs(s"mem=$mem, dmems=${decompose(mem).mkString(",")} dreaders=${decompose(reader).mkString(",")}")
      val dreaders = reader match {
        case reader if isFringe(reader) => decompose(mem).map { m => reader }
        case reader => decompose(reader)
      }
      decompose(mem).zip(dreaders).foreach { case (dmem, dreader) => 
        val bus = if (isArgIn(mem) || isGetDRAMAddress(mem)) Some(InputArg(s"${quote(dmem)}")) else None
        getReaderCUs(reader).foreach { readerCU =>
          if (!localWritten) { // Write to FIFO/StreamOut/RemoteReg
            // Allocate local mem in the readerCU
            allocateMem(dmem, dreader, readerCU)
            // Set writeport of the local mem who doesn't have a writer (ArgIn and GetDRAMAddress)
            bus.foreach { bus => readerCU.mems(dmem).writePort = Some(bus) }
          } else { // Local reg accumulation
            readerCU.getOrElseUpdate(dmem) {
              val Def(RegNew(init)) = mem //Only register can be locally written
              val initVal = extractConstant(init)
              AccumReg(ConstReg(s"${initVal}"))
            }
          }
        }
      }
    }
  }

  /*
   * @param reader the reader symbol
   * @return list of CUs where the reader symbol is used in calculation. In case a
   * load/regRead/fifoDeq is used for both data calculation and address calculation for remote
   * memory, this function returns both the PCU and MCUs
   * */
  def getReaderCUs(reader:Symbol):List[PseudoComputeUnit] = dbgblk(s"getReaderCUs ${qdef(reader)}") {
    val readerCUs = mutable.Set[PseudoComputeUnit]()
    if (isFringe(reader)) { readerCUs += allocateCU(reader) } // Fringe is considered to be a reader of the stream
    else {
      parentOf(reader).foreach { pipe => // RegRead outside HwBlock doesn't have parent
        val stms = getStms(pipe)
        def addParentCU(d:Def, mem:Symbol, ind:Option[Seq[Symbol]]) = {
          val indSyms = ind.map { ind => symsUsedInCalcExps(stms)(ind) }.getOrElse(Nil)
          if (indSyms.contains(reader) && isRemoteMem(mem)) {
            readerCUs ++= decompose(mem).flatMap(allocateMemoryCU)
          } else if (d.allInputs.contains(reader)) {
            readerCUs += allocateCU(pipe)
          }
        }
        stms.foreach {
          case TP(s, d@ParLocalReader(reads)) =>
            val (mem, inds, _) = reads.head
            addParentCU(d, mem, inds.map{_.head})
          case TP(s, d@ParLocalWriter(writes)) =>
            val (mem, _, inds, _) = writes.head
            addParentCU(d, mem, inds.map{_.head})
          case TP(s@Def(_:CounterNew), d) if d.allInputs.contains(reader) => readerCUs ++= getReaderCUs(s)
          case TP(s@Def(_:CounterChainNew), d) if d.allInputs.contains(reader) => readerCUs ++= getReaderCUs(s)
          case TP(s, d) if d.allInputs.contains(reader) & isControlNode(s) => readerCUs += allocateCU(s)
          case TP(s, d) if d.allInputs.contains(reader) => readerCUs += allocateCU(pipe)
          case TP(s, d) => 
        }
      }
    }
    dbgs(s"ReaderCUs for ${qdef(reader)} = [${readerCUs.mkString(",")}]")
    readerCUs.toList
  }

  def getWriterCU(dwriter:Symbol) = {
    val writer = compose(dwriter)
    val ParLocalWriter(writes) = writer 
    val pipe = parentOf(writer).get 
    val (mem, value, inds, ens) = writes.head
    value.get match {
      case reader@ParLocalReader(reads) =>
        val (mem, _, _) = reads.head
        if (isRemoteMem(mem)) {
          val dmem = getMatchedDecomposed(dwriter, mem)
          getMCUforReader(dmem, reader)
        } else {
          allocateCU(pipe)
        }
      case _ => allocateCU(pipe)
    }
  }

  def getMCUforReader(dmem:Symbol, reader:Symbol) = {
    val mem = compose(dmem)
    val idx = readersOf(mem).indexOf(reader)
    allocateMemoryCU(dmem)(idx)
  } 

  def prescheduleLocalMemRead(mem: Symbol, reader:Symbol) = {
    dbgblk(s"Allocating local memory read: $reader") {
      getReaderCUs(reader).foreach { readerCU =>
        decompose(mem).zip(decompose(reader)).foreach { case (dmem, dreader) =>
          val locallyWritten = isLocallyWritten(dmem, dreader, Some(readerCU))
          dbgs(s"$mem isLocalMemWrite:${locallyWritten} readerCU:$readerCU dreader:$dreader")
          if (locallyWritten) {
            val reg = readerCU.get(dmem).get // Accumulator should be allocated during RegNew
            readerCU.addReg(dreader, reg)
          } else {
            readerCU.addReg(dreader, MemLoadReg(allocateMem(dmem, dreader, readerCU))) //FIXME: rename to MemLoadReg
          }
        }
      }
    }
  }

  def prescheduleLocalMemWrite(mem: Symbol, writer:Symbol) = {
    dbgblk(s"Allocating local memory write: $writer of mem:${quote(mem)}") {
      val remoteReaders = getRemoteReaders(mem, writer)
      dbgs(s"remoteReaders:${remoteReaders.mkString(",")}")
      if (remoteReaders.nonEmpty || isArgOut(mem)) {
        allocateLocalMem(mem)
        decompose(mem).zip(decompose(writer)).foreach { case (dmem, dwriter) =>
          dbgs(s"dmem:$dmem, dwriter:$dwriter")
          val (bus, output) = if (isUnitPipe(parentOf(writer).get)) {
            val bus = if (isArgOut(mem)) 
              OutputArg(s"${quote(dmem)}_${quote(dwriter)}") 
            else
              CUScalar(s"${quote(dmem)}_${quote(dwriter)}")
            (bus, ScalarOut(bus))
          } else {
            val bus = CUVector(s"${quote(dmem)}_${quote(dwriter)}")
            (bus, VectorOut(bus))
          }
          val writerCU = getWriterCU(dwriter) 
          writerCU.addReg(dwriter, output)
          remoteReaders.foreach { reader =>
            getReaderCUs(reader).foreach { readerCU =>
              readerCU.mems(dmem).writePort = Some(bus)
            }
          }
        }
      }
    }
  }

  def prescheduleRemoteMemRead(mem: Symbol, reader:Symbol) = {
    dbgblk(s"Allocating remote memory read: ${qdef(reader)}") {
      val pipe = parentOf(reader).get
      val stms = getStms(pipe)
      val readerCUs = getReaderCUs(reader)
      val ParLocalReader(reads) = reader
      val (_, addrs, _) = reads.head
      val addr = addrs.map(_.head)
      decompose(mem).zip(decompose(reader)).foreach { case (dmem, dreader) =>
        readerCUs.foreach { readerCU =>
          dbgs(s"readerCU = $readerCU")
          val bus = CUVector(s"${quote(dmem)}_${quote(dreader)}_${quote(readerCU.pipe)}") 
          readerCU.addReg(dreader, VectorIn(bus))
          // Schedule address calculation
          val (ad, addrStages) = extractRemoteAddrStages(dmem, addr, stms)
          val sramCUs = allocateMemoryCU(dmem)
          sramCUs.foreach { sramCU =>
            dbgs(s"sramCUs for dmem=${qdef(dmem)} cu=$sramCU")
            val sram = sramCU.mems(mem)
            sramCU.readStages(List(sram)) = (readerCU.pipe, addrStages)
            sram.readPort = Some(bus)
            sram.readAddr = ad.map{ad => ReadAddrWire(sram)} //TODO
          }
        }
      }
    }
  }

  def prescheduleRemoteMemWrite(mem: Symbol, writer:Symbol) = {
    dbgblk(s"Allocating remote memory write: ${qdef(writer)}") {
      val pipe = parentOf(writer).get
      val stms = getStms(pipe)
      val ParLocalWriter(writes) = writer
      val (mem, value, addrs, ens) = writes.head
      val addr = addrs.map(_.head)
      decompose(mem).zip(decompose(writer)).foreach { case (dmem, dwriter) =>
        val bus = CUVector(s"${quote(dmem)}_${quote(dwriter)}")
        val writerCU = getWriterCU(dwriter) 
        dbgs(s"writerCU = $writerCU")
        writerCU.addReg(dwriter, VectorOut(bus))
        // Schedule address calculation
        val (ad, addrStages) = extractRemoteAddrStages(dmem, addr, stms)
        val sramCUs = allocateMemoryCU(dmem)
        sramCUs.foreach { sramCU =>
          dbgs(s"sramCUs for dmem=${qdef(dmem)} cu=$sramCU")
          val sram = sramCU.mems(mem)
          sram.writePort = Some(bus)
          sram.writeAddr = ad.map(ad => WriteAddrWire(sram)) //TODO
          sramCU.writeStages(List(sram)) = (writerCU.pipe, addrStages)
        }
      }
    }
  }

  def allocateFringe(fringe:Symbol, dram:Symbol, streamOuts:List[Symbol]) = {
    val cu = allocateCU(fringe)
    val mode = fringeToMode(fringe)
    streamOuts.foreach { streamOut =>
      decompose(streamOut).foreach { mem => allocateMem(mem, fringe, cu) }
    }
  }

  override protected def visit(lhs: Sym[_], rhs: Op[_]) = {
    dbgl(s"Visiting $lhs = $rhs") {
      rhs match {
        case Hwblock(func,_) =>
          allocateCU(lhs)

        case UnitPipe(en, func) =>
          prescheduleStages(lhs, func)

        case UnrolledForeach(en, cchain, func, iters, valids) =>
          prescheduleStages(lhs, func)
          allocateCChains(lhs) 

        case UnrolledReduce(en, cchain, accum, func, reduce, iters, valids, rV) =>
          prescheduleStages(lhs, func)
          allocateCChains(lhs) 

        case FringeDenseLoad(dram, cmdStream, dataStream) =>
          allocateFringe(lhs, dram, List(cmdStream))

        case FringeDenseStore(dram, cmdStream, dataStream, ackStream) =>
          allocateFringe(lhs, dram, List(cmdStream, dataStream))

        case FringeSparseLoad(dram, addrStream, dataStream) =>
          allocateFringe(lhs, dram, List(addrStream))

        case FringeSparseStore(dram, cmdStream, ackStream) =>
          allocateFringe(lhs, dram, List(cmdStream))

        case rhs if isLocalMem(lhs) =>
          allocateLocalMem(lhs)
          if (isGetDRAMAddress(lhs)) prescheduleLocalMemRead(lhs, lhs) //Hack: GetDRAMAddress is both the mem and the reader

        case rhs if isRemoteMem(lhs) =>
          allocateMemoryCU(lhs)
          
        case SimpleStruct(elems) => decompose(lhs)

        case ParLocalReader(reads)  =>
          val (mem, addrs, ens) = reads.head
          if (isLocalMemAccess(lhs)) { // RegRead, FIFODeq, StreamDeq
            prescheduleLocalMemRead(mem, lhs)
          } else { // SRAMLoad
            prescheduleRemoteMemRead(mem, lhs)
          }

        case ParLocalWriter(writes)  => 
          val (mem, value, addrs, ens) = writes.head
          if (isLocalMemAccess(lhs)) { // RegWrite, FIFOEnq, StreamEnq
            prescheduleLocalMemWrite(mem, lhs)
          } else { // SRAMStore
            prescheduleRemoteMemWrite(mem, lhs)
          }

        // Something bad happened if these are still in the IR
        case _:OpForeach => throw new Exception(s"Disallowed compact op $lhs = $rhs")
        case _:OpReduce[_] => throw new Exception(s"Disallowed compact op $lhs = $rhs")
        case _:OpMemReduce[_,_] => throw new Exception(s"Disallowed compact op $lhs = $rhs")
        case _ => 
      }
    }
    super.visit(lhs, rhs)
  }

  override def preprocess[S:Staged](b: Block[S]): Block[S] = {
    top = None
    mapping.clear()
    super.preprocess(b)
  }

  override def postprocess[S:Staged](b: Block[S]): Block[S] = {
    val cus = mapping.values.flatten
    val owner = cus.flatMap{cu => cu.srams.map{sram =>
      (sram.reader, sram.mem) -> (cu, sram)
    }}.toMap

    for (cu <- cus) {
      var nSRAMs = cu.srams.size
      var prevN = -1
      while (nSRAMs != prevN) {
        prevN = nSRAMs
        //addSRAMsReadInWriteStages() //handle data depended load/store?
        nSRAMs = cu.srams.size
      }

      //def addSRAMsReadInWriteStages() {
        //val writeStages = cu.writeStages.values.flatMap(_._2).toList

        //val newWrites = writeStages.foreach{
          //case DefStage(reader@LocalReader(reads), _) => reads foreach {
            //case (mem,addr,en) =>
              //if (isBuffer(mem) && !cu.srams.exists{sram => sram.reader == reader && sram.mem == mem}) {
                //val (ownerCU,orig) = owner((reader, mem))
                //val copy = orig.copyMem(orig.name+"_"+quote(cu.pipe))
                //cu.srams += copy

                //val key = ownerCU.writeStages.keys.find{key => key.contains(orig)}

                //if (key.isDefined) {
                  //cu.writeStages += List(copy) -> ownerCU.writeStages(key.get)
                //}
                //dbgs(s"Adding SRAM $copy ($reader, $mem) read in write stage of $cu")
              //}
          //}
          //case _ =>
        //}
      //}

    }

    for (cu <- cus) {
      for (sram <- cu.srams) {
        initializeSRAM(sram, sram.mem, sram.reader, cu)
      }
    }

    dbgs(s"\n\n//----------- Finishing Allocation ------------- //")
    dbgblk(s"decomposition") {
      dbgs(s"decomposed.keys=${decomposed.keys.toList.mkString(s",")}")
      decomposed.foreach { case(s, dss) => dbgs(s"${qdef(s)} -> [${dss.mkString(",")}]") }
    }
    dbgblk(s"composition") {
      dbgs(s"composed.keys=${composed.keys.toList.mkString(s",")}")
      composed.foreach { case(ds, s) => dbgs(s"${qdef(ds)}")}
    }
    dbgs(s"// ----- CU Allocation ----- //")
    mapping.foreach { case (sym, cus) =>
      cus.foreach { cu => dbgpcu(cu) }
    }

    super.postprocess(b)
  }
}
