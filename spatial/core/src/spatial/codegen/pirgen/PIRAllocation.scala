package spatial.codegen.pirgen
import spatial.SpatialExp
import org.virtualized.SourceContext

import scala.collection.mutable

trait PIRAllocation extends PIRTraversal {
  val IR: SpatialExp with PIRCommonExp
  import IR._

  override val name = "PIR CU Allocation"

  // -- State
  var top: Option[Expr] = None
  var mapping = mutable.Map[Expr, List[PCU]]()

  private def controllersHack(pipe: Expr): List[Expr] = pipe match {
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
  def pipeDependencies(pipe: Expr): List[Expr] = parentOf(pipe) match {
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

  def allocateCChains(pipe: Expr) = {
    val cchainOpt = pipe match {
      case Def(UnrolledForeach(en, cchain, func, iters, valids)) => 
        Some((cchain, iters, valids))
      case Def(UnrolledReduce(en, cchain, accum, func, reduce, iters, valids, rV)) => 
        Some((cchain, iters, valids))
      case Def(_:UnitPipe | _:Hwblock) => 
        val cu = allocateCU(pipe)
        val ctr = CUCounter(ConstReg(1), ConstReg(1), ConstReg(1), 1)
        val cc = CChainInstance(s"${pipe}_unit", List(ctr))
        cu.cchains += cc
        None
      case _ => None
    }
    cchainOpt.foreach { case (cchain, iters, valids) =>
      dbgblk(s"Allocate cchain ${qdef(cchain)} for $pipe") {
        val cu = allocateCU(pipe)
        def allocateCounter(start: Expr, end: Expr, stride: Expr, par:Int) = {
          val min = cu.getOrElseUpdate(start){ const(start) }
          val max = cu.getOrElseUpdate(end){ const(end) }
          val step = cu.getOrElseUpdate(stride){ const(stride) }
          dbgs(s"counter start:${qdef(start)}, end:${qdef(end)}, stride:${qdef(stride)}, par:$par")
          CUCounter(localScalar(min), localScalar(max), localScalar(step), par)
        }
        val Def(CounterChainNew(ctrs)) = cchain
        val counters = ctrs.collect{case ctr@Def(CounterNew(start,end,stride,_)) => 
          val ConstReg(par) = extractConstant(parFactorsOf(ctr).head)
          allocateCounter(start, end, stride, par.asInstanceOf[Int])
        }
        val cc = CChainInstance(quote(cchain), counters)
        cu.cchains += cc
        addIterators(cu, cc, iters, valids)
      }
    }
  }

  def allocateCU(pipe: Expr): PCU = getOrElseUpdate(mapping, pipe, {
    val parent = parentHack(pipe).map(allocateCU)

    val style = pipe match {
      case Def(FringeDenseLoad(dram, _, _))  => 
        FringeCU(allocateDRAM(dram), MemLoad)
      case Def(FringeDenseStore(dram, _, _, _))  => 
        FringeCU(allocateDRAM(dram), MemStore)
      case Def(FringeSparseLoad(dram, _, _))  => 
        FringeCU(allocateDRAM(dram), MemGather)
      case Def(FringeSparseStore(dram, _, _))  => 
        FringeCU(allocateDRAM(dram), MemScatter)
      case _ if isControlNode(pipe) => styleOf(pipe) match {
        case InnerPipe   => PipeCU
        case MetaPipe    => MetaPipeCU
        case SeqPipe     => SequentialCU
        case StreamPipe  => StreamCU
        case ForkJoin    => throw new Exception("ForkJoin is not supported in PIR")
        case ForkSwitch  => throw new Exception("ForkSwitch is not supported in PIR")
      }
    }

    val cu = PseudoComputeUnit(quote(pipe), pipe, style)
    cu.parent = parent

    cu.innerPar = style match {
      case MemoryCU(i) => None
      case FringeCU(dram, mode) => None
      case _ => Some(getInnerPar(pipe))
    } 
 
    if (top.isEmpty && parent.isEmpty) top = Some(pipe)

    dbgs(s"Allocating CU $cu for $pipe")
    List(cu)
  }).head

  def allocateMemoryCU(dsram:Expr):List[PCU] = {
    val cus = getOrElseUpdate(mapping, dsram, { 
      val sram = compose(dsram)
      val parentCU = parentOf(sram).map(allocateCU)
      val writer = writerOf(sram)
      dbgblk(s"Allocating memory cu for ${qdef(sram)}, writer:$writer") {
        readersOf(sram).zipWithIndex.map { case (readAcc, i) => 
          val reader = readAcc.node
          val dreader = getMatchedDecomposed(dsram, reader) 
          val cu = PseudoComputeUnit(s"${quote(dsram)}_dsp$i", dsram, MemoryCU(i))
          dbgs(s"Allocating MCU duplicates $cu for ${quote(dsram)}, reader:${quote(dreader)}")
          cu.parent = parentCU
          val psram = createMem(dsram, dreader, cu)
          cu
        }.toList
      }
    })
    cus
  }

  private def initializeMem(cuMem: CUMemory, reader: Expr, cu: PCU) {
    val mem = compose(cuMem.mem)
    def setBanking = {
      val banking = if (isSRAM(mem)) {
        val writeAccess = writerOf(mem) 
        val writer = writeAccess.node
        val writerCU = allocateCU(writeAccess.ctrlNode)
        val readBanking  = bank(mem, reader)
        val writeBanking = bank(mem, writer)
        cuMem.banking = Some(mergeBanking(writeBanking, readBanking))
      }
      else if (isFIFO(mem) || isStream(mem)) {
        cuMem.banking = Some(Strided(1))
      }
      else {
        cuMem.banking = None
      }
    }
    def setSwapCUs = {
      if (isArgIn(mem) || isArgOut(mem) || isGetDRAMAddress(mem)) {
        cuMem.bufferDepth = 1 
        cuMem.consumer = top.map(allocateCU)
        cuMem.producer = top.map(allocateCU)
      }
      else if (isSRAM(mem) || isReg(mem)) {
        val instIndex = dispatchOf(reader, mem).head
        val instance = duplicatesOf(mem).apply(instIndex)
        val writeAccess = writerOf(mem) 
        val readAccess = getAccess(reader).get
        val swapWritePipe = topControllerOf(writeAccess, mem, instIndex)
        val swapReadPipe  = topControllerOf(readAccess, mem, instIndex)
        val swapReadCU = swapReadPipe.map{ ctrl =>
          val topCtrl = topControllerHack(readAccess, ctrl)
          allocateCU(topCtrl.node)
        }
        val swapWriteCU = swapWritePipe.map { ctrl =>
          val topCtrl = topControllerHack(writeAccess, ctrl)
          allocateCU(topCtrl.node)
        }
        cuMem.consumer = swapReadCU
        cuMem.producer = swapWriteCU
        cuMem.bufferDepth = instance.depth
      }
    }
    setSwapCUs
    setBanking
    cuMem.mode = memMode(mem, cu)
  }
  
  /**
   * @param dmem decomposed memory
   * @param addr address expression
   * @param stms searching scope
   * @return The flatten address symbol and extracted stages
   * Extract address calculation for mem 
   **/
  def extractRemoteAddrStages(dmem: Expr, addr: Option[Seq[Exp[Index]]], stms: Seq[Stm]): (Option[Expr], Seq[PseudoStage])= {
    dbgblk(s"Extracting Remote Addr in for dmem=$dmem addr=$addr") {
      val memCUs = allocateMemoryCU(dmem)
      val flatOpt = addr.map{is => flattenNDIndices(is, stagedDimsOf(compose(dmem).asInstanceOf[Exp[SRAM[_]]])) }
      // Exprs
      val indexExps = addr.map { is => expsUsedInCalcExps(stms)(Seq(), is) }.getOrElse(Nil)
      val indexSyms = indexExps.collect { case s:Sym[_] => s }
      
      //indexSyms.foreach { case lhs@Op(rhs) => visit(lhs, rhs, memCUs); case _ => }

      val ad = flatOpt.map(_._1) // sym of flatten  addr

      dbgs(s"$dmem addr:[${addr.map(_.mkString(","))}], indexExps:[${indexExps.mkString(",")}] indexSyms:[${indexSyms.mkString(",")}]")
      memCUs.foreach { memCU => copyBounds(indexExps ++ addr.getOrElse(Seq()), memCU) }

      // PseudoStages
      val indexStages: Seq[PseudoStage] = indexSyms.map{s => DefStage(s) }
      val flatStages = flatOpt.map(_._2).getOrElse(Nil)
      val remoteAddrStage = ad.map{a => AddrStage(dmem, a) }
      val addrStages = indexStages ++ flatStages ++ remoteAddrStage

      dbgl(s"addrStages:") {
        addrStages.foreach { stage => dbgs(s"$stage") }
      }
      (ad, addrStages)
    }
  }

  def copyBounds(exps: Seq[Expr], cu:PseudoComputeUnit) = {
    val allExps = exps ++ exps.flatMap{x => getDef(x).map(_.expInputs).getOrElse(Nil) }

    allExps.foreach {
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

  /*
   * Schedule stages of PCU corresponding to pipe
   * */
  def prescheduleStages(pipe: Expr, func: Block[Any]):PseudoComputeUnit = dbgblk(s"prescheduleStages ${qdef(pipe)}") {
    val cu = allocateCU(pipe)

    val remotelyAddedStages = cu.computeStages // Stages added prior to traversing this pipe
    val remotelyAddedStms = remotelyAddedStages.flatMap(_.output).flatMap{
      case s: Sym[_] => Some(stmOf(s))
      case _ => None
    }


    val stms = remotelyAddedStms ++ blockContents(func)

    cu.computeStages.clear() // Clear stages so we don't duplicate existing stages
    
    // sram loads that are not just used in index calculation 
    //var nonIndOnlyLoads:List[Expr] = nonIndOnlySRAMLoads(func, stms)

    val localCompute = symsUsedInCalcExps(stms)(Seq(func.result), func.effectful)
    copyBounds(localCompute , cu)

    // Sanity check
    val trueComputation = localCompute.filterNot{case Exact(_) => true; case s => isRegisterRead(s)}
    if (isOuterControl(pipe)) {
      if (trueComputation.nonEmpty) {
        warn(s"Outer control $pipe has compute stages: ")
        trueComputation.foreach{case lhs@Def(rhs) => warn(s"$lhs = $rhs")}
      }
    }
    else { // Only inner pipes have stages
      cu.computeStages ++= localCompute.map{ s => 
        val isReduce = (s match {
          case Def(RegRead(_)) => false
          case Def(RegWrite(_,_,_)) => false
          case s => reduceType(s).isDefined
        }) && !isBlockReduce(func)
        DefStage(s, isReduce = isReduce)
      }
      dbgl(s"prescheduled stages for $cu:") {
        cu.computeStages.foreach {
          case s@DefStage(op, _) => dbgs(s"$s reduceType=${reduceType(op)}")
          case s => dbgs(s"$s")
        }
      }
    }
    cu
  }

  def memMode(dmem:Expr, cu:PCU) = (compose(dmem), cu.style) match {
    case (mem, style) if isReg(mem) | isGetDRAMAddress(mem) => ScalarBufferMode
    case (mem, style) if isStreamIn(mem) => VectorFIFOMode // from Fringe
    case (mem, style) if isFIFO(mem) | isStreamOut(mem) => 
      val writer = writerOf(mem)
      if (getInnerPar(writer.ctrlNode)==1) ScalarFIFOMode else VectorFIFOMode
    case (mem, MemoryCU(i)) if isSRAM(mem) => SRAMMode
    case (mem, PipeCU) if isSRAM(mem) => VectorFIFOMode
  }

  /*
   * @param dmem decomposed memory
   * @param dreader decomposed reader
   * @param cu 
   * Create memory (Reg/FIFO/SRAM/Stream) inside cu for dreader
   * */
  def createMem(dmem: Expr, dreader: Expr, cu: PCU): CUMemory =  {
    val cuMem = getOrElseUpdate(cu.memMap, dmem, {
      val name = if (isGetDRAMAddress(dmem)) s"${quote(dmem)}"
                 else s"${quote(dmem)}_${quote(dreader)}"
      val size = (compose(dmem), cu.style) match {
        case (m, MemoryCU(i)) if isSRAM(m) => dimsOf(m.asInstanceOf[Exp[SRAM[_]]]).product
        case (m, PipeCU) if isSRAM(m) => dimsOf(m.asInstanceOf[Exp[SRAM[_]]]).product
        case (m, style) if isSRAM(m) => dimsOf(m.asInstanceOf[Exp[SRAM[_]]]).product
        case (m, style) if isFIFO(m) => 
          sizeOf(m.asInstanceOf[Exp[FIFO[Any]]]) match { case Exact(d) => d.toInt }
        case (m, style) if isReg(m) | isStream(m) | isGetDRAMAddress(m) => 1
      }
      val cuMem = CUMemory(name, size, dmem, dreader, cu)
      dbgs(s"Add mem=$cuMem to cu=$cu")
      initializeMem(cuMem, compose(dreader), cu)
      cuMem
    })
    cuMem
  }

  /*
   * @param mem original memory Expr
   * Allocate local memory inside the reader
   * */
  def allocateLocalMem(mem:Expr) = dbgblk(s"allocateLocalMem($mem)"){
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
        bus.foreach { b => globals += b }
        getReaderCUs(reader).foreach { readerCU =>
          if (!localWritten) { // Write to FIFO/StreamOut/RemoteReg
            // Allocate local mem in the readerCU
            createMem(dmem, dreader, readerCU)
            // Set writeport of the local mem who doesn't have a writer (ArgIn and GetDRAMAddress)
            bus.foreach { bus => readerCU.memMap(dmem).writePort = Some(bus) }
          } else { // Local reg accumulation
            readerCU.getOrElseUpdate(dmem) {
              val Def(RegNew(init)) = mem //Only register can be locally written
              AccumReg(extractConstant(init))
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
  def getReaderCUs(reader: Expr): List[PseudoComputeUnit] = dbgblk(s"getReaderCUs ${qdef(reader)}") {
    val readerCUs = mutable.Set[PseudoComputeUnit]()
    if (isFringe(reader)) { readerCUs += allocateCU(reader) } // Fringe is considered to be a reader of the stream
    else {
      parentOf(reader).foreach { pipe => // RegRead outside HwBlock doesn't have parent
        val stms = getStms(pipe)
        def addParentCU(s: Expr, d:Def, mem: Expr, ind: Option[Seq[Expr]]) = {
          val indSyms = ind.map { ind => symsUsedInCalcExps(stms)(Seq(), ind) }.getOrElse(Nil)
          if (indSyms.contains(reader) && isRemoteMem(mem)) {
            readerCUs ++= decompose(mem).flatMap(allocateMemoryCU)
          } else if (d.allInputs.contains(reader) || (s==reader && isInnerPipe(pipe)) ) { //RegRead can occur outside user
            readerCUs += allocateCU(pipe)
          }
        }
        dbgl(s"$pipe's stms:") { stms.foreach { stm => dbgs(s"$stm") } }
        stms.foreach {
          case TP(s, d@ParLocalReader(reads)) =>
            val (mem, inds, _) = reads.head
            addParentCU(s, d, mem, inds.map{_.head})
          case TP(s, d@ParLocalWriter(writes)) =>
            val (mem, _, inds, _) = writes.head
            addParentCU(s, d, mem, inds.map{_.head})
          case TP(s@Def(_:CounterNew), d) if d.allInputs.contains(reader) => readerCUs ++= getReaderCUs(s)
          case TP(s@Def(_:CounterChainNew), d) if d.allInputs.contains(reader) => readerCUs ++= getReaderCUs(s)
          case TP(s, d) if d.allInputs.contains(reader) & isControlNode(s) => readerCUs += allocateCU(s)
          case TP(s, d) if d.allInputs.contains(reader) => readerCUs += allocateCU(pipe) // Include pipe only if it's used 
          case TP(s, d) => 
        }
      }
    }
    dbgs(s"ReaderCUs for ${qdef(reader)} = [${readerCUs.mkString(",")}]")
    readerCUs.toList
  }

  /**
   * @param dwriter decomposed writer
   * @return If value/data of the writer is from a load of SRAM, returns the MCU, otherwise returns the
   * PCU
   **/
  def getWriterCU(dwriter:Expr) = {
    val writer = compose(dwriter)
    val ParLocalWriter(writes) = writer 
    val pipe = parentOf(writer).get 
    val (mem, value, inds, ens) = writes.head
    // TODO: leave the optimization to PIROptimizer
    //value.get match {
      //case reader@ParLocalReader(reads) =>
        //val (mem, _, _) = reads.head
        //if (isRemoteMem(mem)) {
          //val dmem = getMatchedDecomposed(dwriter, mem)
          //getMCUforReader(dmem, reader)
        //} else {
          //allocateCU(pipe)
        //}
      //case _ => allocateCU(pipe)
    //}
    allocateCU(pipe)
  }

  def getMCUforReader(dmem:Expr, reader:Expr) = {
    val mem = compose(dmem)
    val idx = readersOf(mem).map(_.node).indexOf(reader)
    allocateMemoryCU(dmem)(idx)
  } 

  def prescheduleLocalMemRead(mem: Expr, reader:Expr) = {
    dbgblk(s"Allocating local memory read: $reader") {
      getReaderCUs(reader).foreach { readerCU =>
        decompose(mem).zip(decompose(reader)).foreach { case (dmem, dreader) =>
          val locallyWritten = isLocallyWritten(dmem, dreader, Some(readerCU))
          dbgs(s"$mem isLocalMemWrite:$locallyWritten readerCU:$readerCU dreader:$dreader")
          if (locallyWritten) {
            val reg = readerCU.get(dmem).get // Accumulator should be allocated during RegNew
            readerCU.addReg(dreader, reg)
          } else {
            val pmem = readerCU.memMap(dmem)
            readerCU.addReg(dreader, MemLoadReg(pmem))
          }
        }
      }
    }
  }

  def prescheduleLocalMemWrite(mem: Expr, writer:Expr) = {
    dbgblk(s"Allocating local memory write: $writer of mem:${quote(mem)}") {
      val remoteReaders = getRemoteReaders(mem, writer)
      dbgs(s"remoteReaders:${remoteReaders.mkString(",")}")
      if (remoteReaders.nonEmpty || isArgOut(mem)) {
        allocateLocalMem(mem)
        decompose(mem).zip(decompose(writer)).foreach { case (dmem, dwriter) =>
          dbgs(s"dmem:$dmem, dwriter:$dwriter")
          val parBy1 = getInnerPar(writerOf(mem).ctrlNode)==1
          val (bus, output) = if (isReg(mem) || ((isFIFO(mem) || isStream(mem)) && parBy1)) {
            val bus = if (isArgOut(mem)) 
              OutputArg(s"${quote(dmem)}_${quote(dwriter)}") 
            else
              CUScalar(s"${quote(dmem)}_${quote(dwriter)}")
            globals += bus
            (bus, ScalarOut(bus))
          } else {
            val bus = CUVector(s"${quote(dmem)}_${quote(dwriter)}")
            globals += bus
            (bus, VectorOut(bus))
          }
          val writerCU = getWriterCU(dwriter) 
          writerCU.addReg(dwriter, output)
          dbgs(s"Add dwriter:$dwriter to writerCU:$writerCU")
          remoteReaders.foreach { reader =>
            getReaderCUs(reader).foreach { readerCU =>
              readerCU.memMap(dmem).writePort = Some(bus)
            }
          }
        }
      }
    }
  }

  def prescheduleRemoteMemRead(mem: Expr, reader:Expr) = {
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
          globals += bus
          val vfifo = createMem(dmem, dreader, readerCU)
          readerCU.addReg(dreader, MemLoadReg(vfifo))
          vfifo.writePort = Some(bus)
          // Schedule address calculation
          val (ad, addrStages) = extractRemoteAddrStages(dmem, addr, stms)
          val sramCUs = allocateMemoryCU(dmem)
          sramCUs.foreach { sramCU =>
            dbgs(s"sramCUs for dmem=${qdef(dmem)} cu=$sramCU")
            val sram = sramCU.memMap(mem)
            sramCU.readStages(List(sram)) = (readerCU.pipe, addrStages)
            sram.readPort = Some(bus)
            //sram.readAddr = ad.map(ad => ReadAddrWire(sram))
          }
        }
      }
    }
  }

  def prescheduleRemoteMemWrite(mem: Expr, writer:Expr) = {
    dbgblk(s"Allocating remote memory write: ${qdef(writer)}") {
      val pipe = parentOf(writer).get
      val stms = getStms(pipe)
      val ParLocalWriter(writes) = writer
      val (mem, value, addrs, ens) = writes.head
      val addr = addrs.map(_.head)
      decompose(mem).zip(decompose(writer)).foreach { case (dmem, dwriter) =>
        val bus = CUVector(s"${quote(dmem)}_${quote(dwriter)}")
        globals += bus
        val writerCU = getWriterCU(dwriter) 
        dbgs(s"writerCU = $writerCU")
        writerCU.addReg(dwriter, VectorOut(bus))
        // Schedule address calculation
        val (ad, addrStages) = extractRemoteAddrStages(dmem, addr, stms)
        val sramCUs = allocateMemoryCU(dmem)
        sramCUs.foreach { sramCU =>
          dbgs(s"sramCUs for dmem=${qdef(dmem)} cu=$sramCU")
          val sram = sramCU.memMap(mem)
          sram.writePort = Some(bus)
          //sram.writeAddr = ad.map(ad => WriteAddrWire(sram))
          sramCU.writeStages(List(sram)) = (writerCU.pipe, addrStages)
        }
      }
    }
  }

  def allocateFringe(fringe: Expr, dram: Expr, streamIns: List[Expr], streamOuts: List[Expr]) = {
    val cu = allocateCU(fringe)
    val mode = cu.style match { 
      case FringeCU(dram, mode) => mode
      case _ => throw new Exception(s"fringe's style is nt FringeCU ${cu.style}")
    }
    streamIns.foreach { streamIn =>
      val readerCUs = readersOf(streamIn).map(_.node).flatMap(getReaderCUs)
      val dmems = decomposeWithFields(streamIn) match {
        case Right(dmems) if dmems.size==1 => dmems
        case Right(dmems) => throw new Exception(s"PIR don't support struct load/gather ${qdef(fringe)}") 
      }
      dmems.foreach { case (field, dmem) =>
        val bus = CUVector(s"${quote(dmem)}_${quote(fringe)}_$field")
        cu.fringeVectors += field -> bus
        globals += bus
        readerCUs.foreach { _.memMap(dmem).writePort = Some(bus) }
      }
    }
    streamOuts.foreach { streamOut =>
      decompose(streamOut).foreach { mem => createMem(mem, fringe, cu) }
    }
  }

  override protected def visit(lhs: Sym[_], rhs: Op[_]) = {
    dbgl(s"Visiting $lhs = $rhs") {
      rhs match {
        case Hwblock(func,_) =>
          allocateCU(lhs)
          allocateCChains(lhs) 

        case UnitPipe(en, func) =>
          allocateCU(lhs)
          prescheduleStages(lhs, func)
          allocateCChains(lhs) 

        case UnrolledForeach(en, cchain, func, iters, valids) =>
          allocateCU(lhs)
          prescheduleStages(lhs, func)
          allocateCChains(lhs) 

        case UnrolledReduce(en, cchain, accum, func, reduce, iters, valids, rV) =>
          allocateCU(lhs)
          prescheduleStages(lhs, func)
          allocateCChains(lhs) 

        case _ if isFringe(lhs) =>
          val dram = rhs.allInputs.filter { e => isDRAM(e) }.head
          val streamIns = rhs.allInputs.filter { e => isStreamIn(e) }.toList
          val streamOuts = rhs.allInputs.filter { e => isStreamOut(e) }.toList
          allocateFringe(lhs, dram, streamIns, streamOuts)

        case _ if isLocalMem(lhs) =>
          allocateLocalMem(lhs)
          if (isGetDRAMAddress(lhs)) prescheduleLocalMemRead(lhs, lhs) //Hack: GetDRAMAddress is both the mem and the reader

        case _ if isRemoteMem(lhs) =>
          allocateMemoryCU(lhs)
          
        case SimpleStruct(_) => decompose(lhs)

        case ParLocalReader(reads)  =>
          val (mem, _, _) = reads.head
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

  override def preprocess[S:Type](b: Block[S]): Block[S] = {
    top = None
    mapping.clear()
    super.preprocess(b)
  }

  override def postprocess[S:Type](b: Block[S]): Block[S] = {
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
