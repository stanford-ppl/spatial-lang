package spatial.codegen

import argon.core._
import argon.nodes._
import forge._
import spatial.aliases._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._
import org.virtualized.SourceContext

import scala.collection.mutable
import scala.collection.mutable.WrappedArray
import scala.reflect.runtime.universe.{Block => _, Type => _, _}
import scala.reflect.ClassTag

package object pirgen {
  type Expr = Exp[_]
  type CU = ComputeUnit

  val globals   = mutable.Set[GlobalComponent]()
  val metadatas = scala.collection.mutable.ListBuffer[MetadataMaps]()
  def cus = mappingOf.values.flatMap{cus => cus}.collect { case cu:CU => cu}.toList

  @stateful def isConstant(x: Expr):Boolean = x match {
    case Const(c) => true
    case Param(c) => true
    case Final(c) => true
    case _ => false
  }

  @stateful def getConstant(x: Expr): Option[AnyVal] = x match {
    case Const(c: FixedPoint) if c.fmt.isExactInt => Some(c.toInt)
    case Const(c: FixedPoint) => Some(c.toFloat)
    case Const(c: FloatPoint) => Some(c.toFloat)
    case Const(c: Boolean) => Some(c)

    case Param(c: FixedPoint) if c.fmt.isExactInt => Some(c.toInt)
    case Param(c: FixedPoint) => Some(c.toFloat)
    case Param(c: FloatPoint) => Some(c.toFloat)
    case Param(c: Boolean) => Some(c)

    case Final(c: BigInt)  => Some(c.toInt)
    case _ => None
  }

  @stateful def extractConstant(x: Expr): ConstReg[AnyVal] = getConstant(x) match {
    case Some(c) => ConstReg(c)
    case None => throw new Exception(s"Cannot allocate constant value for $x")
  }

  private def log[T](msg:String, logger:Option[PIRLogger] = None)(f: => T):T = {
    logger.fold(f) { _.dbgblk(msg) { f } }
  }

  private def visitDown(a: Any): Set[Any] = a match {
    case cu: ComputeUnit => (cu.allStages ++ cu.cchains ++ cu.mems ++ cu.fringeGlobals.values).toSet

    case cchain: CChainInstance => cchain.counters.toSet
    case cchain: CChainCopy => Set(cchain.inst)
    case cchain: UnitCChain => Set.empty

    case MemLoad(mem) => Set(mem)
    case CounterReg(counter, _, _) => Set(counter)
    case ControlIn(in) => Set(in)
    case ScalarIn(in) => Set(in)
    case VectorIn(in) => Set(in)
    case ControlOut(out) => Set(out)
    case ScalarOut(out) => Set(out)
    case VectorOut(out) => Set(out)

    case Some(x) => Set(x)
    case (data,addr,_) => Set(data, addr)
    case _ => Set.empty
  }

  def visitIn(a: Any): Set[Any] = a match {
    case counter: CUCounter => Set(counter.start, counter.end, counter.stride)
    case mem: CUMemory => (mem.writePort ++ mem.readPort.map { case (data, addr, _) => addr}).toSet
    case stage: Stage => stage.ins.toSet
    case a => visitDown(a)
  }

  def visitOut(a: Any): Set[Any] = a match {
    case stage: Stage => stage.outs.toSet
    case mem: CUMemory => mem.readPort.map { case (data, addr, _) => data}.toSet
    case a => visitDown(a)
  }

  def collectInput[T:ClassTag](x:Any, logger:Option[PIRLogger]=None):Set[T] = log(s"collectInput($x, ${x.getClass.getSimpleName})", logger) {
    x match {
      case m:T => Set(m)
      case iter: Iterator[_] => iter.flatMap(x => collectInput[T](x, logger)).toSet
      case iter: Iterable[_] => iter.flatMap(x => collectInput[T](x, logger)).toSet
      case _ => visitIn(x).flatMap(x => collectInput[T](x, logger))
    }
  }

  def collectOutput[T:ClassTag](x:Any, logger:Option[PIRLogger]=None):Set[T] = log(s"collectOutput($x, ${x.getClass.getSimpleName})", logger) {
    x match {
      case m:T => Set(m)
      case iter: Iterator[_] => iter.flatMap(x => collectOutput[T](x, logger)).toSet
      case iter: Iterable[_] => iter.flatMap(x => collectOutput[T](x, logger)).toSet
      case _ => visitOut(x).flatMap(x => collectOutput[T](x, logger))
    }
  }

  def isReadable(x: LocalComponent): Boolean = x match {
    case _:ScalarOut | _:VectorOut | _:ControlOut => false
    case _:ScalarIn  | _:VectorIn  | _:ControlIn => true
    case _:MemLoad| _:MemNumel => true
    case _:TempReg | _:AccumReg | _:ReduceReg => true
    case _:WriteAddrWire | _:ReadAddrWire => false
    case _:ControlReg => true
    case _:ValidReg | _:ConstReg[_] | _:CounterReg => true
  }
  def isWritable(x: LocalComponent): Boolean = x match {
    case _:ScalarOut | _:VectorOut | _:ControlOut => true
    case _:ScalarIn  | _:VectorIn  | _:ControlIn => false
    case _:MemLoad| _:MemNumel => false
    case _:TempReg | _:AccumReg | _:ReduceReg => true
    case _:WriteAddrWire | _:ReadAddrWire => true
    case _:ControlReg => true
    case _:ValidReg | _:ConstReg[_] | _:CounterReg => false
  }
  def isControl(x: LocalComponent): Boolean = x match {
    case _:ValidReg | _:ControlReg => true
    case _ => false
  }

  def isInterCU(x: GlobalBus): Boolean = x match {
    case _:InputArg | _:OutputArg => false
    case _ => true
  }

  def memRef(x: LocalComponent):Option[CUMemory] = x match {
    case MemLoad(mem) => Some(mem)
    case _ => None
  }

  @stateful def isReadInPipe(mem: Expr, pipe: Expr, reader: Option[Expr] = None): Boolean = {
    readersOf(mem).isEmpty || readersOf(mem).exists{read => reader.forall(_ == read.node) && read.ctrlNode == pipe }
  }
  @stateful def isWrittenInPipe(mem: Expr, pipe: Expr, writer: Option[Expr] = None): Boolean = {
    !isArgIn(mem) && (writersOf(mem).isEmpty || writersOf(mem).exists{write => writer.forall(_ == write.node) && write.ctrlNode == pipe })
  }
  @stateful def isWrittenByUnitPipe(mem: Expr): Boolean = {
    writersOf(mem).headOption.map{writer => isUnitPipe(writer.ctrlNode)}.getOrElse(true)
  }
  @stateful def isReadOutsidePipe(mem: Expr, pipe: Expr, reader: Option[Expr] = None): Boolean = {
    isArgOut(mem) || readersOf(mem).exists{read => reader.forall(_ == read.node) && read.ctrlNode != pipe }
  }

  def isBuffer(mem: Expr): Boolean = isSRAM(mem)

  @stateful def isGetDRAMAddress(mem:Expr) = mem match {
    case Def(_:GetDRAMAddress[_]) => true
    case _ => false
  }

  @stateful def isLocalMem(mem: Expr): Boolean = {
    var cond = isReg(mem) || isStreamIn(mem) || isStreamOut(mem) || isGetDRAMAddress(mem)
    //cond ||= isFIFO(mem) //TODO: if fifo only have a single reader then FIFO can also be localMem
    cond
  }

  def isRemoteMem(mem: Expr): Boolean = {
    var cond = isSRAM(mem)
    cond ||= isFIFO(mem) //TODO: if fifo only have a single reader then FIFO can also be localMem
    cond
  }

  @stateful def isMem(e: Expr):Boolean = isLocalMem(e) | isRemoteMem(e)

  @stateful def getMem(access:Expr) = access match {
    case ParLocalReader((mem, _, _)::_) => mem 
    case ParLocalWriter((mem, _, _, _)::_) => mem 
  }

  def isStage(d: Def): Boolean = d match {
    case _:CounterNew => false
    case _:CounterChainNew => false
    case _:RegNew[_] => false
    case _:SRAMNew[_,_] => false
    case _:FIFONew[_] => false
    case _:StreamInNew[_] => false
    case _:StreamOutNew[_] => false
    case _ => true
  }

  @stateful def isStage(e: Expr): Boolean = !isFringe(e) && !isControlNode(e) && getDef(e).exists(isStage)

  //Hack Check if func is inside block reduce
  def isBlockReduce(func: Block[Any]): Boolean = {
    func.effects.reads.intersect(func.effects.writes).exists(isSRAM)
  }

  @stateful def flattenNDAddress(addr: Exp[Any], dims: Seq[Exp[Index]]) = addr match {
    case Def(ListVector(List(Def(ListVector(indices))))) if indices.nonEmpty => flattenNDIndices(indices, dims)
    case Def(ListVector(indices)) if indices.nonEmpty => flattenNDIndices(indices, dims)
    case _ => throw new Exception(s"Unsupported address in PIR generation: $addr")
  }

  // returns (sym of flatten addr, List[Addr Stages])
  @stateful def flattenNDIndices(indices: Seq[Exp[Any]], dims: Seq[Exp[Index]]):(Expr, List[OpStage]) = {
    val cdims:Seq[Int] = dims.map{
      case Exact(d) => d.toInt
      case d => throw new Exception(s"Unable to get bound of memory size $d")
    }
    val strides:List[Expr] = List.tabulate(dims.length){ d =>
      if (d == dims.length - 1) int32s(1)
      else int32s(cdims.drop(d+1).product)
    }
    var partialAddr: Exp[Any] = indices.last
    var addrCompute: List[OpStage] = Nil
    for (i <- dims.length-2 to 0 by -1) { // If dims.length <= 1 this won't run
      val mul = OpStage(PIRFixMul, List(indices(i),strides(i)), fresh[Index])
      val add = OpStage(PIRFixAdd, List(mul.out, partialAddr),  fresh[Index])
      partialAddr = add.out
      addrCompute ++= List(mul,add)
    }
    (partialAddr, addrCompute)
  }

  def flattenND(inds:List[Int], dims:List[Int]):Int = { 
    if (inds.isEmpty && dims.isEmpty) 0 
    else { 
      val i::irest = inds
      val d::drest = dims
      assert(i < d && i >= 0, s"Index $i out of bound $d")
      i * drest.product + flattenND(irest, drest)
    }
  }

  def nodeToOp(node: Def): Option[PIROp] = node match {
    case Mux(_,_,_)                      => Some(PIRALUMux)
    case FixAdd(_,_)                     => Some(PIRFixAdd)
    case FixSub(_,_)                     => Some(PIRFixSub)
    case FixMul(_,_)                     => Some(PIRFixMul)
    case FixDiv(_,_)                     => Some(PIRFixDiv)
    case FixMod(_,_)                     => Some(PIRFixMod)
    case FixLt(_,_)                      => Some(PIRFixLt)
    case FixLeq(_,_)                     => Some(PIRFixLeq)
    case FixEql(_,_)                     => Some(PIRFixEql)
    case FixNeq(_,_)                     => Some(PIRFixNeq)
    case FixLsh(_,_)                     => Some(PIRFixSla)
    case FixRsh(_,_)                     => Some(PIRFixSra)
    case e: Min[_] if isFixPtType(e.mR)  => Some(PIRFixMin)
    case e: Max[_] if isFixPtType(e.mR)  => Some(PIRFixMax)
    case FixNeg(_)                       => Some(PIRFixNeg)

    // Float ops currently assumed to be single op
    case FltAdd(_,_)                     => Some(PIRFltAdd)
    case FltSub(_,_)                     => Some(PIRFltSub)
    case FltMul(_,_)                     => Some(PIRFltMul)
    case FltDiv(_,_)                     => Some(PIRFltDiv)
    case FltLt(_,_)                      => Some(PIRFltLt)
    case FltLeq(_,_)                     => Some(PIRFltLeq)
    case FltEql(_,_)                     => Some(PIRFltEql)
    case FltNeq(_,_)                     => Some(PIRFltNeq)
    case FltNeg(_)                       => Some(PIRFltNeg)

    case FltAbs(_)                       => Some(PIRFltAbs)
    case FltExp(_)                       => Some(PIRFltExp)
    case FltLog(_)                       => Some(PIRFltLog)
    case FltSqrt(_)                      => Some(PIRFltSqrt)
    case e: Min[_] if isFltPtType(e.mR)  => Some(PIRFltMin)
    case e: Max[_] if isFltPtType(e.mR)  => Some(PIRFltMax)

    case And(_,_)                        => Some(PIRBitAnd)
    case Or(_,_)                         => Some(PIRBitOr)
    case _                               => None
  }
  def typeToStyle(tpe: ControlStyle):CUStyle = tpe match {
    case InnerPipe      => PipeCU
    case MetaPipe       => MetaPipeCU
    case SeqPipe        => SequentialCU
    case StreamPipe     => StreamCU
    case ForkJoin       => throw new Exception(s"Do not support ForkJoin in PIR")
    case ForkSwitch     => throw new Exception("Do not support ForkSwitch in PIR")
  }

  // HACK Not used
  @stateful def bank(mem: Expr, access: Expr) = {
    val pattern = accessPatternOf(access).last
    val stride  = 1

    val pipe = parentOf(access).get
    val bankFactor = getInnerPar(pipe)

    // TODO: Distinguish isInner?
    val banking = pattern match {
      case AffineAccess(Exact(a),i,b) => Banking(a.toInt, bankFactor, true)
      case StridedAccess(Exact(a), i) => Banking(a.toInt, bankFactor, true)
      case OffsetAccess(i, b)         => Banking(1, bankFactor, true)
      case LinearAccess(i)            => Banking(1, bankFactor, true)
      case InvariantAccess(b)         => NoBanking(1)
      case RandomAccess               => NoBanking(1)
    }
    banking match {
      case Banking(stride,f,_) if f > 1  => Strided(stride, 16)
      case Banking(stride,f,_) if f == 1 => NoBanks
      case NoBanking(_) if bankFactor==1 => NoBanks
      case NoBanking(_)                  => Duplicated
    }
  }

  def mergeBanking(bank1: SRAMBanking, bank2: SRAMBanking) = (bank1,bank2) match {
    case (Strided(s1, b1),Strided(s2, b2)) if s1 == s2 && b1 == b2 => Strided(s1, b1)
    case (Strided(s1, b1),Strided(s2, b2)) => Diagonal(s1, s2)
    case (Duplicated, _) => Duplicated
    case (_, Duplicated) => Duplicated
    case (NoBanks, bank2) => bank2
    case (bank1, NoBanks) => bank1
  }

  @stateful def getInnerPar(n:Expr):Int = n match {
    case Def(Hwblock(func,_)) => 1
    case Def(UnitPipe(en, func)) => 1
    case Def(UnrolledForeach(en, cchain, func, iters, valids)) => 
      getConstant(parFactorsOf(cchain).last).get.asInstanceOf[Int]
    case Def(UnrolledReduce(en, cchain, accum, func, iters, valids)) =>
      getConstant(parFactorsOf(cchain).last).get.asInstanceOf[Int]
    case Def(FringeDenseLoad(dram, _, dataStream)) => getInnerPar(dataStream)
    case Def(FringeDenseStore(dram, _, dataStream, _)) => getInnerPar(dataStream)
    case Def(FringeSparseLoad(dram, _, dataStream)) => getInnerPar(dataStream)
    case Def(FringeSparseStore(dram, cmdStream, _)) => getInnerPar(cmdStream)
    case Def(Switch(body, selects, cases)) => 1 // Outer Controller
    case Def(SwitchCase(body)) => 1 
    case Def(d:StreamInNew[_]) => getInnerPar(readersOf(n).head.node)
    case Def(d:StreamOutNew[_]) => getInnerPar(writersOf(n).head.node)
    case Def(d:ParSRAMStore[_]) => getInnerPar(parentOf(n).get)
    case Def(d:ParSRAMLoad[_]) => getInnerPar(parentOf(n).get)
    case Def(d:ParFIFOEnq[_]) => getInnerPar(parentOf(n).get)
    case Def(d:ParFIFODeq[_]) => getInnerPar(parentOf(n).get)
    case Def(d:ParStreamRead[_]) => getInnerPar(parentOf(n).get)
    case Def(d:ParStreamWrite[_]) => getInnerPar(parentOf(n).get)
    case Def(d:ParFILOPush[_]) => getInnerPar(parentOf(n).get)
    case Def(d:ParFILOPop[_]) => getInnerPar(parentOf(n).get)
    case Def(_:SRAMLoad[_]) => 1 
    case Def(_:SRAMStore[_]) => 1 
    case Def(_:SRAMStore[_]) => 1 
    case Def(_:FIFOEnq[_]) => 1 
    case Def(_:FIFODeq[_]) => 1 
    case Def(_:StreamRead[_]) => 1 
    case Def(_:StreamWrite[_]) => 1 
    case Def(_:FILOPush[_]) => 1 
    case Def(_:FILOPop[_]) => 1 
    case Def(_:RegWrite[_]) => 1 
    case Def(_:RegRead[_]) => 1 
    case n if isArgIn(n) | isArgOut(n) | isGetDRAMAddress(n) => 1
    case n => throw new Exception(s"Undefined getInnerPar for $n")
  }

  @stateful def nIters(x: Expr, ignorePar: Boolean = false): Long = x match {
    case Def(CounterChainNew(ctrs)) =>
      val loopIters = ctrs.map{
        case Def(CounterNew(start,end,stride,par)) =>
          val min = boundOf.get(start).map(_.toDouble).getOrElse(0.0)
          val max = boundOf.get(end).map(_.toDouble).getOrElse(1.0)
          val step = boundOf.get(stride).map(_.toDouble).getOrElse(1.0)
          val p = boundOf.get(par).map(_.toDouble).getOrElse(1.0)
          dbg(s"nIter: bounds: min=$min, max=$max, step=$step, p=$p")

          val nIters = Math.ceil((max - min)/step)
          if (ignorePar) nIters.toLong else Math.ceil(nIters/p).toLong

        case Def(Forever()) => 0L
      }
      loopIters.fold(1L){_*_}
  }

  def nIters(x:CUCounter, ignorePar:Boolean) = {
    val CUCounter(ConstReg(start:Int), ConstReg(end:Int), ConstReg(stride:Int), par) = x
    val iters = Math.ceil((end - start)/stride)
    if (ignorePar) iters.toLong else Math.ceil(iters/par).toLong
  }

  @stateful def nIters(x:CChainInstance, ignorePar:Boolean): Long = {
    mappingOf.get(x).fold {
      x.counters.map(c => nIters(c, ignorePar)).product
    } { exp => nIters(exp) }
  }

  def getWriterCU(bus:GlobalBus, logger:Option[PIRLogger] = None):CU = log(s"getWriterCU($bus)", logger) {
    val writers = cus.filter { cu => collectOutput[GlobalBus](cu, logger).contains(bus) }
    assert(writers.size==1, s"writers of $bus = ${writers}.size != 1")
    writers.head
  }

  val times = scala.collection.mutable.Stack[Long]()
  def tic = {
    times.push(System.nanoTime())
  }
  def toc(unit:String):Double = {
    val startTime = times.pop()
    val endTime = System.nanoTime()
    val timeUnit = unit match {
      case "ns" => 1
      case "us" => 1000
      case "ms" => 1000000
      case "s" => 1000000000
      case _ => throw new Exception(s"Unknown time unit!")
    }
    (endTime - startTime) * 1.0 / timeUnit
  }

  def toc(info:String, unit:String):Unit = {
    val time = toc(unit)
    println(s"$info elapsed time: ${f"$time%1.3f"}$unit")
  }

}
