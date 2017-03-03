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
  var mapping = mutable.HashMap[Symbol, List[PCU]]()

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

    //parentHack(pipe).foreach{parent => copyIterators(cu, allocateCU(parent)) }

    val Def(rhs) = pipe
    val ccs = syms(rhs).collect{
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

  def qdef(lhs:Symbol):String = {
    val rhs = lhs match {
      case Def(e:UnrolledForeach) => 
        s"UnrolledForeach(iters=(${e.iters.mkString(",")}), valids=(${e.valids.mkString(",")}))"
      case Def(e:UnrolledReduce[_,_]) => 
        s"UnrolledReduce(iters=(${e.iters.mkString(",")}), valids=(${e.valids.mkString(",")}))"
      case Def(BurstLoad(dram, fifo, ofs, ctr, i)) =>
        s"BurstLoad(dram=$dram, fifo=$fifo, ofs=$ofs, ctr=$ctr, i=$i)"
      case Def(BurstStore(dram, fifo, ofs, ctr, i)) =>
        s"BurstStore(dram=$dram, fifo=$fifo, ofs=$ofs, ctr=$ctr, i=$i)"
      case Def(d) if isControlNode(lhs) => s"${d.getClass.getSimpleName}(binds=${d.binds})"
      case Op(rhs) => s"$rhs"
      case Def(rhs) => s"$rhs"
    }
    s"$lhs = $rhs"
  }

  def allocateComputeUnit(pipe: Symbol): PCU = mapping.getOrElseUpdate(pipe, {
    val parent = parentHack(pipe).map(allocateCU)

    val style = pipe match {
      case Def(_:UnitPipe) => UnitCU
      case Def(_:Hwblock)  => UnitCU
      case Def(_:BurstLoad[_]) => UnitCU
      case Def(_:BurstStore[_]) => UnitCU
      case Def(_:Scatter[_]) => StreamCU
      case Def(_:Gather[_]) => StreamCU
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
      case Def(_:BurstLoad[_] | _:BurstStore[_]) => cu.cchains += UnitCChain(quote(pipe)+"_unitcc")
      case Def(e:Scatter[_]) =>
      case Def(e:Gather[_]) =>
      case _ =>
    }
    if (top.isEmpty && parent.isEmpty) top = Some(pipe)

    dbgs(s"Allocating CU $cu for ${pipe}")
    List(cu)
  }).head

  def allocateCU(pipe: Symbol): PCU = pipe match {
    case Def(_:Hwblock)             => allocateComputeUnit(pipe)
    case Def(_:UnrolledForeach)     => allocateComputeUnit(pipe)
    case Def(_:UnrolledReduce[_,_]) => allocateComputeUnit(pipe)
    case Def(_:UnitPipe)            => allocateComputeUnit(pipe)

    case Def(_:BurstLoad[_])  => allocateComputeUnit(pipe)
    case Def(_:BurstStore[_]) => allocateComputeUnit(pipe)
    case Def(_:Scatter[_])    => allocateComputeUnit(pipe)
    case Def(_:Gather[_])     => allocateComputeUnit(pipe)

    case Def(d) => throw new Exception(s"Don't know how to generate CU for\n  $pipe = $d")
  }

  def allocateMemoryCU(sram:Symbol):List[PCU] = mapping.getOrElseUpdate(sram, {
    val Def(d) = sram

    val readAccesses = readersOf(sram).groupBy { read => 
      val id = dispatchOf(read, sram)
      assert(id.size==1)
      id.head
    }

    val writeAccesses = writersOf(sram)
    assert(writeAccesses.size==1, s"Plasticine currently only supports single writer at the moment $sram writeAccesses:$writeAccesses")
    val writeAccess = writeAccesses.head
    dbgblk(s"Allocating memory cu for $sram = $d, writeAccess:${writeAccess}") {
      duplicatesOf(sram).zipWithIndex.map{ case (mem, i) => 

        val readAccess = readAccesses(i).head
        val cu = PseudoComputeUnit(s"${quote(sram)}_dsp${i}", sram, MemoryCU(i))
        dbgs(s"Allocating MCU duplicates $cu for $sram, readAccess:$readAccess")
        //val readerCU = allocateCU(readAccess.ctrlNode)
        //val parent = if (readAccess.ctrlNode==writeAccess.ctrlNode) //Not multibuffered
          //parentHack(readAccess.ctrlNode).map(allocateCU)
        //else {
          //parentHack(topControllerOf(readAccess, sram, i).get.node).map(allocateCU)
        //}
        cu.parent = None

        //initCU(cu, pipe)

        //val readerCU = allocateCU(readAccess.ctrlNode)
        val psram = allocateMem(sram, readAccess.node, cu)

        //pipe match {
          //case Def(e:UnrolledForeach)      => addIterators(cu, e.cchain, e.iters, e.valids)
          //case Def(e:UnrolledReduce[_,_])  => addIterators(cu, e.cchain, e.iters, e.valids)
          //case Def(_:UnitPipe | _:Hwblock) => cu.cchains += UnitCChain(quote(pipe)+"_unitcc")
          //case _ =>
        //}

        cu
      }.toList
    }
  })

  def prescheduleRegisterRead(reg: Symbol, reader: Symbol, pipe: Option[Symbol]) = {
    dbgblk(s"Allocating register read: $reader") {
      // Register reads may be used by more than one pipe
      readersOf(reg).filter(_.node == reader).map(_.ctrlNode).foreach{ readCtrl =>
        val isCurrentPipe = pipe.exists(_ == readCtrl)
        val isLocallyWritten = isWrittenInPipe(reg, readCtrl)

        if (!isCurrentPipe || !isLocallyWritten) {
          val readerCU = allocateCU(readCtrl)
          dbgs(s"Adding reader stage $reader of reg($reg) to readerCU $readerCU")
          readerCU.computeStages += DefStage(reader)
        }
      }
    }
  }

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
          dbgblk(s"$sramCU Write stages: ") {
            stages.foreach{stage => dbgs(s"$stage")}
          }
          sramCU.writeStages(sramCU.srams.toList) = (writerCU.pipe,stages)
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

  def scheduleRead(reader:Symbol, reads:List[ParLocalRead], stms:Seq[Stm], cus:List[PCU], pipe:Symbol,
                   func:Block[Any], remoteStages:mutable.Set[Symbol]) = {
    val rhs = reader match {case Def(d) => d; case _ => null }
    dbgblk(s"$reader = $rhs [READER]") {
      reads.zipWithIndex.foreach{ case ((mem,addrs,ens), i) =>
        val addr = addrs.map{_.head}
        if (isReg(mem)) {
          prescheduleRegisterRead(mem, reader, Some(pipe))
          val isLocallyRead = isReadInPipe(mem, pipe, Some(reader)) //TODO: should this be outside reads.foreach ?
          val isLocallyWritten = isWrittenInPipe(mem, pipe)
          //dbgs(s"isLocallyRead: $isLocallyRead, isLocallyWritten: $isLocallyWritten")
          if (!isLocallyWritten || !isLocallyRead || isInnerAccum(mem)) remoteStages += reader
        }
        else if (isBuffer(mem)) {
          val addrStages = extractRemoteAddrStages(mem, addr, stms, cus(i), pipe, func, remoteStages)
          allocateReadSRAM(reader, mem, cus(i), addrStages)
        }
      }
    }
  }

  def scheduleWrite(writer:Symbol, writes:List[ParLocalWrite], stms:Seq[Stm], cu:PCU, pipe:Symbol, 
                    func:Block[Any], remoteStages:mutable.Set[Symbol]) = {
    val rhs = writer match {case Def(d) => d; case _ => null }
    dbgblk(s"$writer = $rhs [WRITER]") {
      writes.foreach{case (mem, value, addrs, ens) =>
        dbgs(s"Checking if $mem write can be implemented as FIFO-on-write:")
        val addr = addrs.map{_.head}
        val writeAsFIFO = value.exists{v => useFifoOnWrite(mem, v) }

        if ((isBuffer(mem) || isFIFO(mem)) && writeAsFIFO) {
          // This entire section is a hack to support FIFO-on-write for burst loads
          val enableComputation = ens.map{e => getScheduleForAddress(stms)(Seq(e)) }.getOrElse(Nil)
          val enableSyms = enableComputation.map{case TP(s,d) => s} ++ ens
          dbgs(s"write(mem=$mem, value=$value, addr=$addr, ens=$ens):")
          dbgs(s"ens:$ens enableSyms:${enableSyms.mkString(",")}")

          val (start,end) = getFifoBounds(ens)

          val startX = start match {case start@Some(Def(_:RegRead[_])) => start; case _ => None }
          val endX = end match {case end@Some(Def(_:RegRead[_])) => end; case _ => None }

          val remoteWriteStage = FifoOnWriteStage(mem, startX, endX)
          val enStages = startX.map{s => DefStage(s) }.toList ++ endX.map{s => DefStage(s) }.toList
          if (enStages.nonEmpty) dbgblk(s"boundStages:") { enStages.foreach { stage => dbgs(s"$stage") } }

          allocateWrittenSRAM(writer, mem, cu, enStages ++ List(remoteWriteStage))

          //TODO consider parSRAMStore, localWrite addr will be None 
          val indexSyms = addr.map { is => symsUsedInCalcExps(stms)(is) }.getOrElse(Nil)
          dbgs(s"indexSyms:$indexSyms")
          val remoteSyms = symsOnlyUsedInWriteAddrOrEn(stms)(func.result +: func.effectful, indexSyms ++ enableSyms)
          dbgs(s"symsOnlyUsedInWriteAddrOrEn: $remoteSyms")
          remoteStages ++= remoteSyms
        }
        else if (isBuffer(mem)) {
          val addrStages = extractRemoteAddrStages(mem, addr, stms, cu, pipe, func, remoteStages)
          allocateWrittenSRAM(writer, mem, cu, addrStages)
        }
      }
    }
  }

  def extractRemoteAddrStages(mem:Symbol, addr:Option[Seq[Exp[Index]]], stms:Seq[Stm], cu:PCU, pipe:Symbol, 
                              func:Block[Any], remoteStages:mutable.Set[Symbol]):List[PseudoStage]= {
    dbgblk(s"Extracting Remote Addr in $cu for mem=$mem addr=$addr") {
      val flatOpt = addr.map{is => flattenNDIndices(is, stagedDimsOf(mem.asInstanceOf[Exp[SRAM[_]]])) }
      // Symbols
      var indexSyms = addr.map { is => symsUsedInCalcExps(stms)(is) }.getOrElse(Nil)
      val depRemoteStages = mutable.Set[Symbol]()   // Stages to calculate data depended addr calc
      indexSyms.foreach {
        case reader@ParLocalReader(reads) if isBuffer(reads.head._1) =>
          val memCUs = allocateMemoryCU(mem)
          scheduleRead(reader, reads, stms, memCUs, pipe, func, depRemoteStages)
        case _ =>
      }
      indexSyms = indexSyms.filterNot { s => depRemoteStages.contains(s) }
      val ad = flatOpt.map(_._1) // sym of flatten  addr

      dbgs(s"$cu addr:[${addr.map(_.mkString(","))}], indexSyms:[${indexSyms.mkString(",")}], depRemoteStages:[${depRemoteStages.mkString(",")}]")

      // PseudoStages
      val indexStages:List[PseudoStage] = indexSyms.map{s => DefStage(s) }
      val flatStages = flatOpt.map(_._2).getOrElse(Nil)
      val remoteAddrStage = ad.map{a => AddrStage(mem, a) }
      val addrStages = indexStages ++ flatStages ++ remoteAddrStage

      //val isLocallyRead = isReadInPipe(mem, pipe)

      // Currently have to duplicate if used in both address and compute
      //if (indexSyms.nonEmpty && !isLocallyRead) 
      if (indexSyms.nonEmpty) {
        //dbg(s"Checking if symbols calculating ${addr.get} are used in current scope $pipe")
        val symAddr = symsOnlyUsedInWriteAddr(stms)(func.result +: func.effectful, indexSyms)
        dbgs(s"symsOnlyUsedInAddr:[${symAddr.mkString(",")}]")
        remoteStages ++= symAddr
      }
      addrStages
    }
  }

  // HACK: Ignore write address for SRAMs written from popping the result of tile loads
  // (Skipping the vector packing/unpacking nonsense in between)
  def useFifoOnWrite(mem: Exp[Any], value: Exp[Any]): Boolean = dbgblk(s"useFifoOnWrite($mem)") {
    value match { //TODO how about gather??
      case Def(FIFODeq(fifo, en, _))     =>
        dbgs(s"$value = pop($fifo) [${writersOf(fifo)}]")
        writersOf(fifo).forall{writer => writer.node match {case Def(_:BurstLoad[_]) => true; case _ => false }}
      case Def(ParFIFODeq(fifo, ens, _)) =>
        dbgs(s"$value = pop($fifo) [${writersOf(fifo)}]")
        writersOf(fifo).forall{writer => writer.node match {case Def(_:BurstLoad[_]) => true; case _ => false }}
      case Def(ListVector(elems))    =>
        dbgs(s"$value = vector")
        useFifoOnWrite(mem, elems.head)
      case Def(VectorApply(vector,index))     =>
        dbgs(s"$value = vec")
        useFifoOnWrite(mem, vector)
      case _ =>
        dbgs(s"$value -> no FIFO-on-write")
        //dbg(s"Written value is $value = $d: FIFO-on-write disallowed")
        false
    }
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

  def prescheduleStages(pipe: Symbol, func: Block[Any]):Unit = dbgblk(s"prescheduleStages ${qdef(pipe)}") {
    val cu = allocateCU(pipe)

    val remotelyAddedStages = cu.computeStages // Stages added prior to traversing this pipe
    val remotelyAddedStms = remotelyAddedStages.flatMap(_.output).flatMap{
      case s: Sym[_] => Some(stmOf(s))
      case _ => None
    }

    //Hack Check if func is inside block reduce
    val reduceSRAM = func.summary.reads.intersect(func.summary.writes).filter(isSRAM).nonEmpty

    val stms = remotelyAddedStms ++ blockContents(func)
    val stages = stms.map{case TP(lhs,rhs) => lhs}

    val remoteStages = mutable.Set[Symbol]()   // Stages to ignore (goes on different CU)

    cu.computeStages.clear() // Clear stages so we don't duplicate existing stages
    
    // sram loads that are not just used in index calculation 
    var nonIndOnlyLoads:List[Symbol] = nonIndOnlySRAMLoads(func, stms)

    /* Schedule remote addr calculation */
    foreachSymInBlock(func){
      // NOTE: Writers always appear to occur in the associated writer controller
      // However, register reads may appear outside their corresponding controller
      case writer@ParLocalWriter(writes) if !isControlNode(writer) =>
        scheduleWrite(writer, writes, stms, cu, pipe, func, remoteStages)

      case reader@ParLocalReader(reads) if !isControlNode(reader) & !nonIndOnlyLoads.contains(reader) =>
        scheduleRead(reader, reads, stms, List(cu), pipe, func, remoteStages)

      case reader@ParLocalReader(reads) if !isControlNode(reader) & nonIndOnlyLoads.contains(reader) =>
        dbgs(s"${qdef(reader)} [Index]")
        val Def(d) = reader
        visitFat(List(reader), d)

      case lhs@Op(rhs) => //TODO
        dbgs(s"${qdef(lhs)} [OTHER]")
        visit(lhs, rhs)
      case lhs@Def(rhs) =>
        dbgs(s"${qdef(lhs)} [OTHER]")
        visitFat(List(lhs), rhs)
    }

    val localCompute = stages.filter{s => (isPrimitiveNode(s) || isRegisterRead(s) || isGlobal(s) || isVector(s)) && !remoteStages.contains(s) }

    // Sanity check
    val trueComputation = localCompute.filterNot{case Exact(_) => true; case s => isRegisterRead(s)}
    if (isOuterControl(pipe) && trueComputation.nonEmpty) {
      warn(s"Outer control $pipe has compute stages: ")
      trueComputation.foreach{case lhs@Def(rhs) => warn(s"$lhs = $rhs")}
    }

    cu.computeStages ++= localCompute.map{s => 
      val isReduce = (s match {
        case Def(RegRead(_)) => false
        case Def(RegWrite(_,_,_)) => false
        case s => reduceType(s).isDefined
      }) && !reduceSRAM
      DefStage(s, isReduce = isReduce)
    }

    dbgblk(s"remoteStages for $cu:") {
      remoteStages.foreach { s => 
        s match {
          case Def(d) =>
            dbgs(s"$s = $d")
          case s =>
            dbgs(s"$s")
        }
      }
    }
    dbgblk(s"prescheduled stages for $cu:") {
      cu.computeStages.foreach { s =>
        s match {
          case s@DefStage(op, _) => dbgs(s"$s reduceType=${reduceType(op)}")
          case s => dbgs(s"$s")
        }
      }
    }
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

    val n = cu.getOrElse(len){ allocateLocal(len, pipe) }
    val ctr = CUCounter(ConstReg("0i"), localScalar(n), ConstReg("1i"))
    val cc  = CChainInstance(quote(pipe)+"_cc", List(ctr))
    cu.cchains += cc
    val i = CounterReg(cc, 0)
    cu.regs += i


    val addr = allocateReadSRAM(pipe, addrs, cu, Nil)
    addr.readAddr = Some(i) //TODO: Change to remote remote Addr?

    val addrIn = fresh[Int32]
    cu.addReg(addrIn, SRAMReadReg(addr))

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

    val n = cu.getOrElse(len){ allocateLocal(len, pipe) }
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

    case BurstLoad(dram, fifo, ofs, ctr, i) =>
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
      prescheduleGather(lhs, dram, local, addrs, end)

    case SRAMNew(dimensions) => 

      duplicatesOf(lhs).zipWithIndex.foreach{ case (mem, i) => 
        mem match {
          case BankedMemory(dims, depth, isAccum) =>
            allocateMemoryCU(lhs)
            //val strides = s"""List(${dims.map(_.banks).mkString(",")})"""
            //val numWriters = writersOf(lhs).filter{ write => dispatchOf(write, lhs) contains i }.distinct.length
            //val numReaders = readersOf(lhs).filter{ read => dispatchOf(read, lhs) contains i }.distinct.length
          case DiagonalMemory(strides, banks, depth, isAccum) =>
            Console.println(s"NOT SUPPORTED, MAKE EXCEPTION FOR THIS!")
        }
      }

    // Something bad happened if these are still in the IR
    case _:OpForeach => throw new Exception(s"Disallowed compact op $lhs = $rhs")
    case _:OpReduce[_] => throw new Exception(s"Disallowed compact op $lhs = $rhs")
    case _:OpMemReduce[_,_] => throw new Exception(s"Disallowed compact op $lhs = $rhs")
    case _ => super.visit(lhs, rhs)
  }

  override def preprocess[S:Staged](b: Block[S]): Block[S] = {
    top = None
    mapping.clear()
    globals = Set.empty
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
                dbgs(s"Adding SRAM $copy ($reader, $mem) read in write stage of $cu")
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
