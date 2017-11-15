package spatial.codegen.pirgen

import argon.core._
import argon.nodes._
import spatial.analysis.SpatialTraversal
import spatial.aliases._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._

import scala.collection.mutable
import scala.collection.mutable.WrappedArray
import scala.reflect.runtime.universe.{Block => _, Type => _, _}

trait PIRTraversal extends SpatialTraversal with Partitions with PIRLogger {

  implicit val self:PIRTraversal = this

  def getOrElseUpdate[K, V](map:mutable.Map[K, V], key:K, value: =>V):V = {
    if (!map.contains(key)) {
      map += key -> value
    }
    map(key)
  }

  def ancesstorBelow(top:Expr, cur:Expr):Option[Expr] = {
    parentOf(cur).flatMap { p => if (p==top) Some(cur) else ancesstorBelow(top, p) }
  }

  def lcaOf(a:Expr, b:Expr) = leastCommonAncestor(a, b, { (x:Expr) => parentOf(x)})

  def carriedDep(mem:Expr, reader:Expr, writer:Expr):Boolean = {
    if (depsOf(mem).nonEmpty) return false
    if (isAccum(mem)) return true
    parentOf(mem).getOrElse(return false)
    val memParent = parentOf(mem).get
    val readerParent = parentOf(reader).get
    val writerParent = parentOf(writer).get
    val lca = lcaOf(memParent, readerParent).flatMap { a => lcaOf(a, writerParent) }.getOrElse(return false)
    dbgs(s"mem=$mem, memParent=$memParent, reader=$reader writer=$writer, lca=$lca")
    val readAnc = ancesstorBelow(lca, readerParent).get
    val writeAnc = ancesstorBelow(lca, writerParent).get
    assert(readAnc==writeAnc)
    !isUnitPipe(readAnc)
  }

  def isLocallyWritten(dmem:Expr, dreader:Expr, cu:CU) = {
    val reader = compose(dreader)
    val mem = compose(dmem)
    val isLocal = if (isArgIn(mem) || isStreamIn(mem) || isGetDRAMAddress(mem) || isFringe(reader)) {
      false
    } else {
      val pipe = parentOf(reader).get
      val writers = writersOf(mem)
      dbgs(s"depsOf($reader)=${depsOf(reader)} isUnitPipe($pipe)=${isUnitPipe(pipe)}")
      writers.exists { writer => 
        writer.ctrlNode == pipe && 
        mappingOf(cu) == pipe && 
        (depsOf(reader).contains(writer.node) || carriedDep(mem, reader, writer.node))
      }
    }
    dbgs(s"isLocallyWritten=$isLocal ${qdef(mem)} ${qdef(reader)}")
    isLocal
  }

  def lookupField(exp:Expr, fieldName:String):Option[Expr] = {
    decomposeWithFields(exp) match {
      case Left(exp) => None
      case Right(fs) => 
        val matches = fs.filter(_._1==fieldName)
        assert(matches.size<=1, s"$exp has struct type with duplicated field name: [${fs.mkString(",")}]")
        if (matches.nonEmpty)
          Some(matches.head._2)
        else
          None
    }
  }

  /*
   * @return readers of dmem that are remotely read 
   * */
  def getRemoteReaders(dmem:Expr, dwriter:Expr):List[Expr] = {
    val mem = compose(dmem)
    val writer = compose(dwriter)
    if (isArgIn(mem) || isGetDRAMAddress(mem) || isStreamOut(mem)) {
      getReaders(mem)
    } else {
      readersOf(mem).filter { reader => 
        reader.ctrlNode!=parentOf(writer).get }.map(_.node)
    }
  }

  /*
   * @return read access of or equivalent the mem
   * */
  def getReaders(dmem:Expr):List[Expr] = {
    val mem = compose(dmem)
    if (isGetDRAMAddress(mem)) List(mem) // GetDRAMAddress is both the mem and the reader
    else if (isStreamOut(mem)) fringeOf(mem).map(f => List(f)).getOrElse(Nil) // Fringe is a reader of the stramOut
    else readersOf(mem).map{_.node}
  }

  /*
   * @return write access or equivalent of the mem
   * */
  def getWriters(dmem:Expr): List[Expr] = {
    val mem = compose(dmem)
    val writers = writersOf(mem)
    writers.map { _.node }
  }

  /*
   * Returns a single id for reader
   * */
  def getDispatches(dmem:Expr, daccess:Expr) = {
    val mem = compose(dmem)
    val access = compose(daccess)
    val instIds = if (isStreamOut(mem) || isArgOut(mem) || isGetDRAMAddress(mem) || isArgIn(mem) || isStreamIn(mem)) {
      List(0)
    } else {
      dispatchOf(access, mem).toList
    }
    if (isReader(access)) {
      assert(instIds.size==1, 
        s"number of dispatch = ${instIds.size} for reader $access but expected to be 1")
    }
    instIds
  }

  /*
   * Returns a single duplicate for reader
   * */
  def getDuplicates(dmem:Expr, access:Expr):List[Memory] = {
    val instIds = getDispatches(dmem, access)
    val insts = duplicatesOf(compose(dmem))
    instIds.map{ id => insts(id) }
  }

  def getDuplicate(dmem:Expr, id:Int):Memory = {
    val insts = duplicatesOf(compose(dmem))
    insts(id)
  }

  def getWritersForInst(dmem:Expr, inst:Memory):List[Expr] = {
    val mem = compose(dmem)
    val insts = duplicatesOf(mem)
    val instId = insts.indexOf(inst)
    writersOf(mem).filter { writer => getDispatches(mem, writer.node).contains(instId) }.map{_.node}
  }

  def allocateRetimingFIFO(reg:LocalComponent, bus:GlobalBus, cu:CU):CUMemory = {
    //HACK: don't know what the original sym is at splitting. 
    //Probably LocalComponent should keep a copy of sym at allocation time?
    val memSym = null //TODO: fix this??
    val mem = CUMemory(s"$reg", memSym, cu)
    bus match {
      case bus:ControlBus =>
        mem.tpe = ControlFIFOType
      case bus:ScalarBus =>
        mem.tpe = ScalarFIFOType
      case bus:VectorBus =>
        mem.tpe = VectorFIFOType
    }
    mem.size = 1
    mem.writePort += ((bus, None, None))
    cu.memMap += reg -> mem
    dbgs(s"allocateRetimingFIFO($reg, $bus, $cu) = $mem")
    mem
  }

  def allocateDRAM(dram:Expr): OffChip = { //FIXME
    val region = OffChip(dram.name.getOrElse(quote(dram)))
    if (!globals.contains(region)) {
      globals += region
      region
    } else {
      region
    }
  }

  def allocateLocal(cu:CU, dx: Expr): LocalComponent = cu.getOrElseUpdate(dx) {
    compose(dx) match {
      case c if isConstant(c) => extractConstant(dx)
      case Def(FIFONumel(fifo)) => 
        val dfifo = decomposeWithFields(fifo) match {
          case Left(exp) => exp
          case Right(seq) => seq.filter{ case (field, exp) => field == getField(dx).get }.head
        }
        MemNumel(cu.memMap(dfifo))
      case Def(RegNew(init)) =>
        val dinit = decomposeWithFields(init)
        val initExp = dinit match {
          case Left(dinit) => dinit
          case Right(seq) =>
            val field = getField(dx).get
            seq.filter{ case (f, e) => f == field }.head._2
        }
        val reg = TempReg(dx, getConstant(initExp))
        dbgs(s"Allocate Reg with Init: $init -> $initExp $reg init=${reg.init}")
        reg
      case _ => TempReg(dx, None)
    }
  }

  def copyIterators(destCU: CU, srcCU: CU, iterIdx:Option[(Int, Int)]=None): Map[CUCChain,CUCChain] = {
    if (destCU != srcCU) {
      val cchainCopies = srcCU.cchains.toList.map {
        case cc@CChainCopy(name, inst, owner)   => cc -> CChainCopy(name, inst, owner)
        case cc@CChainInstance(name, ctrs) => 
          val cp = CChainCopy(name, cc, srcCU)
          iterIdx.foreach { ii => cp.iterIndices += ii }
          cc -> cp
        case cc@UnitCChain(name)                => cc -> CChainCopy(name, cc, srcCU)
      }
      val cchainMapping = Map[CUCChain,CUCChain](cchainCopies:_*)

      destCU.cchains ++= cchainCopies.map(_._2)
      dbgs(s"copying iterators from ${srcCU.name} to ${destCU.name} destCU.cchains:[${destCU.cchains.mkString(",")}]")

      srcCU.iterators.foreach{ case (iter,CounterReg(cchain,cIdx,iIdx)) =>
        val reg = CounterReg(cchainMapping(cchain),cIdx,iIdx)
        destCU.addReg(iter, reg)
        //dbgs(s"$iter -> $reg")
      }
      srcCU.valids.foreach{case (valid, ValidReg(cchain,cIdx,vIdx)) =>
        val reg = ValidReg(cchainMapping(cchain), cIdx, vIdx) 
        destCU.addReg(valid, reg)
        //dbgs(s"$valid -> $reg")
      }
      cchainMapping
    }
    else Map.empty[CUCChain,CUCChain]
  }

  /*
   * @param allStms all statements in which to search for exps 
   * @param results produced expression to be considered
   * @param effectful effectful exps to be considered for searching input exps 
   * Get symbols used to calculate results and effectful excluding symbols that are 
   * - used for address calculation and control calculation for FIFOs. 
   * - Doesn't correspond to a PIR stage
   * - Read access of local memories if the read access is not locally used 
   *   (used by stms that produces results or effectful) 
   * */
  def mysyms(lhs: Any): Seq[Expr] = lhs match {
    case Def(d) => mysyms(d) 
    case SRAMStore(sram,dims,is,ofs,data,en)  => syms(sram) ++ syms(data) //++ syms(es)
    case ParSRAMStore(sram,addr,data,ens)     => syms(sram) ++ syms(data) //++ syms(es)
    case FIFOEnq(fifo, data, en)              => syms(fifo) ++ syms(data)
    case ParFIFOEnq(fifo, data, ens)          => syms(fifo) ++ syms(data)
    case FIFODeq(fifo, en)                    => syms(fifo)
    case ParFIFODeq(fifo, ens)                => syms(fifo)
    case StreamWrite(stream, data, en)        => syms(stream) ++ syms(data)
    case ParStreamWrite(stream, data, ens)    => syms(stream) ++ syms(data)
    case StreamRead(stream, ens)              => syms(stream)
    case ParStreamRead(stream, ens)           => syms(stream)
    case Switch(body, selects, cases)         => syms(cases) ++ syms(selects)
    case SwitchCase(body)                     => syms(body)
    case d: Def => d.allInputs //syms(d)
    case _ => syms(lhs)
  }

  def expsUsedInCalcExps(allStms: Seq[Stm])(results: Seq[Expr], effectful: Seq[Expr] = Nil): Seq[Expr] = {
    dbgblk(s"expsUsedInCalcExps"){
      dbgs(s"results=${results.mkString(",")}")
      dbgs(s"effectful=[${effectful.mkString(",")}]")
      val scopeIndex = makeScopeIndex(allStms)
      def deps(x: Expr): Seq[Stm] = orderedInputs(mysyms(x), scopeIndex)

      var stms = schedule((results++effectful).flatMap(deps) ++ orderedInputs(effectful, scopeIndex)){ next => 
        dbgs(s"$next inputs=[${deps(next).mkString(",")}] isStage=${isStage(next)}")
        deps(next)
      }
      stms = stms.filter{ case TP(s,d) => isStage(s) }
      stms.map{case TP(s,d) => s}
    }
  }

  def symsUsedInCalcExps(allStms: Seq[Stm])(results: Seq[Expr], effectful: Seq[Expr] = Nil): Seq[Sym[_]] = {
    expsUsedInCalcExps(allStms)(results, effectful).collect{ case s:Sym[_] => s }
  }

  def filterStmUseExp(allStms: Seq[Stm])(exp: Expr): Seq[Stm] = {
    allStms.filter { case TP(s,d) => d.allInputs.contains(exp) }
  }

  def getStms(pipe: Expr):Seq[Stm] = pipe match {
    case Def(Hwblock(func,_)) if isInnerControl(pipe) => blockNestedContents(func)
    case Def(Hwblock(func,_)) => blockContents(func)
    case Def(UnitPipe(en, func)) if isInnerControl(pipe) => blockNestedContents(func)
    case Def(UnitPipe(en, func)) => blockContents(func)
    case Def(UnrolledForeach(en, cchain, func, iters, valids)) if isInnerControl(pipe) => blockNestedContents(func)
    case Def(UnrolledForeach(en, cchain, func, iters, valids)) => blockContents(func)
    case Def(UnrolledReduce(en, cchain, accum, func, iters, valids)) if isInnerControl(pipe) => blockNestedContents(func)
    case Def(UnrolledReduce(en, cchain, accum, func, iters, valids)) => blockContents(func)
    case Def(op@Switch(body, selects, cases)) => cases.flatMap(getStms)
    case Def(op@SwitchCase(body)) => blockNestedContents(body)
    case _ => throw new Exception(s"Don't know how to get stms pipe=${qdef(pipe)}")
  }

  def itersOf(pipe:Expr):Option[Seq[Seq[Expr]]] = pipe match {
    case Def(UnrolledForeach(en, cchain, func, iters, valids)) => Some(iters)
    case Def(UnrolledReduce(en, cchain, accum, func, iters, valids)) => Some(iters)
    case _ => None
  }

  // --- Transformation functions
  def removeComputeStages(cu: CU, remove: Set[Stage]) {
    val ctx = ComputeContext(cu)
    val stages = mutable.ArrayBuffer[Stage]()
    stages ++= ctx.stages
    cu.computeStages.clear()

    stages.foreach{
      case stage@MapStage(op,ins,outs) if !remove.contains(stage) =>
        stage.ins = ins.map{case LocalRef(i,reg) => ctx.refIn(reg) }
        stage.outs = outs.map{case LocalRef(i,reg) => ctx.refOut(reg) }
        ctx.addStage(stage)

      case stage@ReduceStage(op,init,in,acc,accParent) if !remove.contains(stage) =>
        ctx.addStage(stage)

      case _ => // This stage is being removed! Ignore it!
    }
  }

  def swapBus(cus: Iterable[CU], orig: GlobalBus, swap: GlobalBus) = dbgblk(s"swapBus($orig -> $swap)") {
    cus.foreach{cu =>
    cu.allStages.foreach{stage => swapBus_stage(stage) }
    cu.mems.foreach{mem => swapBus_mem(mem) }
    cu.cchains.foreach{cc => swapBus_cchain(cc) }
    //swap_producer(orig, swap)

    def swapBus_stage(stage: Stage): Unit = stage match {
      case stage@MapStage(op, ins, outs) =>
        stage.ins = ins.map{ref => swapBus_ref(ref) }
        stage.outs = outs.map{ref => swapBus_ref(ref) }
      case stage:ReduceStage => // No action
    }
    def swapBus_ref(ref: LocalRef): LocalRef = ref match {
      case LocalRef(i,reg) => LocalRef(i, swapBus_reg(reg))
    }
    def swapBus_reg(reg: LocalComponent): LocalComponent = (reg,swap) match {
      case (ScalarIn(`orig`),  swap: ScalarBus) => ScalarIn(swap)
      case (ScalarOut(`orig`), swap: ScalarBus) => ScalarOut(swap)
      case (VectorIn(`orig`),  swap: VectorBus) => VectorIn(swap)
      case (VectorOut(`orig`), swap: VectorBus) => VectorOut(swap)

      case (ScalarIn(x), _)  if x != orig => reg
      case (ScalarOut(x), _) if x != orig => reg
      case (VectorIn(x), _)  if x != orig => reg
      case (VectorOut(x), _) if x != orig => reg

      case (_:LocalPort[_], _) => throw new Exception(s"$swap is not a valid replacement for $orig")
      case _ => reg
    }

    def swapBus_mem(mem: CUMemory): Unit = {
      mem.writePort.transform {
        case (`orig`, addr, top) => 
          dbgs(s"swapBus_mem: $cu.$mem.writePort $orig -> $swap")
          val newTop = top.map { top =>
            val origWriterCU = getWriterCU(orig)
            if (origWriterCU==top) {
              val newWriterCU = getWriterCU(swap)
              newWriterCU
            } else top
          }
          (swap, addr, newTop)
        case port => port 
      }
      mem.readPort.transform {
        case (`orig`, addr, top) => 
          dbgs(s"swapBus_mem: $cu.$mem.readPort $orig -> $swap")
          (swap, addr, top)
        case port => port 
      }
    }

    //def swap_producer(orig:GlobalBus, swap:GlobalBus) = {
      //producerOf.transform { case (mem, wps) =>
        //wps.map { 
          //case (`orig`, producer) =>
            //val origWriterCU = getWriterCU(orig)
            //val newWriterCU = getWriterCU(swap)
            //if (origWriterCU == producer) (swap, newWriterCU)
            //else (swap, producer)
          //case (writer, producer) => (writer, producer)
        //}
      //}
    //}
    //def swap_reader(orig:GlobalBus, swap:GlobalBus) = {
      //val origReaders = getWriterCU(orig)
      //val newReaders = getWriterCU(swap)
      //consumerOf.transform { case (mem, rcs) =>
        //val newConsumers = 
      //}
    //}
    //def getReaderCUs(bus:GlobalBus):CU = {
      //val readers = cus.filter { cu => globalInputs(cu).contains(bus) }
      //readers
    //}

    def swapBus_cchain(cchain: CUCChain): Unit = cchain match {
      case cc: CChainInstance => cc.counters.foreach{ctr => swapBus_ctr(ctr)}
      case _ => // No action
    }
    def swapBus_ctr(ctr: CUCounter): Unit = {
      ctr.start = swapBus_reg(ctr.start)
      ctr.end = swapBus_reg(ctr.end)
      ctr.stride = swapBus_reg(ctr.stride)
    }

  }
  }

  // Given result register type A, reroute to type B as necessary
  def propagateReg(exp: Expr, a: LocalComponent, b: LocalComponent, ctx: CUContext):LocalComponent = (a,b) match {
    case (a:ScalarOut, b:ScalarOut) => a
    case (a:VectorOut, b:VectorOut) => a
    case (_:ReduceReg | _:AccumReg, _:ReduceReg | _:AccumReg) => a

    // Propagating from read addr wire to another read addr wire is ok (but should usually never happen)
    case (a:ReadAddrWire, b:ReadAddrWire) => ctx.addOutputFor(exp)(a,b); b
    case (a,b) if !isReadable(a) => throw new Exception(s"Cannot propagate for $exp from output-only $a")
    case (a,b) if !isWritable(b) => throw new Exception(s"Cannot propagate for $exp to input-only $b")

    // Prefer reading original over a new temporary register
    case (a, b:TempReg) => a

    // Special cases: don't propagate to write/read wires from counters or constants
    case (_:CounterReg | _:ConstReg[_], _:WriteAddrWire | _:ReadAddrWire) => a

    // General case for outputs: Don't add mapping for exp to output
    case (a,b) if !isReadable(b) => ctx.addOutput(a,b); b

    // General case for all others: add output + mapping
    case (a,b) => ctx.addOutputFor(exp)(a,b); b
  }

  def runAll[S:Type](b: Block[S]): Block[S] = {
    init()
    var block = b
    block = preprocess(block)
    block = run(block)
    block = postprocess(block)
    block
  }

}
