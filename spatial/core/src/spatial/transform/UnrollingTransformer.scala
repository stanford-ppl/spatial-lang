package spatial.transform

import argon.core._
import argon.nodes._
import argon.transform.ForwardTransformer
import org.virtualized.SourceContext
import spatial.aliases._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._
import spatial.lang.Math

case class UnrollingTransformer(var IR: State) extends UnrollingBase { self =>
  override val name = "Unrolling Transformer"
  var inHwScope: Boolean = false

  def strMeta(e: Exp[_]): Unit = metadata.get(e).foreach{m => logs(c" - ${m._1}: ${m._2}") }


  /**
    * Clone functions - used to add extra rules (primarily for metadata) during unrolling
    * Applied directly after mirroring
    */
  var cloneFuncs: List[Exp[_] => Unit] = Nil
  def duringClone[T](func: Exp[_] => Unit)(blk: => T)(implicit ctx: SrcCtx): T = {
    val prevCloneFuncs = cloneFuncs
    cloneFuncs = cloneFuncs :+ func   // Innermost is executed last

    val result = blk
    cloneFuncs = prevCloneFuncs

    result
  }
  def inReduction[T](isInner: Boolean)(blk: => T): T = {
    duringClone{e => if (spatialConfig.enablePIR && !isInner) reduceType(e) = None }{ blk }
  }
  def inCycle[T](reduceTp: Option[ReduceFunction])(blk: => T): T = {
    duringClone{e => if (spatialConfig.enablePIR) reduceType(e) = reduceTp }{ blk }
  }





  /**
    * "Pachinko" rules:
    *
    * Given a memory access which depends on indices i1 ... iN,
    * and some parallelization factors P1 ... PN associated with each index,
    *
    *
    * When a new memory access is unrolled:
    *   1. Check other unrolled accesses which have the same original symbol
    *   2.a. Look up all loop iterators used to compute the indices for this access
    *   2.b. Look up the unroll number for each of these iterators (-1 for random accesses)
    *   3. The duplicate of the access is the number of accesses which have already been unrolled with the same numbers
    *   4. Update list of accesses to include current's numbers
    */
  var pachinko = Map[(Exp[_],Exp[_],Int), Seq[Seq[Int]]]()

  def registerAccess(original: Exp[_], unrolled: Exp[_] /*, pars: Option[Seq[Int]]*/): Unit = {
    val reads = unrolled match { case LocalReader(rds) => rds.map(_._1); case _ => Nil }
    val resets = unrolled match { case LocalResetter(rsts) => rsts.map(_._1); case _ => Nil }
    val writes = unrolled match { case LocalWriter(wrts) => wrts.map(_._1); case _ => Nil }
    //val accesses = reads ++ writes

    val unrollInts: Seq[Int] = accessPatternOf.get(original).map{patterns =>
      patterns.flatMap{
        case GeneralAffine(prods,_) if spatialConfig.useAffine => prods.map(_.i).map{i => unrollNum.getOrElse(i, -1) }
        case p => Seq(p.index.flatMap{i => unrollNum.get(i)}.getOrElse(-1))
      }
    }.getOrElse(Seq(-1))

    // Total number of address channels needed
    val channels = accessWidth(unrolled)

    // For each memory this access reads, set the new dispatch value
    reads.foreach{mem =>
      dbgs(u"Registering read of $mem: " + c"$original -> $unrolled")
      dbgs(c"  Channels: $channels")
      dbgs(c"  ${str(original)}")
      dbgs(c"  ${str(unrolled)}")
      dbgs(c"  Unroll numbers: $unrollInts")
      val dispatchers = dispatchOf.get(unrolled, mem)

      if (dispatchers.isEmpty) {
        warn(c"Memory $mem had no dispatchers for $unrolled")
        warn(c"(This is likely a compiler bug where an unused memory access was not removed)")
      }

      dispatchers.foreach{origDispatches =>
        if (origDispatches.size != 1) {
          bug(c"Readers should have exactly one dispatch, $original -> $unrolled had ${origDispatches.size}")
          bug(original.ctx)
        }
        val dispatches = origDispatches.flatMap{orig =>
          dbgs(c"  Dispatch #$orig: ")
          dbgs(c"    Previous unroll numbers: ")
          val others = pachinko.getOrElse( (original,mem,orig), Nil)

          val banking = duplicatesOf(mem).apply(orig) match {
            case banked: BankedMemory => banked.dims.map(_.banks)
            case diagonal: DiagonalMemory => Seq(diagonal.banks) ++ List.fill(diagonal.nDims - 1)(1)
          }

          // Address channels taken care of by banking
          val bankedChannels = if (!isAccessWithoutAddress(original)) {
            accessPatternOf.get(original).map{ patterns =>
              dbgs(c"  Access pattern $patterns")
              if (patterns.exists(_.isGeneral)) {
                // TODO: Not sure if this is even remotely correct...
                // (unrollFactorsOf(original) diff unrollFactorsOf(mem)).flatten.map{case Exact(c) => c.toInt }

                var used: Set[Exp[Index]] = Set.empty
                def bankFactor(i: Exp[Index]): Int = {
                  if (!used.contains(i)) {
                    used += i
                    parFactorOf(i) match {case Exact(c) => c.toInt }
                  }
                  else 1
                }

                patterns.zip(banking).map{
                  case (GeneralAffine(prods,_),actualBanks) =>
                    val requiredBanks = prods.map(p => bankFactor(p.i) ).product
                    java.lang.Math.min(requiredBanks, actualBanks)

                  case (pattern, actualBanks) => pattern.index match {
                    case Some(i) =>
                      val requiredBanking = bankFactor(i)
                      java.lang.Math.min(requiredBanking, actualBanks)
                    case None => 1
                  }
                }
              }
              else {
                val iters = patterns.map(_.index)
                iters.distinct.map{
                  case x@Some(i) =>
                    dbgs(c"      Index: $i")
                    val requiredBanking = parFactorOf(i) match {case Exact(p) => p.toInt }
                    val actualBanking = banking(iters.indexOf(x))
                    dbgs(c"      Actual banking: $actualBanking")
                    dbgs(c"      Required banking: $requiredBanking")
                    java.lang.Math.min(requiredBanking, actualBanking) // actual may be higher than required, or vice versa
                  case None => 1
                }
              }
            }.getOrElse(Seq(1))
          }
          else {
            banking
          }

          val banks = bankedChannels.product
          val duplicates = (channels + banks - 1) / banks // ceiling(channels/banks)

          dbgs(c"    Bankings: $banking")
          dbgs(c"    Banked Channels: $bankedChannels ($banks)")
          dbgs(c"    Duplicates: $duplicates")

          others.foreach{other => dbgs(c"      $other") }

          val dispatchStart = orig + duplicates * others.count{p => p == unrollInts }
          pachinko += (original,mem,orig) -> (unrollInts +: others)

          val dispatches = List.tabulate(duplicates){i => dispatchStart + i}.toSet

          dbgs(c"    Setting new dispatch values: $dispatches")

          //dispatchOf(unrolled, mem) = Set(dispatch)
          dispatches.foreach{d => portsOf(unrolled, mem, d) = portsOf(unrolled, mem, orig) }
          dispatches
        }

        if (dispatches.isEmpty) {
          dbg(c"Dispatches created for $unrolled on memory $mem (dispatches: $origDispatches) was empty")
          bug(unrolled.ctx, c"Dispatches created for $unrolled on memory $mem (dispatches: $origDispatches) was empty.")
          bug(c"${str(unrolled)}")
        }

        dispatchOf(unrolled, mem) = dispatches
      }
    }
  }

  override def transform[A:Type](lhs: Sym[A], rhs: Op[A])(implicit ctx: SrcCtx): Exp[A] = (rhs match {
    case e:Hwblock =>
      inHwScope = true
      val lhs2 = super.transform(lhs,rhs)
      inHwScope = false
      lhs2
    case e:OpForeach        => unrollForeachNode(lhs, e)
    case e:OpReduce[_]      => unrollReduceNode(lhs, e)
    case e:OpMemReduce[_,_] => unrollMemReduceNode(lhs, e)
    case _ => super.transform(lhs, rhs)
  }).asInstanceOf[Exp[A]]

  val writeParallelizer: PartialFunction[(Unroller, Def, SrcCtx),Exp[MUnit]] = {
    case (lanes, e@RegFileStore(reg,inds,data,en), ctx) =>
      val addrs = lanes.map{p => inds.map(f(_)) }
      val datas = lanes.map{p => f(data) }
      val ens   = lanes.map{p => f(en) }
      RegFile.par_store(f(reg),addrs,datas,ens)(e.mT,e.bT,ctx,state)

    case (lanes, e@LineBufferEnq(lb,data,en), ctx) =>
      val datas = lanes.map{p => f(data) }
      val ens   = lanes.map{p => Bit.and(f(en), globalValid()) }
      LineBuffer.par_enq(f(lb), datas, ens)(e.mT,e.bT,ctx,state)

    case (lanes, e@LineBufferRotateEnq(lb,row,data,en), ctx) =>
      val datas = lanes.map{p => f(data) }
      val ens   = lanes.map{p => Bit.and(f(en), globalValid()) }
      val rw    = lanes.inLane(0){ f(row) }
      LineBuffer.par_rotateEnq(f(lb), rw, datas, ens)(e.mT,e.bT,ctx,state)

    case (lanes, e@FIFOEnq(fifo, data, en), ctx) =>
      val datas = lanes.map{p => f(data) }
      val ens   = lanes.map{p => Bit.and( f(en), globalValid()) }
      FIFO.par_enq(f(fifo), datas, ens)(e.mT,e.bT,ctx,state)

    case (lanes, e@FILOPush(filo, data, en), ctx) =>
      val datas = lanes.map{p => f(data) }
      val ens   = lanes.map{p => Bit.and( f(en), globalValid()) }
      FILO.par_push(f(filo), datas, ens)(e.mT,e.bT,ctx,state)

    case (lanes, e@StreamWrite(stream, data, en), ctx) =>
      val datas = lanes.map{p => f(data) }
      val ens   = lanes.map{p => Bit.and( f(en), globalValid()) }
      StreamOut.par_write(f(stream), datas, ens)(e.mT,e.bT,ctx,state)

    // TODO: Assuming dims and ofs are not needed for now
    case (lanes, e@SRAMStore(sram,dims,inds,ofs,data,en), ctx) =>
      val addrs = lanes.map{p => inds.map(f(_)) }
      val datas = lanes.map{p => f(data) }
      val ens   = lanes.map{p => Bit.and(f(en), globalValid()) }
      SRAM.par_store(f(sram), addrs, datas, ens)(e.mT,e.bT,ctx,state)
  }

  val readParallelizer: PartialFunction[(Unroller, Def, SrcCtx),Exp[Vector[_]]] = {
    case (lanes, e@RegFileLoad(reg,inds,en), ctx) =>
      val addrs = lanes.map{p => inds.map(f(_)) }
      val ens   = lanes.map{p => f(en) }
      RegFile.par_load(f(reg), addrs, ens)(mtyp(e.mT), mbits(e.bT), ctx, state)

    case (lanes, e@LineBufferLoad(lb,row,col,en), ctx) =>
      val rows = lanes.map{p => f(row) }
      val cols = lanes.map{p => f(col) }
      val ens  = lanes.map{p => f(en) }
      LineBuffer.par_load(f(lb), rows, cols, ens)(mtyp(e.mT),mbits(e.bT),ctx, state)

    case (lanes, e@FIFODeq(fifo, en), ctx) =>
      val enables = lanes.map{p => Bit.and(f(en), globalValid()) }
      FIFO.par_deq(f(fifo), enables)(mtyp(e.mT),mbits(e.bT),ctx,state)

    case (lanes, e@FILOPop(filo, en), ctx) =>
      val enables = lanes.map{p => Bit.and(f(en), globalValid()) }
      FILO.par_pop(f(filo), enables)(mtyp(e.mT),mbits(e.bT),ctx,state)

    case (lanes, e@StreamRead(stream, en), ctx) =>
      val enables = lanes.map{p => Bit.and(f(en), globalValid()) }
      StreamIn.par_read(f(stream), enables)(mtyp(e.mT),mbits(e.bT),ctx,state)

    // TODO: Assuming dims and ofs are not needed for now
    case (lanes, e@SRAMLoad(sram,dims,inds,ofs,en), ctx) =>
      val addrs = lanes.map{p => inds.map(f(_)) }
      val ens   = lanes.map{p => Bit.and(f(en), globalValid()) }
      SRAM.par_load(f(sram), addrs, ens)(mtyp(e.mT),mbits(e.bT),ctx,state)
  }

  /**
    * Create duplicates of the given node or special case, vectorized version
    * NOTE: Only can be used within reify scope
    * TODO: Whitelist loop invariant nodes
    **/
  def shouldUnrollAccess(lhs: Sym[_], rhs: Op[_], lanes: Unroller): Boolean = rhs match {
    case LocalReader(reads) if reads.forall{r => lanes.isCommon(r.mem) } => rhs match {
      case _:SRAMLoad[_] => !isLoopInvariant(lhs)
      case _:RegFileLoad[_] => !isLoopInvariant(lhs)
      case _ => true
    }

    case LocalWriter(writes) if writes.forall{r => lanes.isCommon(r.mem) } => rhs match {
      case _:SRAMStore[_] => !isLoopInvariant(lhs)
      case _ => true
    }

    case LocalResetter(resets) if resets.forall{r => lanes.isCommon(r.mem) } => rhs match {
      case _:SRAMStore[_] => !isLoopInvariant(lhs)
      case _ => true
    }
    case _ => false
  }
  def shouldUnifyAccess(lhs: Sym[_], rhs: Op[_], lanes: Unroller): Boolean = rhs match {
    case LocalReader(reads)  if isLoopInvariant(lhs) && reads.forall{r => lanes.isCommon(r.mem) } => rhs match {
      case _:SRAMLoad[_]    => true
      case _:RegFileLoad[_] => true
      case _ => false
    }
    case LocalWriter(writes) if isLoopInvariant(lhs) && writes.forall{r => lanes.isCommon(r.mem) } => rhs match {
      case _:SRAMStore[_] => true
      case _ => false
    }
    case LocalResetter(resets) if isLoopInvariant(lhs) && resets.forall{r => lanes.isCommon(r.mem) } => rhs match {
      case _:SRAMStore[_] => true
      case _ => false
    }
    case _ => false
  }

  def unroll[T](lhs: Sym[T], rhs: Op[T], lanes: Unroller)(implicit ctx: SrcCtx): List[Exp[_]] = rhs match {
    // Account for the edge ca e with FIFO writing
    case LocalReader(_) if shouldUnrollAccess(lhs, rhs, lanes) && readParallelizer.isDefinedAt((lanes,rhs,ctx)) =>
      val lhs2 = readParallelizer.apply((lanes,rhs,ctx))
      transferMetadata(lhs, lhs2)
      cloneFuncs.foreach{func => func(lhs2) }
      registerAccess(lhs, lhs2)
      logs(s"Unrolling $lhs = $rhs"); strMeta(lhs)
      logs(s"Created ${str(lhs2)}"); strMeta(lhs2)
      lanes.split(lhs, lhs2)(mtyp(lhs.tp),ctx)

    case LocalWriter(_) if shouldUnrollAccess(lhs, rhs, lanes) && writeParallelizer.isDefinedAt((lanes,rhs,ctx)) =>
      val lhs2 = writeParallelizer.apply((lanes,rhs,ctx))
      transferMetadata(lhs, lhs2)
      cloneFuncs.foreach{func => func(lhs2) }
      registerAccess(lhs, lhs2)
      logs(s"Unrolling $lhs = $rhs"); strMeta(lhs)
      logs(s"Created ${str(lhs2)}"); strMeta(lhs2)
      lanes.unify(lhs, lhs2)

    case LocalResetter(_) if shouldUnrollAccess(lhs, rhs, lanes) && writeParallelizer.isDefinedAt((lanes,rhs,ctx)) =>
      val lhs2 = writeParallelizer.apply((lanes,rhs,ctx))
      transferMetadata(lhs, lhs2)
      cloneFuncs.foreach{func => func(lhs2) }
      registerAccess(lhs, lhs2)
      logs(s"Unrolling $lhs = $rhs"); strMeta(lhs)
      logs(s"Created ${str(lhs2)}"); strMeta(lhs2)
      lanes.unify(lhs, lhs2)

    case e: Switch[_] => duplicateSwitch(lhs, e, lanes)

    case e: OpForeach        => duplicateController(lhs,rhs,lanes){ unrollForeachNode(lhs, e) }
    case e: OpReduce[_]      => duplicateController(lhs,rhs,lanes){ unrollReduceNode(lhs, e) }
    case e: OpMemReduce[_,_] => duplicateController(lhs,rhs,lanes){ unrollMemReduceNode(lhs, e) }
    case _ if isControlNode(lhs) => duplicateController(lhs,rhs,lanes){ cloneOp(lhs, rhs) }

    case e: RegNew[_] =>
      logs(s"Duplicating $lhs = $rhs")
      val dups = lanes.duplicate(lhs, rhs)
      logs(s"  Created registers: ")
      lanes.foreach{p => dbgs(s"  $p: $lhs -> ${f(lhs)}") }
      dups

    // For inner loop invariant ops, no need to copy the operation - can just do once and broadcast to all lanes
    case _:EnabledPrimitive[_] if shouldUnifyAccess(lhs, rhs, lanes) =>
      logs(s"Unifying $lhs = $rhs (loop invariant)")
      val lhs2 = lanes.inLane(0){ cloneOp(lhs, rhs) } // Doesn't matter which lane, as long as it's in one of them
      lanes.unify(lhs, lhs2)

    case _ =>
      logs(s"Duplicating $lhs = $rhs")
      val dups = lanes.duplicate(lhs, rhs)
      dups
  }

  def duplicateSwitch[T](lhs: Sym[T], rhs: Switch[T], lanes: Unroller): List[Exp[_]] = {
    dbgs(s"Unrolling Switch:")
    dbgs(s"$lhs = $rhs")
    if (lanes.size > 1 && isOuterControl(lhs)) lhs.tp match {
      case Bits(bT) =>
        // TODO: Adding registers here is pretty hacky, should find way to avoid this
        val regs = List.tabulate(lanes.size){p => Reg[T](bT.zero)(mtyp(lhs.tp),mbits(bT),lhs.ctx,state) }
        val writes = new Array[Exp[_]](lanes.size)
        Parallel.op_parallel_pipe(globalValids, () => {
          lanes.map{p =>
            dbgs(s"$lhs duplicate ${p+1}/${lanes.size}")
            val pipe = Pipe.op_unit_pipe(globalValids, () => {
              val lhs2 = cloneOp(lhs, rhs)
              val write = regs(p) := lhs.tp.wrapped(lhs2)
              writes(p) = write.s
              unit
            })
            levelOf(pipe) = OuterControl
            styleOf(pipe) = SeqPipe
          }
          unit
        })
        val reads = lanes.map{p =>
          val read = lhs.tp.unwrapped(regs(p).value(lhs.ctx,state))
          register(lhs, read)
          read
        }

        (regs.map(_.s), writes, reads).zipped.foreach{case (reg, write, read) =>
          duplicatesOf(reg) = List(BankedMemory(Seq(NoBanking(1)),1,isAccum = false))
          dispatchOf(write, reg) = Set(0)
          portsOf(write,reg, 0) = Set(0)
          dispatchOf(read, reg) = Set(0)
          portsOf(read, reg, 0) = Set(0)
        }

        reads

      case _ =>
        assert(lhs.tp == UnitType, s"Could not unroll non-bit, non-unit switch $lhs")
        val lhs2 = Parallel.op_parallel_pipe(globalValids, () => {
          lanes.foreach{p =>
            dbgs(s"$lhs duplicate ${p+1}/${lanes.size}")
            cloneOp(lhs, rhs)
          }
          unit
        })
        lanes.unifyUnsafe(lhs, lhs2)
    }
    else if (lanes.size > 1) {
      lanes.duplicate(lhs, rhs)
    }
    else {
      val first = lanes.inLane(0){ cloneOp(lhs, rhs) }
      lanes.unify(lhs, first)
    }
  }

  def duplicateController[T](lhs: Sym[T], rhs: Op[T], lanes: Unroller)(unroll: => Exp[_]): List[Exp[_]] = {
    dbgs(s"Duplicating controller:")
    dbgs(s"$lhs = $rhs")
    if (lanes.size > 1) {
      val lhs2 = Parallel.op_parallel_pipe(globalValids, () => {
        lanes.foreach{p =>
          dbgs(s"$lhs duplicate ${p+1}/${lanes.size}")
          unroll
        }
        unit
      })
      lanes.unify(lhs, lhs2)
    }
    else {
      dbgs(s"$lhs duplicate 1/1")
      val first = lanes.inLane(0){ unroll }
      lanes.unify(lhs, first)
    }
  }


  /**
    * Unrolls purely independent loop iterations
    * NOTE: The func block should already have been mirrored to update dependencies prior to unrolling
    */
  def unrollMap[T:Type](func: Block[T], lanes: Unroller)(implicit ctx: SrcCtx): List[Exp[T]] = {
    val origResult = func.result

    tab += 1
    mangleBlock(func, {stms =>
      stms.foreach{case TP(lhs,rhs) => unroll(lhs, rhs, lanes)(lhs.ctx) }
    })
    tab -= 1

    // Get the list of duplicates for the original result of this block
    lanes.map{p => f(origResult) }
  }


  def fullyUnrollForeach(
    lhs:    Exp[_],
    cchain: Exp[CounterChain],
    func:   Block[MUnit],
    iters:  Seq[Bound[Index]]
  )(implicit ctx: SrcCtx): Exp[_] = {
    dbgs(s"Fully unrolling foreach $lhs")
    val lanes = FullUnroller(cchain, iters, isInnerControl(lhs))
    val blk = stageSealedBlock {
      val is = lanes.indices
      val vs = lanes.valids
      unrollMap(func, lanes)
      unit
    }
    val effects = blk.effects
    val lhs2 = stageEffectful(UnitPipe(globalValids, blk), effects)(ctx)
    transferMetadata(lhs, lhs2)

    if (styleOf(lhs) != StreamPipe) styleOf(lhs2) = SeqPipe

    dbgs(s"Created unit pipe ${str(lhs2)}")
    lhs2
  }

  def partiallyUnrollForeach (
    lhs:    Exp[_],
    cchain: Exp[CounterChain],
    func:   Block[MUnit],
    iters:  Seq[Bound[Index]]
  )(implicit ctx: SrcCtx): Exp[_] = {
    dbgs(s"Unrolling foreach $lhs")
    val lanes = PartialUnroller(cchain, iters, isInnerControl(lhs))
    val is = lanes.indices
    val vs = lanes.indexValids

    val blk = stageSealedBlock { unrollMap(func, lanes); unit }
    val effects = blk.effects
    val lhs2 = stageEffectful(UnrolledForeach(globalValids, cchain, blk, is, vs), effects.star)(ctx)
    transferMetadata(lhs, lhs2)

    dbgs(s"Created foreach ${str(lhs2)}")
    lhs2
  }
  def unrollForeachNode(lhs: Sym[_], rhs: OpForeach)(implicit ctx: SrcCtx): Exp[_] = {
    val OpForeach(en, cchain, func, iters) = rhs
    if (canFullyUnroll(cchain) && !spatialConfig.enablePIR) fullyUnrollForeach(lhs, f(cchain), func, iters)
    else partiallyUnrollForeach(lhs, f(cchain), func, iters)
  }


  def unrollReduceTree[T:Type:Bits](
    inputs: Seq[Exp[T]],
    valids: Seq[Exp[Bit]],
    ident:   Option[Exp[T]],
    reduce: (Exp[T], Exp[T]) => Exp[T]
  )(implicit ctx: SrcCtx): Exp[T] = ident match {
    case Some(z) =>
      dbg(c"Unrolling reduction tree with zero $z")
      val validInputs = inputs.zip(valids).map{case (in,v) => Math.math_mux(v, in, z) }
      Math.reduceTree(validInputs){(x: Exp[T], y: Exp[T]) => reduce(x,y) }

    case None =>
      // ASSUMPTION: If any values are invalid, they are at the end of the list (corresponding to highest index values)
      // TODO: This may be incorrect if we parallelize by more than the innermost iterator
      val inputsWithValid = inputs.zip(valids)
      dbg("Unrolling reduction tree with " + inputsWithValid.length + " inputs: " + inputs.mkString(", "))
      Math.reduceTree(inputsWithValid){(x: (Exp[T], Exp[Bit]), y: (Exp[T],Exp[Bit])) =>
        val res = reduce(x._1, y._1)
        (Math.math_mux(y._2, res, x._1), Bit.or(x._2, y._2)) // res is valid if x or y is valid
      }._1
  }


  def unrollReduceAccumulate[T:Type:Bits,C[T]](
    accum:  Exp[C[T]],             // Accumulator
    inputs: Seq[Exp[T]],           // Symbols to be reduced
    valids: Seq[Exp[Bit]],         // Data valid bits corresponding to inputs
    ident:  Option[Exp[T]],        // Optional identity value
    fold:   Option[Exp[T]],        // Optional fold value
    reduce: Lambda2[T,T,T],        // Reduction function
    load:   Lambda1[C[T],T],       // Load function from accumulator
    store:  Lambda2[C[T],T,MUnit], // Store function to accumulator
    iters:  Seq[Bound[Index]],     // Iterators for entire reduction (used to determine when to reset)
    start:  Seq[Exp[Index]],       // Start for each iterator
    isInner: Boolean
  )(implicit ctx: SrcCtx): Exp[MUnit] = {

    val treeResult = inReduction(isInner){ unrollReduceTree[T](inputs, valids, ident, reduce.toFunction2) }
    val redType = reduceType(reduce.result)

    val result = inReduction(isInner){
      val accValue = load.inline(accum)
      val isFirst = Math.reduceTree(iters.zip(start).map{case (i,st) => FixPt.eql(i, st) }){(x,y) => Bit.and(x,y) }

      isReduceStarter(accValue) = true

      if (spatialConfig.enablePIR) {
        inCycle(redType){ reduce.inline(treeResult, accValue) }
      }
      else fold match {
        // FOLD: On first iteration, use init value rather than zero
        case Some(init) =>
          val accumOrFirst = Math.math_mux(isFirst, init, accValue)
          reduceType(accumOrFirst) = redType
          reduce.inline(treeResult, accumOrFirst)

        // REDUCE: On first iteration, store result of tree, do not include value from accum
        // TODO: Could also have third case where we use ident instead of loaded value. Is one better?
        case None =>
          val res2 = reduce.inline(treeResult, accValue)
          val mux = Math.math_mux(isFirst, treeResult, res2)
          reduceType(mux) = redType
          mux
      }
    }

    isReduceResult(result) = true

    inReduction(isInner){ store.inline(accum, result) }
  }

  def fullyUnrollReduce[T](
    lhs:    Exp[_],
    en:     Seq[Exp[Bit]],
    cchain: Exp[CounterChain],
    accum:  Exp[Reg[T]],
    ident:  Option[Exp[T]],
    fold:   Option[Exp[T]],
    load:   Lambda1[Reg[T],T],
    store:  Lambda2[Reg[T],T,MUnit],
    func:   Block[T],
    reduce: Lambda2[T,T,T],
    rV:     (Bound[T],Bound[T]),
    iters:  Seq[Bound[Index]]
  )(implicit mT: Type[T], bT: Bits[T], ctx: SrcCtx) = {
    logs(s"Fully unrolling reduce $lhs")
    val lanes = FullUnroller(cchain, iters, isInnerControl(lhs))
    val mC = typ[Reg[T]]

    val blk = stageSealedLambda1(accum){
      val values = unrollMap(func, lanes)(mT, ctx)
      val valids = () => lanes.valids.map{vs => Math.reduceTree(vs){(a,b) => Bit.and(a,b) }}

      if (isOuterControl(lhs)) {
        logs("Fully unrolling outer reduce")
        val pipe = Pipe.op_unit_pipe(globalValids, () => {
          val foldValid = fold.map{_ => Bit.const(true) }
          val result = unrollReduceTree[T]((fold ++ values).toSeq, (foldValid ++ valids()).toSeq, ident, reduce.toFunction2)
          store.inline(accum, result)
        })
        styleOf(pipe) = SeqPipe
        levelOf(pipe) = InnerControl
      }
      else {
        logs("Fully unrolling inner reduce")
        val foldValid = fold.map{_ => Bit.const(true) }
        val result = unrollReduceTree[T]((fold ++ values).toSeq, (foldValid ++ valids()).toSeq, ident, reduce.toFunction2)
        store.inline(accum, result)
      }
      unit
    }
    val effects = blk.effects
    val lhs2 = stageEffectful(UnitPipe(globalValids, blk), effects)(ctx)
    transferMetadata(lhs, lhs2)
    if (styleOf(lhs) != StreamPipe) styleOf(lhs2) = SeqPipe
    logs(c"Created unit pipe ${str(lhs2)}")
    lhs2
  }

  def partiallyUnrollReduce[T](
    lhs:    Exp[_],                   // Original pipe symbol
    en:     Seq[Exp[Bit]],            // Enables
    cchain: Exp[CounterChain],        // Counterchain
    accum:  Exp[Reg[T]],              // Accumulator
    ident:  Option[Exp[T]],           // Optional identity value for reduction
    fold:   Option[Exp[T]],           // Optional value to fold with reduction
    load:   Lambda1[Reg[T],T],        // Load function for accumulator
    store:  Lambda2[Reg[T],T,MUnit],  // Store function for accumulator
    func:   Block[T],                 // Map function
    reduce: Lambda2[T,T,T],           // Reduce function
    rV:     (Bound[T],Bound[T]),      // Bound symbols used to reify rFunc
    iters:  Seq[Bound[Index]]         // Bound iterators for map loop
  )(implicit mT: Type[T], bT: Bits[T], ctx: SrcCtx) = {
    logs(s"Unrolling reduce $lhs")
    val lanes = PartialUnroller(cchain, iters, isInnerControl(lhs))
    val inds2 = lanes.indices
    val vs = lanes.indexValids
    val mC = typ[Reg[T]]
    val start = counterStarts(cchain).map(_.getOrElse(int32s(0)))

    val blk = stageSealedLambda1(accum) {
      logs("Unrolling map")
      val values = unrollMap(func, lanes)(mT,ctx)
      val valids = () => lanes.valids.map{vs => Math.reduceTree(vs){(a,b) => Bit.and(a,b) } }

      if (isOuterControl(lhs)) {
        logs("Unrolling unit pipe reduce")
        val pipe = Pipe.op_unit_pipe(globalValids, () => {
          unrollReduceAccumulate[T,Reg](accum, values, valids(), ident, fold, reduce, load, store, inds2.map(_.head), start, isInner = false)
        })
        styleOf(pipe) = SeqPipe
        levelOf(pipe) = InnerControl
      }
      else {
        logs("Unrolling inner reduce")
        unrollReduceAccumulate[T,Reg](accum, values, valids(), ident, fold, reduce, load, store, inds2.map(_.head), start, isInner = true)
      }
      unit
    }

    val effects = blk.effects
    val lhs2 = stageEffectful(UnrolledReduce(globalValids ++ en, cchain, accum, blk, inds2, vs)(mT,mC), effects.star)(ctx)
    transferMetadata(lhs, lhs2)
    logs(s"Created reduce ${str(lhs2)}")
    lhs2
  }
  def unrollReduceNode[T](lhs: Sym[_], rhs: OpReduce[T])(implicit ctx: SrcCtx) = {
    val OpReduce(en,cchain,accum,map,load,reduce,store,zero,fold,rV,iters) = rhs
    if (canFullyUnroll(cchain) && !spatialConfig.enablePIR) {
      fullyUnrollReduce[T](lhs, f(en), f(cchain), f(accum), zero, fold, load, store, map, reduce, rV, iters)(rhs.mT, rhs.bT, ctx)
    }
    else {
      partiallyUnrollReduce[T](lhs, f(en), f(cchain), f(accum), zero, fold, load, store, map, reduce, rV, iters)(rhs.mT, rhs.bT, ctx)
    }
  }



  def unrollMemReduce[T,C[T]](
    lhs:       Exp[_],                // Original pipe symbol
    en:        Seq[Exp[Bit]],         // Enables
    cchainMap: Exp[CounterChain],     // Map counterchain
    cchainRed: Exp[CounterChain],     // Reduction counterchain
    accum:     Exp[C[T]],             // Accumulator (external)
    ident:     Option[Exp[T]],        // Optional identity value for reduction
    fold:      Boolean,               // Optional value to fold with reduction
    func:      Block[C[T]],           // Map function
    loadRes:   Lambda1[C[T],T],       // Load function for intermediate values
    loadAcc:   Lambda1[C[T],T],       // Load function for accumulator
    reduce:    Lambda2[T,T,T],        // Reduction function
    storeAcc:  Lambda2[C[T],T,MUnit], // Store function for accumulator
    rV:        (Bound[T],Bound[T]),   // Bound symbol used to reify rFunc
    itersMap:  Seq[Bound[Index]],     // Bound iterators for map loop
    itersRed:  Seq[Bound[Index]]      // Bound iterators for reduce loop
  )(implicit mT: Type[T], bT: Bits[T], mC: Type[C[T]], ctx: SrcCtx) = {
    logs(s"Unrolling accum-fold $lhs")

    val mapLanes = PartialUnroller(cchainMap, itersMap, isInnerLoop = false)
    val isMap2 = mapLanes.indices
    val mvs = mapLanes.indexValids
    val partial = func.result
    val start = counterStarts(cchainMap).map(_.getOrElse(int32s(0)))
    val redType = reduceType(reduce.result)

    val blk = stageSealedLambda1(f(accum)) {
      logs(s"[Accum-fold $lhs] Unrolling map")
      val mems = unrollMap(func, mapLanes)
      val mvalids = () => mapLanes.valids.map{vs => Math.reduceTree(vs){(a,b) => Bit.and(a,b)} }

      if (isUnitCounterChain(cchainRed)) {
        logs(s"[Accum-fold $lhs] Unrolling unit pipe reduction")
        val rpipe = Pipe.op_unit_pipe(globalValids, () => {
          val values = inReduction(false){ mems.map{mem => loadRes.inline(mem) } }
          val foldValue = if (fold) { Some( loadAcc.inline(accum) ) } else None
          inReduction(false){ unrollReduceAccumulate[T,C](accum, values, mvalids(), ident, foldValue, reduce, loadAcc, storeAcc, isMap2.map(_.head), start, isInner = false) }
          unit
        })
        styleOf(rpipe) = SeqPipe
        levelOf(rpipe) = InnerControl
      }
      else {
        logs(s"[Accum-fold $lhs] Unrolling pipe-reduce reduction")
        tab += 1

        val reduceLanes = PartialUnroller(cchainRed, itersRed, true)
        val isRed2 = reduceLanes.indices
        val rvs = reduceLanes.indexValids
        reduceLanes.foreach{p =>
          logs(s"Lane #$p")
          itersRed.foreach{i => logs(s"  $i -> ${f(i)}") }
        }

        val rBlk = stageSealedBlock {
          logs(c"[Accum-fold $lhs] Unrolling map loads")
          logs(c"  memories: $mems")

          val values: Seq[Seq[Exp[T]]] = inReduction(false){
            mems.map{mem =>
              withSubstScope(partial -> mem) {
                unrollMap(loadRes, reduceLanes)(mT,ctx)
              }
            }
          }

          logs(s"[Accum-fold $lhs] Unrolling accum loads")
          reduceLanes.foreach{p =>
            logs(s"Lane #$p")
            itersRed.foreach{i => logs(s"  $i -> ${f(i)}") }
          }

          val accValues = inReduction(false){ unrollMap(loadAcc, reduceLanes)(mT,ctx) }

          logs(s"[Accum-fold $lhs] Unrolling reduction trees and cycles")
          reduceLanes.foreach{p =>
            val laneValid = Math.reduceTree(reduceLanes.valids(p)){(a,b) => Bit.and(a,b)}

            logs(s"Lane #$p:")
            tab += 1
            val inputs = values.map(_.apply(p)) // The pth value of each vector load
            val valids = mvalids().map{mvalid => Bit.and(mvalid, laneValid) }

            logs("Valids:")
            valids.foreach{s => logs(s"  ${str(s)}")}

            logs("Inputs:")
            inputs.foreach{s => logs(s"  ${str(s)}") }

            val accValue = accValues(p)

            val result = inReduction(true){
              val treeResult = unrollReduceTree(inputs, valids, ident, reduce.toFunction2)
              val isFirst = Math.reduceTree(isMap2.map(_.head).zip(start).map{case (i,st) => FixPt.eql(i, st) }){(x,y) => Bit.and(x,y) }

              isReduceStarter(accValue) = true

              if (spatialConfig.enablePIR) {
                inCycle(redType){ reduce.inline(treeResult, accValue) }
              }
              else if (fold) {
                // FOLD: On first iteration, use value of accumulator value rather than zero
                //val accumOrFirst = math_mux(isFirst, init, accValue)
                reduce.inline(treeResult, accValue)
              }
              else {
                // REDUCE: On first iteration, store result of tree, do not include value from accum
                val res2 = reduce.inline(treeResult, accValue)
                val mux = Math.math_mux(isFirst, treeResult, res2)
                reduceType(mux) = redType
                mux
              }
            }

            isReduceResult(result) = true
            isReduceStarter(accValue) = true
            register(reduce.result -> result)  // Lane-specific substitution

            tab -= 1
          }

          logs(s"[Accum-fold $lhs] Unrolling accumulator store")
          inReduction(false){ unrollMap(storeAcc, reduceLanes) }
          unit
        }

        val effects = rBlk.effects
        val rpipe = stageEffectful(UnrolledForeach(Nil, cchainRed, rBlk, isRed2, rvs), effects.star)(ctx)
        styleOf(rpipe) = InnerPipe
        levelOf(rpipe) = InnerControl
        tab -= 1
      }
      unit
    }

    val effects = blk.effects
    val lhs2 = stageEffectful(UnrolledReduce(globalValids ++ en, cchainMap, accum, blk, isMap2, mvs)(mT,mC), effects.star)(ctx)
    transferMetadata(lhs, lhs2)

    logs(s"[Accum-fold] Created reduce ${str(lhs2)}")
    lhs2
  }
  def unrollMemReduceNode[T,C[T]](lhs: Sym[_], rhs: OpMemReduce[T,C])(implicit ctx: SrcCtx) = {
    val OpMemReduce(en,cchainMap,cchainRed,accum,func,loadRes,loadAcc,reduce,storeAcc,zero,fold,rV,itersMap,itersRed) = rhs
    unrollMemReduce(lhs,f(en),f(cchainMap),f(cchainRed),f(accum),f(zero),fold,func,loadRes,loadAcc,reduce,storeAcc,rV,itersMap,itersRed)(rhs.mT,rhs.bT,rhs.mC,ctx)
  }

  override def mirror(lhs: Seq[Sym[_]], rhs: Def): Seq[Exp[_]] = rhs match {
    case op:Op[_] => Seq(cloneOp(lhs.head.asInstanceOf[Sym[Any]], op.asInstanceOf[Op[Any]]))
    case _ => super.mirror(lhs, rhs)
  }

  def cloneOp[A](lhs: Sym[A], rhs: Op[A]): Exp[A] = {
    def cloneOrMirror(lhs: Sym[A], rhs: Op[A])(implicit mA: Type[A], ctx: SrcCtx): Exp[A] = (lhs match {
      case Def(op: EnabledControlNode)  => op.mirrorAndEnable(this, globalValids)
      case Def(op: EnabledPrimitive[_]) => op.mirrorAndEnable(this, globalValid)
      case _ => rhs.mirrorNode(f).head
    }).asInstanceOf[Exp[A]]

    logs(c"Cloning $lhs = $rhs")
    strMeta(lhs)

    val (lhs2, isNew) = transferMetadataIfNew(lhs){ cloneOrMirror(lhs, rhs)(mtyp(lhs.tp), lhs.ctx) }

    if (isAccess(lhs) && isNew && inHwScope) {
      registerAccess(lhs, lhs2)
    }

    if (isNew) cloneFuncs.foreach{func => func(lhs2) }
    logs(c"Created ${str(lhs2)}")
    strMeta(lhs2)

    if (cloneFuncs.nonEmpty) {
      dbgs(c"Cloning $lhs = $rhs")
      metadata.get(lhs).foreach{m => dbgs(c" - ${m._1}: ${m._2}") }
      dbgs(c"Created ${str(lhs2)}")
      metadata.get(lhs2).foreach{m => dbgs(c" - ${m._1}: ${m._2}") }
    }

    lhs2
  }
}
