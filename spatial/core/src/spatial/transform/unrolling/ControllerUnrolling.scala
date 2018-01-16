package spatial.transform.unrolling

import argon.core._
import argon.nodes._
import spatial.aliases._
import spatial.banking._
import spatial.lang.Math
import spatial.metadata._
import spatial.nodes._
import spatial.utils._

import org.virtualized.SourceContext

trait ControllerUnrolling extends UnrollingBase {

  override def unroll[T](lhs: Sym[T], rhs: Op[T], lanes: Unroller)(implicit ctx: SrcCtx): List[Exp[_]] = rhs match {
    case e: Switch[_]        => duplicateSwitch(lhs, e, lanes)
    case e: OpForeach        => duplicateController(lhs,rhs,lanes){ unrollForeachNode(lhs, e) }
    case e: OpReduce[_]      => duplicateController(lhs,rhs,lanes){ unrollReduceNode(lhs, e) }
    case e: OpMemReduce[_,_] => duplicateController(lhs,rhs,lanes){ unrollMemReduceNode(lhs, e) }
    case e: UnitPipe         => duplicateController(lhs,rhs,lanes){ unrollUnit(lhs, e) }
    case e: ParallelPipe     => duplicateController(lhs,rhs,lanes){ unrollParallel(lhs, e) }
    case e: StateMachine[_]  => duplicateController(lhs,rhs,lanes){ unrollStateMachine(lhs, e) }
    case _ if isControlNode(lhs) => duplicateController(lhs,rhs,lanes){ cloneOp(lhs, rhs) }
    case _ => super.unroll(lhs, rhs, lanes)
  }

  override def transform[A:Type](lhs: Sym[A], rhs: Op[A])(implicit ctx: SrcCtx): Exp[A] = (rhs match {
    case e:OpForeach        => unrollForeachNode(lhs, e)
    case e:OpReduce[_]      => unrollReduceNode(lhs, e)
    case e:OpMemReduce[_,_] => unrollMemReduceNode(lhs, e)
    case e:StateMachine[_]  => unrollStateMachine(lhs, e)
    case e:Hwblock          => unrollAccel(lhs,e)
    case e:UnitPipe         => unrollUnit(lhs,e)
    case e:SwitchCase[_]    => unrollCase(lhs,e)
    case _ => super.transform(lhs, rhs)
  }).asInstanceOf[Exp[A]]

  def unrollStateMachine[T](lhs: Sym[T], rhs: StateMachine[_]): Exp[T] = {
    implicit val mT: Type[T] = mtyp(rhs.mT)
    implicit val bT: Bits[T] = mbits(rhs.bT)
    val state = rhs.state
    val state2 = fresh[T]
    val start2 = f(rhs.start).asInstanceOf[Exp[T]]

    val lanes = UnitUnroller(isInnerControl(lhs))
    val notDone = rhs.notDone.asInstanceOf[Lambda1[T,Bit]]
    val action  = rhs.action.asInstanceOf[Lambda1[T,MUnit]]
    val nextState = rhs.nextState.asInstanceOf[Lambda1[T,T]]
    val notDone2 = stageSealedLambda1(state2){ withSubstScope(state -> state2){
      mangleBlock(notDone, {stms => stms.foreach{stm => unroll(stm,lanes) }})
      lanes.map{_ => f(notDone.result) }.head
    }}
    val action2 = stageSealedLambda1(state2){ withSubstScope(state -> state2){
      mangleBlock(action, {stms => stms.foreach{stm => unroll(stm,lanes) }})
      lanes.map{_ => f(action.result) }.head
    }}
    val nextState2 = stageSealedLambda1(state2){ withSubstScope(state -> state2){
      mangleBlock(nextState, {stms => stms.foreach{stm => unroll(stm,lanes) }})
      lanes.map{_ => f(nextState.result) }.head
    }}
    val en = globalValids ++ f(rhs.en)
    val effects = notDone2.effects andAlso action2.effects andAlso nextState2.effects
    val lhs2 = stageEffectful(StateMachine[T](en,start2,notDone2,action2,nextState2,state2), effects)(ctx)
    transferMetadata(lhs -> lhs2)
    lhs2.asInstanceOf[Exp[T]]
  }

  def unrollCase[T](lhs: Sym[T], rhs: SwitchCase[T]): Exp[T] = {
    implicit val mT: Type[T] = lhs.tp
    val lanes = UnitUnroller(isInnerControl(lhs))
    val block = rhs.body
    val block2 = stageSealedBlock {
      mangleBlock(block, {stms => stms.foreach{stm => unroll(stm,lanes) }})
      lanes.map{_ => f(block.result) }.head
    }
    val effects = block2.effects andAlso Simple
    val lhs2 = stageEffectful(SwitchCase(block2), effects)(ctx)
    transferMetadata(lhs -> lhs2)
    lhs2.asInstanceOf[Exp[T]]
  }

  def unrollAccel[T](lhs: Sym[T], rhs: Hwblock): Exp[T] = {
    val lanes = UnitUnroller(isInnerControl(lhs))
    val block = rhs.func
    val block2 = stageSealedBlock {
      mangleBlock(block, {stms => stms.foreach { stm => unroll(stm, lanes) } })
      lanes.map{_ => f(block.result) }.head // List of duplicates for the original result of this block
    }
    val effects = block2.effects
    val lhs2 = stageEffectful(Hwblock(block2,rhs.isForever), effects)(ctx)
    transferMetadata(lhs -> lhs2)
    lhs2.asInstanceOf[Exp[T]]
  }

  def unrollUnit[T](lhs: Sym[T], rhs: UnitPipe): Exp[T] = {
    val lanes = UnitUnroller(isInnerControl(lhs))
    val block = rhs.func
    val block2 = stageSealedBlock {
      mangleBlock(block, {stms => stms.foreach { stm => unroll(stm, lanes) } })
      lanes.map{_ => f(block.result) }.head // List of duplicates for the original result of this block
    }
    val en = globalValids
    val effects = block2.effects
    val lhs2 = stageEffectful(UnitPipe(en,block2), effects)(ctx)
    transferMetadata(lhs -> lhs2)
    lhs2.asInstanceOf[Exp[T]]
  }

  def unrollParallel[T](lhs: Sym[T], rhs: ParallelPipe): Exp[T] = {
    val lanes = UnitUnroller(isInnerControl(lhs))
    val block = rhs.func
    val block2 = stageSealedBlock {
      mangleBlock(block, {stms => stms.foreach { stm => unroll(stm, lanes) } })
      lanes.map{_ => f(block.result) }.head // List of duplicates for the original result of this block
    }
    val en = globalValids
    val effects = block2.effects
    val lhs2 = stageEffectful(ParallelPipe(en,block2), effects)(ctx)
    transferMetadata(lhs -> lhs2)
    lhs2.asInstanceOf[Exp[T]]
  }


  /**
    * Duplicate the given switch based on the lanes Unroller instance
    */
  def duplicateSwitch[T](lhs: Sym[T], rhs: Switch[T], lanes: Unroller): List[Exp[_]] = {
    dbgs(s"Unrolling Switch:")
    dbgs(s"$lhs = $rhs")
    if (lanes.size > 1 && isOuterControl(lhs)) lhs.tp match {
      case Bits(bt) =>
        implicit val mT: Type[T] = mtyp(lhs.tp)
        implicit val bT: Bits[T] = mbits(bt)
        implicit val ctx: SrcCtx = lhs.ctx

        // TODO: Adding registers here is pretty hacky, should find way to avoid this
        val regs: List[Reg[T]] = List.tabulate(lanes.size){p => Reg[T](bT.zero) }
        val writes = new Array[Exp[_]](lanes.size)
        Parallel.op_parallel_pipe(globalValids, () => {
          lanes.map{p =>
            dbgs(s"$lhs duplicate ${p+1}/${lanes.size}")
            val pipe = Pipe.op_unit_pipe(globalValids, () => {
              val lhs2 = cloneOp(lhs, rhs)
              val write = regs(p) := wrap(lhs2)
              writes(p) = write.s
              unit
            })
            levelOf(pipe) = OuterControl
            styleOf(pipe) = SeqPipe
          }
          unit
        })
        val reads = lanes.map{p =>
          val read = unwrap(regs(p).value)
          register(lhs -> read)
          read
        }

        (regs.map(_.s), writes, reads).zipped.foreach{case (reg, write, read) =>
          duplicatesOf(reg) = Seq(Memory(RegBank(),1,isAccum = false))
          //dispatchOf((write,Seq(0)), reg) = Set(0)
          portsOf(write,reg, 0) = Set(0)
          //dispatchOf((read,Seq(0)), reg) = Set(0)
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

  /**
    * Duplicate the controller using the given call by name unroll function.
    * Duplication is done based on the Unroller helper instance lanes.
    */
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
    mangleBlock(func, {stms => stms.foreach{stm => unroll(stm,lanes) } })
    lanes.map{_ => f(func.result) } // List of duplicates for the original result of this block
  }

  def unrollFor[T:Type](func: Block[T], lanes: Unroller)(implicit ctx: SrcCtx): Unit = {
    mangleBlock(func, {stms => stms.foreach{stm => unroll(stm,lanes) }})
  }

  /***********************/
  /** FOREACH UNROLLING **/
  /***********************/

  def fullyUnrollForeach(
    lhs:    Exp[_],
    cchain: Exp[CounterChain],
    func:   Block[MUnit],
    iters:  Seq[Bound[Index]]
  )(implicit ctx: SrcCtx): Exp[_] = {
    dbgs(s"Fully unrolling foreach $lhs")
    val lanes = FullUnroller(cchain, iters, isInnerControl(lhs))
    val blk = stageSealedBlock { unrollMap(func, lanes); unit }
    val lhs2 = stageEffectful(UnitPipe(globalValids, blk), blk.effects)(ctx)
    transferMetadata(lhs -> lhs2)

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
    transferMetadata(lhs -> lhs2)

    dbgs(s"Created foreach ${str(lhs2)}")
    lhs2
  }
  def unrollForeachNode(lhs: Sym[_], rhs: OpForeach)(implicit ctx: SrcCtx): Exp[_] = {
    val OpForeach(en, cchain, func, iters) = rhs
    if (canFullyUnroll(cchain) && !spatialConfig.enablePIR) fullyUnrollForeach(lhs, f(cchain), func, iters)
    else partiallyUnrollForeach(lhs, f(cchain), func, iters)
  }



  /***********************/
  /** REDUCE  UNROLLING **/
  /***********************/

  // Hack to get the accumulator duplicate from the original and the loadAccum block for this reduction
  // Assumes that the accumulator corresponds to exactly one duplicate
  def accumHack[T](orig: Exp[T], load: Block[_]): Exp[T] = {
    val contents = blockNestedContents(load)
    val readers = readersOf(orig)
    readers.find{reader => contents.exists(_.lhs.contains(reader.node)) } match {
      case Some(reader) =>
        val mapping = dispatchOf.getUnsafe(reader.node, orig)
        if (mapping.isEmpty) throw new Exception(s"No dispatch found in reduce for accumulator $orig")
        val dispatch = mapping.head._2.head
        if (!memories.contains((orig,dispatch))) throw new Exception(s"No duplicate found for accumulator $orig")
        memories((orig,dispatch)).asInstanceOf[Exp[T]]

      case None => throw new Exception(s"No reader found in reduce for accumulator $orig")
    }
  }

  def unrollReduceTree[T:Type:Bits](
    inputs: Seq[Exp[T]],
    valids: Seq[Exp[Bit]],
    ident:   Option[Exp[T]],
    reduce: (Exp[T], Exp[T]) => Exp[T]
  )(implicit ctx: SrcCtx): Exp[T] = ident match {
    case Some(z) =>
      dbgs(c"Unrolling reduction tree with zero $z")
      val validInputs = inputs.zip(valids).map{case (in,v) => Math.math_mux(v, in, z) }
      Math.reduceTree(validInputs){(x: Exp[T], y: Exp[T]) => reduce(x,y) }

    case None =>
      // ASSUMPTION: If any values are invalid, they are at the end of the list (corresponding to highest index values)
      // TODO: This may be incorrect if we parallelize by more than the innermost iterator
      val inputsWithValid = inputs.zip(valids)
      dbgs("Unrolling reduction tree with " + inputsWithValid.length + " inputs: " + inputs.mkString(", "))
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
      dbgs(s"Inlining load function in reduce")
      val accValue = withSubstScope(load.input -> accum){ inroll(load) }
      val isFirst = Math.reduceTree(iters.zip(start).map{case (i,st) => FixPt.eql(i, st) }){(x,y) => Bit.and(x,y) }
      reduceType(accValue) = redType

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

    val accStore =  withSubstScope(store.inputB -> result, store.inputA -> accum){ inroll(store) } 
    reduceType(accStore) = redType
    inReduction(isInner){ accStore }
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
  )(implicit mT: Type[T], bT: Bits[T], ctx: SrcCtx): Exp[_] = {
    logs(s"Fully unrolling reduce $lhs")
    val lanes = FullUnroller(cchain, iters, isInnerControl(lhs))
    val mC = typ[Reg[T]]

    val blk = stageSealedLambda1(accum){
      val values = unrollMap(func, lanes)(mT, ctx)
      val valids = () => lanes.valids.map{vs => Math.reduceTree(vs){(a,b) => Bit.and(a,b) }}

      if (isOuterControl(lhs)) {
        dbgs("Fully unrolling outer reduce")
        val pipe = Pipe.op_unit_pipe(globalValids, () => {
          val foldValid = fold.map{_ => Bit.const(true) }
          val result = unrollReduceTree[T]((fold ++ values).toSeq, (foldValid ++ valids()).toSeq, ident, reduce.toFunction2)
          withSubstScope(store.inputB -> result){ inroll(store) }
        })
        styleOf(pipe) = SeqPipe
        levelOf(pipe) = InnerControl
      }
      else {
        dbgs("Fully unrolling inner reduce")
        val foldValid = fold.map{_ => Bit.const(true) }
        val result = unrollReduceTree[T]((fold ++ values).toSeq, (foldValid ++ valids()).toSeq, ident, reduce.toFunction2)
        withSubstScope(store.inputB -> result){ inroll(store) }
      }
      unit
    }
    val lhs2 = stageEffectful(UnitPipe(globalValids, blk), blk.effects)(ctx)
    transferMetadata(lhs -> lhs2)
    if (styleOf(lhs) != StreamPipe) styleOf(lhs2) = SeqPipe
    dbgs(c"Created unit pipe ${str(lhs2)}")
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
  )(implicit mT: Type[T], bT: Bits[T], ctx: SrcCtx): Exp[_] = {
    logs(s"Unrolling reduce $lhs -> $accum")
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
        dbgs("Unrolling unit pipe reduce")
        val pipe = Pipe.op_unit_pipe(globalValids, () => {
          unrollReduceAccumulate[T,Reg](accum, values, valids(), ident, fold, reduce, load, store, inds2.map(_.head), start, isInner = false)
        })
        styleOf(pipe) = SeqPipe
        levelOf(pipe) = InnerControl
      }
      else {
        dbgs("Unrolling inner reduce")
        unrollReduceAccumulate[T,Reg](accum, values, valids(), ident, fold, reduce, load, store, inds2.map(_.head), start, isInner = true)
      }
      unit
    }

    val effects = blk.effects
    val lhs2 = stageEffectful(UnrolledReduce(globalValids ++ en, cchain, blk, inds2, vs), effects.star)(ctx)
    transferMetadata(lhs, lhs2)
    accumulatesTo(lhs2) = accum
    logs(s"Created reduce ${str(lhs2)}")
    lhs2
  }

  def unrollReduceNode[T](lhs: Sym[_], rhs: OpReduce[T])(implicit ctx: SrcCtx): Exp[_] = {
    val OpReduce(en,cchain,accum,map,load,reduce,store,zero,fold,rV,iters) = rhs
    val accum2 = accumHack(accum, load)

    if (canFullyUnroll(cchain) && !spatialConfig.enablePIR) {
      fullyUnrollReduce[T](lhs, f(en), f(cchain), accum2, zero, fold, load, store, map, reduce, rV, iters)(rhs.mT, rhs.bT, ctx)
    }
    else {
      partiallyUnrollReduce[T](lhs, f(en), f(cchain), accum2, zero, fold, load, store, map, reduce, rV, iters)(rhs.mT, rhs.bT, ctx)
    }
  }

  /***************************/
  /** MEM-REDUCE  UNROLLING **/
  /***************************/

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
  )(implicit mT: Type[T], bT: Bits[T], mC: Type[C[T]], ctx: SrcCtx): Exp[_] = {
    logs(s"Unrolling accum-fold $lhs -> $accum")

    val mapLanes = PartialUnroller(cchainMap, itersMap, isInnerLoop = false)
    val isMap2 = mapLanes.indices
    val mvs = mapLanes.indexValids
    val start = counterStarts(cchainMap).map(_.getOrElse(int32s(0)))
    val redType = reduceType(reduce.result)
    val intermed = func.result

    val blk = stageSealedLambda1(accum) {
      logs(s"[Accum-fold $lhs] Unrolling map")
      unrollFor(func,mapLanes)
      val mems = mapLanes.map{_ => memories((intermed,0)) } // TODO: Just use the first duplicate always?

      val mvalids = () => mapLanes.valids.map{vs => Math.reduceTree(vs){(a,b) => Bit.and(a,b)} }

      if (isUnitCounterChain(cchainRed)) {
        logs(s"[Accum-fold $lhs] Unrolling unit pipe reduction")
        val rpipe = Pipe.op_unit_pipe(globalValids, () => {
          val values = inReduction(false){ mapLanes.map{_ => inroll(loadRes) } }
          val foldValue = if (fold) { Some( inroll(loadAcc) ) } else None
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
          //logs(c"  memories: $mems")

          val values: Seq[Seq[Exp[T]]] = inReduction(false){
            mapLanes.map{i =>
              withSubstScope(intermed -> mems(i)) {
                unrollMap(loadRes, reduceLanes)(mT, ctx)
              }
            }
          }

          logs(s"[Accum-fold $lhs] Unrolling accum loads")
          reduceLanes.foreach{p =>
            logs(s"Lane #$p")
            itersRed.foreach{i => logs(s"  $i -> ${f(i)}") }
          }

          val accValues = inReduction(false){ withSubstScope(loadAcc.input -> accum){
            unrollMap(loadAcc, reduceLanes)(mT,ctx)
          }}

          logs(s"[Accum-fold $lhs] Unrolling reduction trees and cycles")
          val results = reduceLanes.map{p =>
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

            register(reduce.result -> result)  // Lane-specific substitution

            tab -= 1
            result
          }

          logs(s"[Accum-fold $lhs] Unrolling accumulator store")
          // Use a default substitution for the reduction result to satisfy the block scheduler
          inReduction(false){ withSubstScope(storeAcc.inputA -> accum, reduce.result -> results.head){
            unrollMap(storeAcc, reduceLanes)
          }}
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
    val lhs2 = stageEffectful(UnrolledReduce(globalValids ++ en, cchainMap, blk, isMap2, mvs), effects.star)(ctx)
    transferMetadata(lhs, lhs2)
    accumulatesTo(lhs2) = accum

    logs(s"[Accum-fold] Created reduce ${str(lhs2)}")
    lhs2
  }

  def unrollMemReduceNode[T,C[T]](lhs: Sym[_], rhs: OpMemReduce[T,C])(implicit ctx: SrcCtx): Exp[_] = {
    val OpMemReduce(en,cchainMap,cchainRed,accum,func,loadRes,loadAcc,reduce,storeAcc,zero,fold,rV,itersMap,itersRed) = rhs
    val accum2 = accumHack(accum, loadAcc)

    unrollMemReduce(lhs,f(en),f(cchainMap),f(cchainRed),accum2,f(zero),fold,func,loadRes,loadAcc,reduce,storeAcc,rV,itersMap,itersRed)(rhs.mT,rhs.bT,rhs.mC,ctx)
  }

}
