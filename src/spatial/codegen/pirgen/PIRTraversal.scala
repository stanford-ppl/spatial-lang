package spatial.codegen.pirgen

import spatial.SpatialConfig
import spatial.api._
import spatial.analysis.SpatialTraversal
import argon.traversal.{CompilerPass, BlockTraversal}

import scala.collection.mutable
import scala.reflect.runtime.universe._

import spatial.SpatialExp

trait PIRTraversal extends SpatialTraversal {
  val IR: SpatialExp with PIRCommonExp
  import IR.{println => _, _}

  val LANES = SpatialConfig.numLanes         // Number of SIMD lanes per CU
  val REDUCE_STAGES = (Math.log(LANES)/Math.log(2)).toInt  // Number of stages required to reduce across all lanes

  var listing = false
  var listingSaved = false
  var tablevel = 0 // Doesn't change tab level with traversal of block
  def dbgs(s:Any):Unit = dbg(s"${"  "*tablevel}${if (listing) "- " else ""}$s")
  def dbgblk[T](s:String)(block: =>T) = {
    dbgs(s + " {")
    tablevel += 1
    listingSaved = listing
    listing = false
    val res = block
    tablevel -=1
    dbgs(s"}")
    listing = listingSaved
    res
  }
  def dbgl[T](s:String)(block: => T) = {
    dbgs(s)
    tablevel += 1
    listing = true
    val res = block
    listing = false
    tablevel -=1
    res
  }
  def dbgpcu(pcu:PseudoComputeUnit) = {
    dbgblk(s"${qdef(pcu.pipe)} -> ${pcu.name}") {
      dbgl(s"regs:") {
        for ((s,r) <- pcu.regTable) { dbgs(s"$s -> $r") }
      }
      dbgl(s"cchains:") {
        pcu.cchains.foreach { cchain => dbgs(s"$cchain") }
      }
      dbgl(s"MEMs:") {
        for ((exp, mem) <- pcu.mems) {
          dbgs(s"""$mem (reader: ${mem.reader}, mode: ${mem.mode}) ${qdef(exp)}""")
        }
      }
      dbgl(s"Write stages:") {
        for ((k,v) <- pcu.writeStages) {
          dbgs(s"Memories: " + k.mkString(", "))
          for (stage <- v._2) dbgs(s"  $stage")
        }
      }
      dbgl(s"Read stages:") {
        pcu.readStages.foreach { case (k,v) =>
          dbgs(s"Memories:" + k.mkString(", "))
          for (stage <- v._2) dbgs(s"  $stage")
        }
      }
      dbgl(s"Compute stages:") { pcu.computeStages.foreach { stage => dbgs(s"$stage") } }
    }
  }

  def dbgcu(cu:ComputeUnit):Unit = dbgblk(s"Generated CU: $cu") {
    dbgblk(s"cchains: ") {
      cu.cchains.foreach{cchain => dbgs(s"$cchain") }
    }
    if (cu.srams.nonEmpty) {
      dbgblk(s"mems: ") {
        for (sram <- cu.srams) {
          dbgl(s"""$sram [${sram.mode}] (exp: ${sram.mem}, reader: ${sram.reader})""") {
            dbgs(s"""banking   = ${sram.banking.map(_.toString).getOrElse("N/A")}""")
            dbgs(s"""writePort    = ${sram.writePort.map(_.toString).getOrElse("N/A")}""")
            dbgs(s"""readPort    = ${sram.readPort.map(_.toString).getOrElse("N/A")}""")
            dbgs(s"""writeAddr = ${sram.writeAddr.map(_.toString).getOrElse("N/A")}""")
            dbgs(s"""readAddr  = ${sram.readAddr.map(_.toString).getOrElse("N/A")}""")
            dbgs(s"""start     = ${sram.writeStart.map(_.toString).getOrElse("N/A")}""")
            dbgs(s"""end       = ${sram.writeEnd.map(_.toString).getOrElse("N/A")}""")
            dbgs(s"""producer = ${sram.producer.map(_.toString).getOrElse("N/A")}""")
            dbgs(s"""consumer  = ${sram.consumer.map(_.toString).getOrElse("N/A")}""")
          }
        }
      }
    }
    for ((srams, stages) <- cu.writeStages) {
      dbgl(s"Generated write stages ($srams): ") {
        stages.foreach(stage => dbgs(s"  $stage"))
      }
    }
    for ((srams, stages) <- cu.readStages) {
      dbgl(s"Generated read stages ($srams): ") {
        stages.foreach(stage => dbgs(s"$stage"))
      }
    }
    dbgl("Generated compute stages: ") {
      cu.computeStages.foreach(stage => dbgs(s"$stage"))
    }
    dbgl(s"CU global inputs:") {
      globalInputs(cu).foreach{in => dbgs(s"$in") }
    }
  }

  def quote(x: Expr):String = s"${composed.get(x).fold("") {o => s"${quote(o)}_"} }$x"
  def qdef(lhs:Expr):String = {
    val rhs = lhs match {
      case Def(e:UnrolledForeach) => 
        s"UnrolledForeach(iters=(${e.iters.mkString(",")}), valids=(${e.valids.mkString(",")}))"
      case Def(e:UnrolledReduce[_,_]) => 
        s"UnrolledReduce(iters=(${e.iters.mkString(",")}), valids=(${e.valids.mkString(",")}))"
      //case Def(BurstLoad(dram, fifo, ofs, ctr, i)) =>
        //s"BurstLoad(dram=$dram, fifo=$fifo, ofs=$ofs, ctr=$ctr, i=$i)"
      //case Def(BurstStore(dram, fifo, ofs, ctr, i)) =>
        //s"BurstStore(dram=$dram, fifo=$fifo, ofs=$ofs, ctr=$ctr, i=$i)"
      case Def(d) if isControlNode(lhs) => s"${d.getClass.getSimpleName}(binds=${d.binds})"
      case Op(rhs) => s"$rhs"
      case Def(rhs) => s"$rhs"
      case lhs if (composed.contains(lhs)) => s"$lhs -> ${qdef(composed(lhs))}"
      case lhs => s"$lhs"
    }
    s"$lhs = $rhs"
  }

  // HACK: Skip parallel pipes in PIR gen
  def parentHack(x: Expr): Option[Expr] = parentOf(x) match {
    case Some(pipe@Def(_:ParallelPipe)) => parentHack(pipe)
    case parentOpt => parentOpt
  }

  // --- Allocating
  def decomposed:mutable.Map[Expr, Seq[(String, Expr)]] // Mapping Mem[Struct(Seq(fieldName, T))] -> Seq((fieldName, Mem[T]))
  def composed:mutable.Map[Expr, Expr] // Mapping Mem[T] -> Mem[Struct(Seq(fieldName, T))]

  def compose(dexp:Expr) = composed.get(dexp).getOrElse(dexp)

  def decomposeWithFields[T](exp:Expr, fields:Seq[T])(implicit ev:TypeTag[T]):Either[Expr, Seq[(String, Expr)]] = {
    if (fields.size<=1) {
      Left(exp)
    } else {
      Right(decomposed.getOrElseUpdate(exp, {
        fields.map { f => 
          val (field, dexp) = f match {
            case field if typeOf[T] =:= typeOf[String] => 
              (field.asInstanceOf[String], fresh[Int32]) 
            case (field, exp) if typeOf[T] =:= typeOf[(String, Expr)] => 
              (field.asInstanceOf[String], exp.asInstanceOf[Expr])
          }
          composed += dexp -> exp
          (field, dexp)
        }
      }))
    }
  }

  def decomposeWithFields[T](exp:Expr)(implicit ev:TypeTag[T]):Either[Expr, Seq[(String, Expr)]] = exp match {
    case Def(StreamInNew(bus)) => decomposeBus(bus, exp) 
    case Def(StreamOutNew(bus)) => decomposeBus(bus, exp)
    case Def(SimpleStruct(elems)) => decomposeWithFields(exp, elems)
    case Def(VectorApply(vec, idx)) => decomposeWithFields(exp, getFields(vec))
    case Def(ListVector(elems)) => decomposeWithFields(exp, elems.flatMap(ele => getFields(ele)))
    case Def(GetDRAMAddress(dram)) => Left(exp) //TODO: consider the case where dram is composed
    case mem if isMem(mem) => 
      val fields =  mem.tp.typeArguments(0) match {
        case s:StructType[_] => s.fields.map(_._1)
        case _ => Seq()
      }
      decomposeWithFields(mem, fields)
    case ParLocalReader(reads) => 
      val (mem, _, _) = reads.head
      decomposeWithFields(exp, getFields(mem))
    case ParLocalWriter(writes) =>
      val (mem, _, _, _) = writes.head
      decomposeWithFields(exp, getFields(mem))
    case _ => 
      decomposed.get(exp).map(fs => Right(fs)).getOrElse(Left(exp))
  }

  def decomposeBus(bus:Bus, mem:Expr) = bus match {
    case BurstCmdBus => decomposeWithFields(mem, Seq("offset", "size", "isLoad"))
    case BurstAckBus => decomposeWithFields(mem, Seq()) 
    case bus:BurstDataBus[_] => decomposeWithFields(mem, Seq()) 
    case bus:BurstFullDataBus[_] => decomposeWithFields(mem, Seq("data", "valid")) //?
    case GatherAddrBus => decomposeWithFields(mem, Seq())
    case bus:GatherDataBus[_] => decomposeWithFields(mem, Seq())
    case bus:ScatterCmdBus[_] => decomposeWithFields(mem, Seq("data", "valid")) //?
    case ScatterAckBus => decomposeWithFields(mem, Seq()) 
    case _ => throw new Exception(s"Don't know how to decompose bus ${bus}")
  }

  def decompose[T](exp:Expr, fields:Seq[T])(implicit ev:TypeTag[T]):Seq[Expr] = {
    decomposeWithFields(exp, fields) match {
      case Left(e) => Seq(e)
      case Right(seq) => seq.map(_._2)
    }
  }

  def decompose(exp:Expr):Seq[Expr] = {
    decomposeWithFields(exp) match {
      case Left(e) => Seq(e)
      case Right(seq) => seq.map(_._2)
    }
  }

  def getFields(exp:Expr):Seq[String] = {
    decomposeWithFields(exp) match {
      case Left(e) => Seq()
      case Right(seq) => seq.map(_._1)
    }
  }

  def getMatchedDecomposed(dele:Expr, ele:Expr):Expr = {
    val i = decompose(compose(dele)).indexOf(dele)
    decompose(ele)(i)
  }

  def isLocallyWritten(dmem:Expr, dreader:Expr, cu:Option[PseudoComputeUnit] = None) = {
    if (isArgIn(compose(dmem)) || isStreamIn(dmem) || isGetDRAMAddress(dmem)) {
      false
    } else {
      val writer = writerOf(compose(dmem))
      val pipe = parentOf(compose(dreader)).get
      writer.ctrlNode == pipe && cu.fold(true) { cu => cu.pipe == pipe }
    }
  }

  /*
   * @return readers of dmem that are remotely read 
   * */
  def getRemoteReaders(dmem:Expr, dwriter:Expr):List[Expr] = {
    val mem = compose(dmem)
    if (isStreamOut(mem)) { getReaders(mem) }
    else {
      readersOf(dmem).filter { reader => reader.ctrlNode!=parentOf(compose(dwriter)).get }.map(_.node)
    }
  }

  def getReaders(dmem:Expr):List[Expr] = {
    val mem = compose(dmem)
    if (isGetDRAMAddress(mem)) List(mem) // GetDRAMAddress is both the mem and the reader
    else if (isStreamOut(mem)) fringeOf(mem).map(f => List(f)).getOrElse(Nil) // Fringe is a reader of the stramOut
    else readersOf(mem).map{_.node}
  }

  def writerOf(mem:Expr):Access = {
    val writers = writersOf(mem)
    assert(writers.size==1, s"Plasticine only support single writer mem=${qdef(mem)} writers=${writers.mkString(",")}")
    writers.head
  }

  def globals:mutable.Set[GlobalComponent]

  def allocateDRAM(ctrl: Expr, dram: Expr, mode: OffchipMemoryMode): MemoryController = {
    val region = OffChip(quote(dram))
    val mc = MemoryController(quote(ctrl), region, mode, parentHack(ctrl).get)
    globals += mc
    globals += region
    mc
  }

  def allocateDRAM(dram:Expr): OffChip = { //FIXME
    val region = OffChip(quote(dram))
    if (!globals.contains(region)) {
      globals += region
      region
    } else {
      region
    }
  }

  def allocateLocal(x: Expr): LocalComponent = x match {
    case c if isConstant(c) => ConstReg(extractConstant(x))
    case _ => TempReg()
  }

  def const(x:Expr) = x match {
    case c if isConstant(c) => ConstReg(extractConstant(x))
    case _ => throw new Exception(s"${qdef(x)} ${x.tp} is not a constant")
  }

  def copyIterators(destCU: AbstractComputeUnit, srcCU: AbstractComputeUnit): Map[CUCChain,CUCChain] = {
    if (destCU != srcCU) {
      val cchainCopies = srcCU.cchains.toList.map{
        case cc@CChainCopy(name, inst, owner) => cc -> cc
        case cc@CChainInstance(name, ctrs)    => cc -> CChainCopy(name, cc, srcCU)
        case cc@UnitCChain(name)              => cc -> CChainCopy(name, cc, srcCU)
      }
      val cchainMapping = Map[CUCChain,CUCChain](cchainCopies:_*)

      destCU.cchains ++= cchainCopies.map(_._2)
      dbgs(s"copying iterators from ${srcCU.name} to ${destCU.name} destCU.cchains:[${destCU.cchains.mkString(",")}]")

      // FIXME: Shouldn't need to use getOrElse here
      srcCU.iterators.foreach{ case (iter,CounterReg(cchain,idx)) =>
        val reg = CounterReg(cchainMapping.getOrElse(cchain,cchain),idx)
        destCU.addReg(iter, reg)
        //dbgs(s"$iter -> $reg")
      }
      srcCU.valids.foreach{case (iter, ValidReg(cchain,idx)) =>
        val reg = ValidReg(cchainMapping.getOrElse(cchain,cchain), idx) 
        destCU.addReg(iter, reg)
        //dbgs(s"$iter -> $reg")
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
  def expsUsedInCalcExps(allStms:Seq[Stm])(results:Seq[Expr], effectful:Seq[Expr] = Nil):List[Expr] = {
    dbgblk(s"symsUsedInCalcExps"){
      def mysyms(lhs: Any):List[Expr] = lhs match {
        case Def(d) => mysyms(d) 
        case SRAMStore(sram,dims,is,ofs,data,en) => syms(sram) ++ syms(data) //++ syms(es)
        case ParSRAMStore(sram,addr,data,ens) => syms(sram) ++ syms(data) //++ syms(es)
        case FIFOEnq(fifo, data, en)          => syms(fifo) ++ syms(data)
        case ParFIFOEnq(fifo, data, ens) => syms(fifo) ++ syms(data)
        case FIFODeq(fifo, en, zero) => syms(fifo) ++ syms(zero)
        case ParFIFODeq(fifo, ens, zero) => syms(fifo) ++ syms(zero)
        case StreamEnq(stream, data, en) => syms(stream) ++ syms(data)
        case ParStreamEnq(stream, data, ens) => syms(stream) ++ syms(data)
        case StreamDeq(stream, ens, zero) => syms(stream) ++ syms(zero)
        case ParStreamDeq(stream, ens, zero) => syms(stream) ++ syms(zero)
        case d:Def => d.allInputs //syms(d)
        case _ => syms(lhs)
      }
      val scopeIndex = makeScopeIndex(allStms)
      def deps(x: Expr):List[Stm] = orderedInputs(mysyms(x), scopeIndex)

      dbgs(s"results=${results.mkString(",")}")
      dbgs(s"effectful=[${effectful.mkString(",")}]")
      var stms = schedule((results++effectful).flatMap(deps) ++ orderedInputs(effectful, scopeIndex)){ next => 
        dbgs(s"$next inputs=[${deps(next).mkString(",")}] isStage=${isStage(next)}")
        deps(next)
      }
      stms = stms.filter{ case TP(s,d) => isStage(s) }
      stms.map{case TP(s,d) => s}
    }
  }

  def symsUsedInCalcExps(allStms:Seq[Stm])(results:Seq[Expr], effectful:Seq[Expr] = Nil):List[Sym[_]] = {
    expsUsedInCalcExps(allStms)(results, effectful).collect{ case s:Sym[_] => s }
  }

  def filterStmUseExp(allStms:Seq[Stm])(exp:Expr):Seq[Stm] = {
    allStms.filter { case TP(s,d) => d.allInputs.contains(exp) }
  }

  def getStms(pipe:Expr) = pipe match {
    case Def(Hwblock(func,_)) => blockContents(func)
    case Def(UnitPipe(en, func)) => blockContents(func)
    case Def(UnrolledForeach(en, cchain, func, iters, valids)) => blockContents(func)
    case Def(UnrolledReduce(en, cchain, accum, func, reduce, iters, valids, rV)) => blockContents(func)
    case _ => throw new Exception(s"Don't know how to get stms pipe=${qdef(pipe)}")
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

      case stage@ReduceStage(op,init,in,acc) if !remove.contains(stage) =>
        ctx.addStage(stage)

      case _ => // This stage is being removed! Ignore it!
    }
  }

  def swapBus(cus: Iterable[CU], orig: GlobalBus, swap: GlobalBus) = cus.foreach{cu =>
    cu.allStages.foreach{stage => swapBus_stage(stage) }
    cu.srams.foreach{sram => swapBus_sram(sram) }
    cu.cchains.foreach{cc => swapBus_cchain(cc) }

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

    def swapBus_writeAddr(addr: WriteAddr): WriteAddr = addr match {
      case reg: LocalComponent => swapBus_reg(reg).asInstanceOf[WriteAddr]
      case _ => addr
    }
    def swapBus_readAddr(addr: ReadAddr): ReadAddr = addr match {
      case reg: LocalComponent => swapBus_reg(reg).asInstanceOf[ReadAddr]
      case _ => addr
    }
    def swapBus_localScalar(sc: LocalScalar): LocalScalar = sc match {
      case reg: LocalComponent => swapBus_reg(reg).asInstanceOf[LocalScalar]
      case _ => sc
    }

    def swapBus_sram(sram: CUMemory): Unit = {
      sram.writePort = sram.writePort.map{case `orig` => swap; case vec => vec}
      sram.readPort = sram.readPort.map{case `orig` => swap; case vec => vec}
      sram.readAddr = sram.readAddr.map{reg => swapBus_readAddr(reg)}
      sram.writeAddr = sram.writeAddr.map{reg => swapBus_writeAddr(reg)}
      sram.writeStart = sram.writeStart.map{reg => swapBus_localScalar(reg)}
      sram.writeEnd = sram.writeEnd.map{reg => swapBus_localScalar(reg)}
    }
    def swapBus_cchain(cchain: CUCChain): Unit = cchain match {
      case cc: CChainInstance => cc.counters.foreach{ctr => swapBus_ctr(ctr)}
      case _ => // No action
    }
    def swapBus_ctr(ctr: CUCounter): Unit = {
      ctr.start = swapBus_localScalar(ctr.start)
      ctr.end = swapBus_localScalar(ctr.end)
      ctr.stride = swapBus_localScalar(ctr.stride)
    }
  }


  def swapCUs(cus: Iterable[CU], mapping: Map[ACU, ACU]): Unit = cus.foreach {cu =>
    cu.cchains.foreach{cchain => swapCU_cchain(cchain) }
    cu.parent = cu.parent.map{parent => mapping.getOrElse(parent,parent) }
    cu.deps = cu.deps.map{dep => mapping.getOrElse(dep, dep) }
    cu.srams.foreach{sram => swapCU_sram(sram) }
    cu.allStages.foreach{stage => stage.inputMems.foreach(swapCU_reg) }

    def swapCU_cchain(cchain: CUCChain): Unit = cchain match {
      case cc: CChainCopy => cc.owner = mapping.getOrElse(cc.owner,cc.owner)
      case _ => // No action
    }
    def swapCU_reg(reg: LocalComponent): Unit = reg match {
      case CounterReg(cc,i) => swapCU_cchain(cc)
      case ValidReg(cc,i) => swapCU_cchain(cc)
      case _ =>
    }

    def swapCU_sram(sram: CUMemory) {
      sram.readAddr.foreach{case reg:LocalComponent => swapCU_reg(reg); case _ => }
      sram.writeAddr.foreach{case reg:LocalComponent => swapCU_reg(reg); case _ => }
    }
  }

  // --- Context for creating/modifying CUs
  abstract class CUContext(val cu: ComputeUnit) {
    private val refs = mutable.HashMap[Expr,LocalRef]()
    private var readAccums: Set[AccumReg] = Set.empty

    def pipe: Expr
    def stages: mutable.ArrayBuffer[Stage]
    def addStage(stage: Stage): Unit
    def isWriteContext: Boolean
    def init(): Unit

    def isUnit = cu.isUnit

    def stageNum: Int = stages.count{case stage:MapStage => true; case _ => false} + 1
    def controlStageNum: Int = controlStages.length
    def prevStage: Option[Stage] = stages.lastOption
    def mapStages: Iterator[MapStage] = stages.iterator.collect{case stage:MapStage => stage}

    def controlStages: mutable.ArrayBuffer[Stage] = cu.controlStages
    def addControlStage(stage: Stage): Unit = cu.controlStages += stage

    def addReg(x: Expr, reg: LocalComponent) {
      //debug(s"  $x -> $reg")
      cu.addReg(x, reg)
    }
    def addRef(x: Expr, ref: LocalRef) { refs += x -> ref }
    def getReg(x: Expr): Option[LocalComponent] = cu.get(x)
    def reg(x: Expr): LocalComponent = {
      cu.get(x).getOrElse(throw new Exception(s"No register defined for $x in $cu"))
    }

    // Add a stage which bypasses x to y
    def bypass(x: LocalComponent, y: LocalComponent) {
      val stage = MapStage(PIRBypass, List(refIn(x)), List(refOut(y)))
      addStage(stage)
    }

    def ref(reg: LocalComponent, out: Boolean, stage: Int = stageNum): LocalRef = reg match {
      // If the previous stage computed the read address for this load, use the registered output
      // of the memory directly. Otherwise, use the previous stage
      case MemLoadReg(sram) => //TODO: rework out the logic here
        /*debug(s"Referencing SRAM $sram in stage $stage")
        debug(s"  Previous stage: $prevStage")
        debug(s"  SRAM read addr: ${sram.readAddr}")*/
        if (prevStage.isEmpty || sram.mode == VectorFIFOMode || sram.mode == ScalarFIFOMode )
          LocalRef(-1, reg)
        else {
          if (sram.mode != VectorFIFOMode && sram.mode!= ScalarFIFOMode) {
            LocalRef(stage-1,reg)
          }
          else
            throw new Exception(s"No address defined for SRAM $sram")
        }

      case reg: CounterReg if isWriteContext && prevStage.isEmpty =>
        //debug(s"Referencing counter $reg in first write stage")
        LocalRef(-1, reg)

      case reg: AccumReg if isUnreadAccum(reg) =>
        //debug(s"First reference to accumulator $reg in stage $stage")
        readAccums += reg
        LocalRef(stage, reg)
      case _ if out =>
        //debug(s"Referencing output register $reg in stage $stage")
        LocalRef(stage, reg)
      case _ =>
        //debug(s"Referencing input register $reg in stage $stage")
        LocalRef(stage-1, reg)
    }
    def refIn(reg: LocalComponent, stage: Int = stageNum) = ref(reg, out = false, stage)
    def refOut(reg: LocalComponent, stage: Int = stageNum) = ref(reg, out = true, stage)

    def addOutputFor(e: Expr)(prev: LocalComponent, out: LocalComponent): Unit = addOutput(prev, out, Some(e))
    def addOutput(prev: LocalComponent, out: LocalComponent): Unit = addOutput(prev, out, None)
    def addOutput(prev: LocalComponent, out: LocalComponent, e: Option[Expr]): Unit = {
      mapStages.find{stage => stage.outputMems.contains(prev) } match {
        case Some(stage) =>
          stage.outs ::= refOut(out, mapStages.indexOf(stage) + 1)
        case None =>
          bypass(prev, out)
      }
      if (e.isDefined) addReg(e.get, out)
      else cu.regs += out // No mapping, only list
    }

    // Get memory in this CU associated with the given reader
    def mem(mem: Expr, reader: Expr): CUMemory = {
      memOption(mem,reader).getOrElse(throw new Exception(s"Cannot find sram ($mem,$reader) in cu $cu"))
    }

    def memOption(mem: Expr, reader: Expr): Option[CUMemory] = {
      cu.srams.find{sram => sram.mem == mem && sram.reader == reader}
    }

    // A CU can have multiple SRAMs for a given mem symbol, one for each local read
    def memories(mem: Expr) = cu.srams.filter(_.mem == mem)


    // HACK: Keep track of first read of accum reg (otherwise can use the wrong stage)
    private def isUnreadAccum(reg: LocalComponent) = reg match {
      case reg: AccumReg => !readAccums.contains(reg)
      case _ => false
    }
  }


  case class ComputeContext(override val cu: ComputeUnit) extends CUContext(cu) {
    def stages = cu.computeStages
    def addStage(stage: Stage) { cu.computeStages += stage }
    def isWriteContext = false
    def pipe = cu.pipe
    def init() = {}
  }
  case class WriteContext(override val cu: ComputeUnit, pipe: Expr, srams: List[CUMemory]) extends CUContext(cu) {
    def init() { cu.writeStages += srams -> mutable.ArrayBuffer[Stage]() }
    def stages = cu.writeStages(srams)
    def addStage(stage: Stage) { cu.writeStages(srams) += stage }
    def isWriteContext = true
  }
  case class ReadContext(override val cu: ComputeUnit, pipe: Expr, srams: List[CUMemory]) extends CUContext(cu) {
    def init() { cu.readStages += srams -> mutable.ArrayBuffer[Stage]() }
    def stages = cu.readStages(srams)
    def addStage(stage: Stage) { cu.readStages(srams) += stage }
    def isWriteContext = false 
  }

  // Given result register type A, reroute to type B as necessary
  def propagateReg(exp: Expr, a: LocalComponent, b: LocalComponent, ctx: CUContext):LocalComponent = (a,b) match {
    case (a:ScalarOut, b:ScalarOut) => a
    case (a:VectorOut, b:VectorOut) => a
    case (a:FeedbackDataReg, b:FeedbackDataReg) => a
    case (_:ReduceReg | _:AccumReg, _:ReduceReg | _:AccumReg) => a

    // Propagating from read addr wire to another read addr wire is ok (but should usually never happen)
    case (a:ReadAddrWire, b:ReadAddrWire) => ctx.addOutputFor(exp)(a,b); b
    case (a,b) if !isReadable(a) => throw new Exception(s"Cannot propagate for $exp from output-only $a")
    case (a,b) if !isWritable(b) => throw new Exception(s"Cannot propagate for $exp to input-only $b")

    // Prefer reading original over a new temporary register
    case (a, b:TempReg) => a

    // Special cases: don't propagate to write/read wires from counters or constants
    case (_:CounterReg | _:ConstReg, _:WriteAddrWire | _:ReadAddrWire) => a

    // General case for outputs: Don't add mapping for exp to output
    case (a,b) if !isReadable(b) => ctx.addOutput(a,b); b

    // General case for all others: add output + mapping
    case (a,b) => ctx.addOutputFor(exp)(a,b); b
  }

}
