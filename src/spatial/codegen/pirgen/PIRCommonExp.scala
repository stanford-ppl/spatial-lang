package spatial.codegen.pirgen
import spatial.SpatialConfig
import spatial.SpatialExp
import spatial.analysis.SpatialMetadataExp
import org.virtualized.SourceContext

import scala.collection.mutable

// PIR operations which need the rest of the Spatial IR mixed in
trait PIRCommonExp extends PIRCommon with SpatialMetadataExp { self:SpatialExp =>
  type Expr = Exp[_]
  type CUControl = ControlType

  //def str(x: Expr) = x match {
    //case Deff(d) => s"$x = $d"
    //case _ => s"$x"
  //}
  override def isConstant(x:Expr):Boolean = x match {
    case Const(c) => true
    case Param(c) => true 
    case Final(c) => true 
    case _ => false 
  }

  override def extractConstant(x: Expr): String = x match {
    case Const(c: BigDecimal) if c.isWhole => s"${c}i"
    case Const(c: BigDecimal) => s"${c}f"
    case Const(c: Boolean) => s"${c}b"

    case Param(c: BigDecimal) if c.isWhole => s"${c}i"
    case Param(c: BigDecimal) => s"${c}f"

    // TODO: Not quite correct since bound is a double ??
    case Final(c) if (c.toInt == c)  => s"${c.toInt}i"
    case Final(c) if (c.toLong == c) => s"${c.toLong}l"
    case Final(c) if (c.toFloat == c) => s"${c.toFloat}f"
    case Final(c) => s"${c.toDouble}d"

    case _ => throw new Exception(s"Cannot allocate constant value for $x")
  }

  def isReadInPipe(mem: Expr, pipe: Expr, reader: Option[Expr] = None): Boolean = {
    readersOf(mem).isEmpty || readersOf(mem).exists{read => reader.forall(_ == read.node) && read.ctrlNode == pipe }
  }
  def isWrittenInPipe(mem: Expr, pipe: Expr, writer: Option[Expr] = None): Boolean = {
    !isArgIn(mem) && (writersOf(mem).isEmpty || writersOf(mem).exists{write => writer.forall(_ == write.node) && write.ctrlNode == pipe })
  }
  def isWrittenByUnitPipe(mem: Expr): Boolean = {
    writersOf(mem).headOption.map{writer => isUnitPipe(writer.ctrlNode)}.getOrElse(true)
  }
  def isReadOutsidePipe(mem: Expr, pipe: Expr, reader: Option[Expr] = None): Boolean = {
    isArgOut(mem) || readersOf(mem).exists{read => reader.forall(_ == read.node) && read.ctrlNode != pipe }
  }

  def isBuffer(mem: Expr): Boolean = isSRAM(mem)

  def isGetDRAMAddress(mem:Expr) = mem match {
    case Def(_:GetDRAMAddress[_]) => true
    case _ => false
  }

  def isLocalMem(mem: Expr): Boolean = isReg(mem) || isFIFO(mem) || isStreamIn(mem) || isStreamOut(mem) || isGetDRAMAddress(mem)

  def isRemoteMem(mem: Expr): Boolean = isSRAM(mem)

  def isMem(e: Expr):Boolean = isLocalMem(e) | isRemoteMem(e) 

  def isLocalMemReadAccess(acc:Expr) = acc match {
    case Def(_:RegRead[_]) => true
    case Def(_:FIFODeq[_]) => true
    case Def(_:ParFIFODeq[_]) => true
    case Def(_:StreamDeq[_]) => true
    case Def(_:ParStreamDeq[_]) => true
    case _ => false
  }

  def isLocalMemWriteAccess(acc:Expr) = acc match {
    case Def(_:RegWrite[_]) => true
    case Def(_:FIFOEnq[_]) => true
    case Def(_:ParFIFOEnq[_]) => true
    case Def(_:StreamEnq[_]) => true
    case Def(_:ParStreamEnq[_]) => true
    case _ => false
  }

  def isLocalMemAccess(acc: Expr) = acc match {
    case acc => isLocalMemReadAccess(acc) || isLocalMemWriteAccess(acc)
  }

  def isRemoteMemAccess(acc:Expr) = acc match {
    case Def(_:SRAMLoad[_]) => true
    case Def(_:ParSRAMLoad[_]) => true
    case Def(_:SRAMStore[_]) => true
    case Def(_:ParSRAMStore[_]) => true
    case _ => false
  }

  def isStage(d:Def):Boolean = d match {
    case d:CounterNew => false
    case d:CounterChainNew => false
    case d:RegNew[_] => false
    case d:SRAMNew[_] => false
    case d:FIFONew[_] => false
    case d:StreamInNew[_] => false
    case d:StreamOutNew[_] => false
    case _ => true
  }

  def isStage(e:Expr):Boolean = e match {
    case e if isFringe(e) => false
    case e if isControlNode(e) => false
    case Def(d) => isStage(d) 
    case _ => false
  }

  //Hack Check if func is inside block reduce
  def isBlockReduce(func:Block[Any]):Boolean = {
    func.summary.reads.intersect(func.summary.writes).filter(isSRAM).nonEmpty
  }

  def flattenNDAddress(addr: Exp[Any], dims: Seq[Exp[Index]]) = addr match {
    case Def(ListVector(List(Def(ListVector(indices))))) if indices.nonEmpty => flattenNDIndices(indices, dims)
    case Def(ListVector(indices)) if indices.nonEmpty => flattenNDIndices(indices, dims)
    case _ => throw new Exception(s"Unsupported address in PIR generation: $addr")
  }

  // returns (sym of flatten addr, List[Addr Stages])
  def flattenNDIndices(indices: Seq[Exp[Any]], dims: Seq[Exp[Index]]):(Expr, List[OpStage]) = {
    val cdims:Seq[Int] = dims.map{case Final(d) => d.toInt; case _ => throw new Exception("Unable to get bound of memory size") }
    val strides:List[Expr] = List.tabulate(dims.length){ d =>
      if (d == dims.length - 1) int32(1)
      else int32(cdims.drop(d+1).reduce(_*_))
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

  def fringeToMode(fringe:Expr):OffchipMemoryMode = fringe match {
    case Def(_:FringeDenseLoad[_]) => MemLoad
    case Def(_:FringeDenseStore[_]) => MemStore
    case Def(_:FringeSparseLoad[_]) => MemGather
    case Def(_:FringeSparseStore[_]) => MemScatter
    case _ => throw new Exception(s"Unknown type of fringe ${fringe}")
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
  }

  // HACK
  def bank(mem: Expr, access: Expr, isUnit: Boolean) = {
    val pattern = accessPatternOf(access).last
    val stride  = 1

    def bankFactor = if (isUnit) 1 else 16

    val banking = pattern match {
      case AffineAccess(Exact(a),i,b) => StridedBanking(a.toInt, bankFactor)
      case StridedAccess(Exact(a), i) => StridedBanking(a.toInt, bankFactor)
      case OffsetAccess(i, b)         => StridedBanking(1, bankFactor)
      case LinearAccess(i)            => StridedBanking(1, bankFactor)
      case InvariantAccess(b)         => NoBanking
      case RandomAccess               => NoBanking
    }
    banking match {
      case StridedBanking(stride,f) if f > 1  => Strided(stride)
      case StridedBanking(stride,f) if f == 1 => NoBanks
      case NoBanking if isUnit                => NoBanks
      case NoBanking                          => Duplicated
    }
  }

  /*def bank(mem: Expr, access: Expr, iter: Option[Expr]) = {
    //val indices = accessIndicesOf(access)
    val pattern = accessPatternOf(access)
    val strides = constDimsToStrides(dimsOf(mem).map{case Exact(d) => d.toInt})

    def bankFactor(i: Expr) = if (iter.isDefined && i == iter.get) 16 else 1

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
    case (Strided(s1),Strided(s2)) if s1 == s2 => Strided(s1)
    case (Strided(s1),Strided(s2)) => Diagonal(s1, s2)
    case (Duplicated, _) => Duplicated
    case (_, Duplicated) => Duplicated
    case (NoBanks, bank2) => bank2
    case (bank1, NoBanks) => bank1
  }
}
