package spatial.codegen

import argon.core._
import argon.nodes._
import forge._
import spatial.aliases._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._
import org.virtualized.SourceContext
import spatial.SpatialConfig

package object pirgen {
  type Expr = Exp[_]
  type CU = ComputeUnit
  type PCU = PseudoComputeUnit
  type ACU = AbstractComputeUnit
  type CUControl = ControlType

  @stateful def isConstant(x: Expr):Boolean = x match {
    case Const(c) => true
    case Param(c) => true
    case Final(c) => true
    case _ => false
  }

  @stateful def getConstant(x: Expr): Option[AnyVal] = x match {
    case Const(c: BigDecimal) if c.isWhole => Some(c.toInt)
    case Const(c: BigDecimal) => Some(c.toFloat)
    case Const(c: Boolean) => Some(c)

    case Param(c: BigDecimal) if c.isWhole => Some(c.toInt)
    case Param(c: BigDecimal) => Some(c.toFloat  )
    case Param(c: Boolean) => Some(c)

    case Final(c: BigInt)  => Some(c.toInt)
    case _ => None
  }

  @stateful def extractConstant(x: Expr): ConstReg[AnyVal] = getConstant(x) match {
    case Some(c) => ConstReg(c)
    case None => throw new Exception(s"Cannot allocate constant value for $x")
  }

  private def collectX[T](a: Any)(func: Any => Set[T]): Set[T] = a match {
    case cu: ComputeUnit => func(cu.allStages) ++ func(cu.cchains) ++ func(cu.mems) ++ func(cu.fringeGlobals.values)

    case cchain: CChainInstance => func(cchain.counters)
    case cchain: CChainCopy => func(cchain.inst)
    case cchain: UnitCChain => Set.empty

    case Some(x) => func(x) // Why is this not matching on Iterable or Iterator?
    case iter: Iterator[_] => iter.flatMap(func).toSet
    case iter: Iterable[_] => func(iter.iterator)
    case _ => Set.empty
  }

  def localInputs(a: Any): Set[LocalComponent] = a match {
    case reg: LocalComponent => Set(reg)
    case mem: CUMemory => localInputs(mem.readAddr ++ mem.writeAddr ++ mem.writeStart ++ mem.writeEnd ++ mem.writePort ++ mem.readPort)
    case counter: CUCounter => localInputs(List(counter.start, counter.end, counter.stride))
    case stage: Stage => stage.inputMems.toSet
    case _ => collectX[LocalComponent](a)(localInputs)
  }

  def localOutputs(a: Any): Set[LocalComponent] = a match {
    case reg: LocalComponent => Set(reg)
    case stage: Stage => stage.outputMems.toSet
    case _ => collectX[LocalComponent](a)(localOutputs)
  }

  def globalInputs(a: Any): Set[GlobalBus] = a match {
    case _:LocalReadBus => Set.empty
    case glob: GlobalBus => Set(glob)
    case ScalarIn(in) => Set(in)
    case VectorIn(in) => Set(in)
    //case MemLoadReg(mem) => Set(LocalReadBus(mem))
    case mem: CUMemory => globalInputs(mem.writeStart ++ mem.writeEnd ++ mem.writePort)
    case counter: CUCounter => globalInputs(List(counter.start, counter.end, counter.stride))
    case stage:Stage => globalInputs(stage.inputMems)
    case _ => collectX[GlobalBus](a)(globalInputs)
  }
  def globalOutputs(a: Any): Set[GlobalBus] = a match {
    case _:LocalReadBus => Set.empty
    case glob: GlobalBus => Set(glob)
    case ScalarOut(out) => Set(out)
    case VectorOut(out) => Set(out)
    case mem: CUMemory => globalOutputs(mem.readPort)
    case stage: Stage => globalOutputs(stage.outputMems)
    case _ => collectX[GlobalBus](a)(globalOutputs)
  }

  def scalarInputs(a: Any): Set[ScalarBus]  = globalInputs(a).collect{case x: ScalarBus => x}
  def scalarOutputs(a: Any): Set[ScalarBus] = globalOutputs(a).collect{case x: ScalarBus => x}

  def vectorInputs(a: Any): Set[VectorBus]  = globalInputs(a).collect{case x: VectorBus => x}
  def vectorOutputs(a: Any): Set[VectorBus] = globalOutputs(a).collect{case x: VectorBus => x}

  def usedCChains(a: Any): Set[CUCChain] = a match {
    case cu: ComputeUnit => usedCChains(cu.allStages) ++ usedCChains(cu.mems)

    case stage: Stage => stage.inputMems.collect{case CounterReg(cchain,_) => cchain}.toSet
    case sram: CUMemory =>
      (sram.readAddr.collect{case CounterReg(cchain,_) => cchain} ++
        sram.writeAddr.collect{case CounterReg(cchain,_) => cchain}).toSet

    case iter: Iterator[Any] => iter.flatMap(usedCChains).toSet
    case iter: Iterable[Any] => usedCChains(iter.iterator)
    case _ => Set.empty
  }

  def usedMem(x:Any):Set[CUMemory] = x match {
    case MemLoadReg(mem) => Set(mem)
    case LocalReadBus(mem) => Set(mem)
    case x:CUMemory if x.mode == SRAMMode =>
      usedMem(x.readAddr ++ x.writeAddr ++ x.writeStart ++ x.writeEnd ++ x.writePort) + x
    case x:CUMemory => Set(x)
    case x:Stage => usedMem(x.inputMems)
    case x:CUCounter => usedMem(x.start) ++ usedMem(x.end) ++ usedMem(x.stride)
    case x:ComputeUnit if x.style.isInstanceOf[FringeCU] => usedMem(x.mems)
    case x:ComputeUnit => usedMem(x.allStages) ++ usedMem(x.cchains) ++ usedMem(x.srams)
    case _ => collectX[CUMemory](x)(usedMem)
  }

  def isReadable(x: LocalComponent): Boolean = x match {
    case _:ScalarOut | _:VectorOut => false
    case _:ScalarIn  | _:VectorIn  => true
    case _:MemLoadReg| _:MemNumel => true
    case _:TempReg | _:AccumReg | _:ReduceReg => true
    case _:WriteAddrWire | _:ReadAddrWire => false
    case _:ControlReg => true
    case _:ValidReg | _:ConstReg[_] | _:CounterReg => true
  }
  def isWritable(x: LocalComponent): Boolean = x match {
    case _:ScalarOut | _:VectorOut => true
    case _:ScalarIn  | _:VectorIn  => false
    case _:MemLoadReg| _:MemNumel => false
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
    case MemLoadReg(mem) => Some(mem)
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

  @stateful def isLocalMem(mem: Expr): Boolean = isReg(mem) || isFIFO(mem) || isStreamIn(mem) || isStreamOut(mem) || isGetDRAMAddress(mem)

  def isRemoteMem(mem: Expr): Boolean = isSRAM(mem)

  @stateful def isMem(e: Expr):Boolean = isLocalMem(e) | isRemoteMem(e)

  @stateful def isLocalMemReadAccess(acc: Expr) = acc match {
    case Def(_:RegRead[_]) => true
    case Def(_:FIFODeq[_]) => true
    case Def(_:ParFIFODeq[_]) => true
    case Def(_:StreamWrite[_]) => true
    case Def(_:ParStreamWrite[_]) => true
    case _ => false
  }

  @stateful def isLocalMemWriteAccess(acc: Expr) = acc match {
    case Def(_:RegWrite[_]) => true
    case Def(_:FIFOEnq[_]) => true
    case Def(_:ParFIFOEnq[_]) => true
    case Def(_:StreamRead[_]) => true
    case Def(_:ParStreamRead[_]) => true
    case _ => false
  }

  @stateful def isLocalMemAccess(acc: Expr) = isLocalMemReadAccess(acc) || isLocalMemWriteAccess(acc)

  @stateful def isRemoteMemAccess(acc:Expr) = acc match {
    case Def(_:SRAMLoad[_]) => true
    case Def(_:ParSRAMLoad[_]) => true
    case Def(_:SRAMStore[_]) => true
    case Def(_:ParSRAMStore[_]) => true
    case _ => false
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
      case Final(d) => d.toInt
      case Param(d:BigDecimal) => d.toInt
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

  @stateful def bank(dmem: Expr) = {
  }

  /*def bank(mem: Expr, access: Expr, iter: Option[Expr]) = {
    //val indices = accessIndicesOf(access)
    val pattern = accessPatternOf(access)
    val strides = constDimsToStrides(dimsOf(mem).map{case Exact(d) => d.toInt})

    def bankFactor(i: Expr) = if (iter.isDefined && i == iter.get) SpatialConfig.lanes else 1

    if (pattern.forall(_ == InvariantAccess)) NoBanks
    else {
      val ap = pattern.last
      val str = stride.last
      ap match {
        case AffineAccess(Exact(a),i,b) =>
      }

      (pattern.last, stride.last) match {
        case
      }
      val banking = (pattern, strides).zipped.map{case (pattern, stride) => pattern match {
        case AffineAccess(Exact(a),i,b) => StridedBanking(a.toInt*stride, bankFactor(i))
        case StridedAccess(Exact(a), i) => StridedBanking(a.toInt*stride, bankFactor(i))
        case OffsetAccess(i, b)         => StridedBanking(stride, bankFactor(i))
        case LinearAccess(i)            => StridedBanking(stride, bankFactor(i))
        case InvariantAccess(b)         => NoBanking
        case RandomAccess               => NoBanking
      }}

      val form = banking.find(_.banks > 1).getOrElse(NoBanking)

      form match {
        case StridedBanking(stride,_)    => Strided(stride)
        case NoBanking if iter.isDefined => Duplicated
        case NoBanking                   => NoBanks
      }
    }
  }*/
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
    case Def(Switch(body, selects, cases)) => getInnerPar(parentOf(n).get)
    case Def(SwitchCase(body)) => getInnerPar(parentOf(n).get)
    case Def(n:ParSRAMStore[_]) => n.ens.size
    case Def(n:ParSRAMLoad[_]) => n.ens.size
    case Def(n:ParFIFOEnq[_]) => n.ens.size
    case Def(n:ParFIFODeq[_]) => n.ens.size
    case Def(n:ParStreamRead[_]) => n.ens.size
    case Def(n:ParStreamWrite[_]) => n.ens.size
    case Def(n:ParFILOPush[_]) => n.ens.size
    case Def(n:ParFILOPop[_]) => n.ens.size
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
  }

  @stateful def parentOf(exp:Expr):Option[Expr] = {
    spatial.metadata.parentOf(exp).flatMap {
      case p@Def(_:Switch[_]) => parentOf(p)
      case p@Def(_:SwitchCase[_]) => parentOf(p)
      case p => Some(p)
    }
  }
}
