package spatial.analysis

import spatial._
import org.virtualized.SourceContext

trait NodeClasses { this: SpatialExp =>

  /** Parallelization factors **/
  def parFactorsOf(x: Exp[_]): Seq[Const[Index]] = x match {
    case Op(CounterNew(start,end,step,par)) => List(par)
    case Op(Forever())             => List(int32(1))
    case Op(CounterChainNew(ctrs)) => ctrs.flatMap{ctr => parFactorsOf(ctr) }
    case Op(e: DenseTransfer[_,_]) => Seq(e.p)
    case Op(e: SparseTransfer[_])  => Seq(e.p)
    case _ => Nil
  }
  def parsOf(x: Exp[_]): Seq[Int] = parFactorsOf(x) map{case Const(p: BigDecimal) => p.toInt }

  /** Control Nodes **/
  type Ctrl = (Exp[_], Boolean)
  implicit class CtrlOps(x: Ctrl) {
    def node: Exp[_] = if (x == null) null else x._1
    def isInner: Boolean = if (x == null) false else x._2
  }

  def isControlNode(e: Exp[_]): Boolean = isPipeline(e) || isParallel(e) || isDRAMTransfer(e) || isSwitch(e) || isSwitchCase(e)
  def isOuterControl(e: Exp[_]): Boolean = isControlNode(e) && levelOf(e) == OuterControl
  def isInnerControl(e: Exp[_]): Boolean = isControlNode(e) && levelOf(e) == InnerControl
  def isPrimitiveControl(e: Exp[_]): Boolean = (isSwitch(e) || isSwitchCase(e)) && levelOf(e) == InnerControl

  def isOuterPipeline(e: Exp[_]): Boolean = isOuterControl(e) && isPipeline(e)
  def isInnerPipeline(e: Exp[_]): Boolean = isInnerControl(e) && isPipeline(e)

  def isOuterControl(e: Ctrl): Boolean = !e.isInner && isOuterControl(e.node)
  def isInnerControl(e: Ctrl): Boolean = e.isInner || isInnerControl(e.node)
  def isInnerPipeline(e: Ctrl): Boolean = e.isInner || isInnerPipeline(e.node)

  def isInnerPipe(e: Exp[_]): Boolean = styleOf(e) == InnerPipe || (styleOf(e) == MetaPipe && isInnerControl(e))
  def isInnerPipe(e: Ctrl): Boolean = e.isInner || isInnerPipe(e.node)
  def isMetaPipe(e: Exp[_]): Boolean = styleOf(e) == MetaPipe
  def isStreamPipe(e: Exp[_]): Boolean = {
    e match {
      case Def(Hwblock(_,isFrvr)) => isFrvr
      case _ => styleOf(e) == StreamPipe
    }
  }
  def isMetaPipe(e: Ctrl): Boolean = !e.isInner && isMetaPipe(e.node)
  def isStreamPipe(e: Ctrl): Boolean = !e.isInner && isStreamPipe(e.node)

  def isDRAMTransfer(e: Exp[_]): Boolean = getDef(e).exists(isDRAMTransfer)
  def isDRAMTransfer(d: Def): Boolean = d match {
    case _:DenseTransfer[_,_] => true
    case _:SparseTransfer[_]  => true
    case _ => false
  }

  def isSwitch(e: Exp[_]): Boolean = getDef(e).exists(isSwitch)
  def isSwitch(d: Def): Boolean = d match {
    case _:Switch[_] => true
    case _ => false
  }

  def isSwitchCase(e: Exp[_]): Boolean = getDef(e).exists(isSwitchCase)
  def isSwitchCase(d: Def): Boolean = d match {
    case _:SwitchCase[_] => true
    case _ => false
  }

  def isPipeline(e: Exp[_]): Boolean = getDef(e).exists(isPipeline)
  def isPipeline(d: Def): Boolean = d match {
    case _:Hwblock => true
    case _         => isUnitPipe(d) || isLoop(d)
  }

  def isUnitPipe(e: Exp[_]): Boolean = getDef(e).exists(isUnitPipe)
  def isUnitPipe(d: Def): Boolean = d match {
    case _:UnitPipe => true
    case _ => false
  }

  def isLoop(e: Exp[_]): Boolean = getDef(e).exists(isLoop)
  def isLoop(d: Def): Boolean = d match {
    case _:OpForeach           => true
    case _:OpReduce[_]         => true
    case _:OpMemReduce[_,_]    => true
    case _:UnrolledForeach     => true
    case _:UnrolledReduce[_,_] => true
    case _:StateMachine[_]     => true
    case _ => false
  }

  /** Determines if a given controller is forever or has any children that are **/
  def willRunForever(e: Exp[_]): Boolean = getDef(e).exists(isForever) || childrenOf(e).exists(willRunForever)

  /** Determines if just the given node is forever (has Forever counter) **/
  def isForever(e: Exp[_]): Boolean = getDef(e).exists(isForever)
  def isForever(d: Def): Boolean = d match {
    case e: Forever             => true
    case e: Hwblock             => e.isForever
    case e: OpForeach           => isForeverCounterChain(e.cchain)
    case e: OpReduce[_]         => isForeverCounterChain(e.cchain)
    case e: OpMemReduce[_,_]    => isForeverCounterChain(e.cchainMap) // This should probably never happen
    case e: UnrolledForeach     => isForeverCounterChain(e.cchain)
    case e: UnrolledReduce[_,_] => isForeverCounterChain(e.cchain)
    case _ => false
  }

  def isParallel(e: Exp[_]): Boolean = getDef(e).exists(isParallel)
  def isParallel(d: Def): Boolean = d match {
    case _:ParallelPipe => true
    case _ => false
  }

  def isFringeNode(e: Exp[_]): Boolean = getDef(e).exists(isFringeNode)
  def isFringeNode(d: Def): Boolean = d match {
    case _: FringeDenseLoad[_] => true
    case _: FringeDenseStore[_] => true
    case _: FringeSparseLoad[_] => true
    case _: FringeSparseStore[_] => true
    case _ => false
  }

  /** Allocations **/
  def stagedDimsOf(x: Exp[_]): Seq[Exp[Index]] = x match {
    case Def(BufferedOutNew(dims,_)) => dims
    case Def(LUTNew(dims,_)) => dims.map{d => int32(d)(x.ctx) }
    case Def(SRAMNew(dims)) => dims
    case Def(DRAMNew(dims,_)) => dims
    case Def(LineBufferNew(rows,cols)) => Seq(rows, cols)
    case Def(RegFileNew(dims)) => dims
    case _ => throw new UndefinedDimensionsError(x, None)(x.ctx)
  }

  def dimsOf(x: Exp[_]): Seq[Int] = x match {
    case Def(LUTNew(dims,_)) => dims
    case _ => stagedDimsOf(x).map{
      case Const(c: BigDecimal) => c.toInt
      case dim => throw new UndefinedDimensionsError(x, Some(dim))(x.ctx)
    }
  }

  def sizeOf(fifo: FIFO[_])(implicit ctx: SrcCtx): Index = wrap(sizeOf(fifo.s))
  def sizeOf(fifo: FILO[_])(implicit ctx: SrcCtx): Index = wrap(sizeOf(fifo.s))
  def sizeOf(x: Exp[_])(implicit ctx: SrcCtx): Exp[Index] = x match {
    case Def(FIFONew(size)) => size
    case Def(FILONew(size)) => size
    case _ => throw new UndefinedDimensionsError(x, None)
  }

  def lenOf(x: Exp[_])(implicit ctx: SrcCtx): Int = x.tp match {
    case tp: VectorType[_] => tp.width
    case _ => x match {
      case Def(ShiftRegNew(size, _)) => size
      case _ => throw new UndefinedDimensionsError(x, None)
    }
  }

  def rankOf(x: Exp[_]): Int = dimsOf(x).length
  def rankOf(x: MetaAny[_]): Int = rankOf(x.s)

  def isAllocation(e: Exp[_]): Boolean = getDef(e).exists(isAllocation)
  def isAllocation(d: Def): Boolean = d match {
    case _:RegNew[_]        => true
    case _:ArgInNew[_]      => true
    case _:ArgOutNew[_]     => true
    case _:SRAMNew[_,_]     => true
    case _:FIFONew[_]       => true
    case _:FILONew[_]       => true
    case _:LineBufferNew[_] => true
    case _:RegFileNew[_,_]  => true
    case _:LUTNew[_,_]      => true
    case _:DRAMNew[_,_]     => true
    case _:StreamInNew[_]   => true
    case _:StreamOutNew[_]  => true
    case _:BufferedOutNew[_] => true
    case _:Forever          => true
    case _ => isDynamicAllocation(d)
  }

  // Allocations which can depend on local, dynamic values
  def isDynamicAllocation(e: Exp[_]): Boolean = getDef(e).exists(isDynamicAllocation)
  def isDynamicAllocation(d: Def): Boolean = d match {
    case _:CounterNew      => true
    case _:CounterChainNew => true
    case _ => isPrimitiveAllocation(d)
  }

  // Dynamic allocations which can be directly used in primitive logic
  def isPrimitiveAllocation(e: Exp[_]): Boolean = getDef(e).exists(isPrimitiveAllocation)
  def isPrimitiveAllocation(d: Def): Boolean = d match {
    case _:StructAlloc[_]  => true
    case _:ListVector[_,_] => true
    case _ => false
  }

  def isDRAM(e: Exp[_]): Boolean = e.tp match {
    case _:DRAMType[_] => true
    case _ => false
  }

  def isSRAM(e: Exp[_]): Boolean = e.tp match {
    case _:SRAMType[_] => true
    case _ => false
  }

  def isReg(e: Exp[_]): Boolean = e.tp match {
    case _:RegType[_] => true
    case _ => false
  }

  def isHostIn(e: Exp[_]): Boolean = isHostIO(e) && writersOf(e).isEmpty
  def isHostOut(e: Exp[_]): Boolean = isHostIO(e) && readersOf(e).isEmpty

  def isFIFO(e: Exp[_]): Boolean = e.tp match {
    case _:FIFOType[_] => true
    case _ => false
  }

  def isFILO(e: Exp[_]): Boolean = e.tp match {
    case _:FILOType[_] => true
    case _ => false
  }

  def isLUT(e: Exp[_]): Boolean = e.tp match {
    case _:LUTType[_] => true
    case _ => false
  }

  def isStreamIn(e: Exp[_]): Boolean = e.tp match {
    case _:StreamInType[_] => true
    case _ => false
  }

  def isStreamOut(e: Exp[_]): Boolean = e.tp match {
    case _:StreamOutType[_] => true
    case _:BufferedOutType[_] => true
    case _ => false
  }
  def isBufferedOut(e: Exp[_]): Boolean = e.tp match {
    case _:BufferedOutType[_] => true
    case _ => false
  }

  def isStream(e: Exp[_]): Boolean = isStreamIn(e) || isStreamOut(e)

  def isStreamLoad(e: Exp[_]): Boolean = e match {
    case Def(_:FringeDenseLoad[_]) => true
    case _ => false
  }

  def isParEnq(e: Exp[_]): Boolean = e match {
    case Def(_:ParFIFOEnq[_]) => true
    case Def(_:ParFILOPush[_]) => true
    case Def(_:ParSRAMStore[_]) => true
    case Def(_:FIFOEnq[_]) => true
    case Def(_:FILOPush[_]) => true
    case Def(_:SRAMStore[_]) => true
    case Def(_:ParLineBufferEnq[_]) => true
    case _ => false
  }

  def isStreamStageEnabler(e: Exp[_]): Boolean = e match {
    case Def(_:FIFODeq[_]) => true
    case Def(_:ParFIFODeq[_]) => true
    case Def(_:FILOPop[_]) => true
    case Def(_:ParFILOPop[_]) => true
    case Def(_:StreamRead[_]) => true
    case Def(_:ParStreamRead[_]) => true
    case Def(_:DecoderTemplateNew[_]) => true
    case Def(_:DMATemplateNew[_]) => true 
    case _ => false
  }

  def isStreamStageHolder(e: Exp[_]): Boolean = e match {
    case Def(_:FIFOEnq[_]) => true
    case Def(_:ParFIFOEnq[_]) => true
    case Def(_:FILOPush[_]) => true
    case Def(_:ParFILOPush[_]) => true
    case Def(_:StreamWrite[_]) => true
    case Def(_:ParStreamWrite[_]) => true
    case Def(_:BufferedOutWrite[_]) => true
    case Def(_:DecoderTemplateNew[_]) => true
    case _ => false
  }

  def isLocalMemory(e: Exp[_]): Boolean = e.tp match {
    case _:SRAMType[_] | _:FIFOType[_] | _:FILOType[_] | _:RegType[_] | _:LineBufferType[_] | _:RegFileType[_] => true
    case _:LUTType[_] => true
    case _:StreamInType[_]  => true
    case _:StreamOutType[_] => true
    case _:BufferedOutType[_] => true
    case _ => false
  }

  def isOffChipMemory(e: Exp[_]): Boolean = e.tp match {
    case _:DRAMType[_]      => true
    case _:StreamInType[_]  => true
    case _:StreamOutType[_] => true
    case _:BufferedOutType[_] => true
    case _:RegType[_]       => isArgIn(e) || isArgOut(e) || isHostIO(e)
    case _ => false
  }

  def isVector(e:Exp[_]):Boolean = e.tp match {
    case _:VectorType[_] => true
    case _ => false
  }

  def isFringe(e:Exp[_]):Boolean = getDef(e).exists(isFringe)
  def isFringe(d:Def):Boolean = d match {
    case _:FringeDenseLoad[_] => true
    case _:FringeDenseStore[_] => true
    case _:FringeSparseLoad[_] => true
    case _:FringeSparseStore[_] => true
    case _ => false
  }

  /** Host Transfer **/

  def isTransfer(e: Exp[_]): Boolean = isTransferToHost(e) || isTransferFromHost(e)

  def isTransferToHost(e: Exp[_]): Boolean = getDef(e).exists(isTransferToHost)
  def isTransferToHost(d: Def): Boolean = d match {
    case _: GetMem[_] => true
    case _: GetArg[_] => true
    case _ => false
  }

  def isTransferFromHost(e: Exp[_]): Boolean = getDef(e).exists(isTransferFromHost)
  def isTransferFromHost(d: Def): Boolean = d match {
    case _: SetMem[_] => true
    case _: SetArg[_] => true
    case _ => false
  }

  /** Stateless Nodes **/
  def isRegisterRead(e: Exp[_]): Boolean = getDef(e).exists(isRegisterRead)
  def isRegisterRead(d: Def): Boolean = d match {
    case _:RegRead[_] => true
    case _ => false
  }

  // Nodes which operate on primitives but are allowed to appear outside inner controllers
  // Register reads are considered to be "stateless" because the read is itself akin to creating a wire
  // attached to the output of a register, not to the register itself
  def isStateless(e: Exp[_]): Boolean = getDef(e).exists(isStateless)
  def isStateless(d: Def): Boolean = d match {
    case _:RegRead[_] => true
    case _ => isDynamicAllocation(d)
  }

  /** Primitive Nodes **/
  def isPrimitiveNode(e: Exp[_]): Boolean = e match {
    case Const(_) => false
    case Param(_) => false
    case _        => !isControlNode(e) && !isAllocation(e) && !isStateless(e) && !isGlobal(e) && !isFringeNode(e)
  }

  /** Accesses **/
  type Access = (Exp[_], Ctrl)
  implicit class AccessOps(x: Access) {
    def node: Exp[_] = x._1
    def ctrl: Ctrl = x._2 // read or write enabler
    def ctrlNode: Exp[_] = x._2._1 // buffer control toggler
    def isInner: Boolean = x._2._2
  }

  // Memory, optional value, optional indices, optional enable
  type LocalWrite = (Exp[_], Option[Exp[_]], Option[Seq[Exp[Index]]], Option[Exp[Bool]])
  implicit class LocalWriteOps(x: LocalWrite) {
    def mem = x._1
    def data = x._2
    def addr = x._3
    def en = x._4
  }

  // Memory, optional indices, optional enable
  type LocalRead = (Exp[_], Option[Seq[Exp[Index]]], Option[Exp[Bool]])
  implicit class LocalReadOps(x: LocalRead) {
    def mem = x._1
    def addr = x._2
    def en = x._3
  }

  type LocalReset = (Exp[_], Option[Exp[Bool]])
  implicit class LocalResetOps(x: LocalReset) {
    def mem = x._1
    def en = x._2
  }

  private object LocalWrite {
    def apply(mem: Exp[_]): List[LocalWrite] = List( (mem, None, None, None) )
    def apply(mem: Exp[_], value: Exp[_] = null, addr: Seq[Exp[Index]] = null, en: Exp[Bool] = null) = {
      List( (mem, Option(value), Option(addr), Option(en)) )
    }
  }

  private object LocalRead {
    def apply(mem: Exp[_]): List[LocalRead] = List( (mem, None, None) )
    def apply(mem: Exp[_], addr: Seq[Exp[Index]] = null, en: Exp[Bool] = null): List[LocalRead] = {
      List( (mem, Option(addr), Option(en)) )
    }
  }

  private object LocalReset {
    def apply(mem: Exp[_]): List[LocalReset] = List( (mem, None) )
    def apply(mem: Exp[_], en: Exp[Bool] = null): List[LocalReset] = {
      List( (mem, Option(en)) )
    }
  }

  def writerUnapply(d: Def): Option[List[LocalWrite]] = d match {
    case RegWrite(reg,data,en)             => Some(LocalWrite(reg, value=data, en=en))
    case RegFileStore(reg,inds,data,en)    => Some(LocalWrite(reg, value=data, addr=inds, en=en))
    case SRAMStore(mem,_,inds,_,data,en)   => Some(LocalWrite(mem, value=data, addr=inds, en=en))
    case FIFOEnq(fifo,data,en)             => Some(LocalWrite(fifo, value=data, en=en))
    case FILOPush(filo,data,en)             => Some(LocalWrite(filo, value=data, en=en))

    case RegFileShiftIn(reg,is,d,data,en)  => Some(LocalWrite(reg, value=data, addr=is, en=en))
    case ParRegFileShiftIn(reg,is,d,data,en) => Some(LocalWrite(reg,value=data, addr=is, en=en))

    case LineBufferEnq(lb,data,en)         => Some(LocalWrite(lb, value=data, en=en))

    case e: DenseTransfer[_,_] if e.isLoad => Some(LocalWrite(e.local, addr=e.iters))
    case e: SparseTransfer[_]  if e.isLoad => Some(LocalWrite(e.local, addr=Seq(e.i)))

    case StreamWrite(stream, data, en)       => Some(LocalWrite(stream, value=data, en=en))
    case BufferedOutWrite(buffer,data,is,en) => Some(LocalWrite(buffer, value=data, addr=is, en=en))

    // TODO: Address and enable are in different format in parallelized accesses
    case ParStreamWrite(stream, data, ens) => Some(LocalWrite(stream))
    case ParLineBufferEnq(lb,data,ens)     => Some(LocalWrite(lb))
    case ParRegFileStore(reg,is,data,ens)  => Some(LocalWrite(reg))
    case ParSRAMStore(mem,addr,data,en)    => Some(LocalWrite(mem))
    case ParFIFOEnq(fifo,data,ens)         => Some(LocalWrite(fifo))
    case ParFILOPush(filo,data,ens)         => Some(LocalWrite(filo))
    case _ => None
  }
  def readerUnapply(d: Def): Option[List[LocalRead]] = d match {
    case RegRead(reg)                       => Some(LocalRead(reg))
    case RegFileLoad(reg,inds,en)           => Some(LocalRead(reg, addr=inds, en=en))
    case LUTLoad(lut,inds,en)               => Some(LocalRead(lut, addr=inds, en=en))
    case SRAMLoad(mem,dims,inds,ofs,en)     => Some(LocalRead(mem, addr=inds, en=en))
    case FIFODeq(fifo,en)                   => Some(LocalRead(fifo, en=en))
    case FILOPop(filo,en)                   => Some(LocalRead(filo, en=en))

    case LineBufferLoad(lb,row,col,en)      => Some(LocalRead(lb, addr=Seq(row,col), en=en))
    case LineBufferColSlice(lb,row,col,len) => Some(LocalRead(lb, addr=Seq(row,col)))
    case LineBufferRowSlice(lb,row,len,col) => Some(LocalRead(lb, addr=Seq(row,col)))

    case e: DenseTransfer[_,_] if e.isStore => Some(LocalRead(e.local, addr=e.iters))
    case e: SparseTransfer[_]  if e.isLoad  => Some(LocalRead(e.addrs))
    case e: SparseTransfer[_]  if e.isStore => Some(LocalRead(e.addrs) ++ LocalRead(e.local))

    case StreamRead(stream, en)              => Some(LocalRead(stream, en=en))

    // TODO: Address and enable are in different format in parallelized accesses
    case ParStreamRead(stream, ens)         => Some(LocalRead(stream))
    case ParLineBufferLoad(lb,row,col,ens)  => Some(LocalRead(lb))
    case ParRegFileLoad(reg,inds,ens)       => Some(LocalRead(reg))
    case ParSRAMLoad(sram,addr,ens)         => Some(LocalRead(sram))
    case ParFIFODeq(fifo,ens)               => Some(LocalRead(fifo))
    case ParFILOPop(filo,ens)               => Some(LocalRead(filo))
    case _ => None
  }

  def resetterUnapply(d: Def): Option[List[LocalReset]] = d match {
    case RegReset(reg, en)                       => Some(LocalReset(reg, en=en))
    case RegFileReset(reg, en)                       => Some(LocalReset(reg, en=en))
    case _ => None
  }

  object LocalWriter {
    def unapply(x: Exp[_]): Option[List[LocalWrite]] = getDef(x).flatMap(writerUnapply)
    def unapply(d: Def): Option[List[LocalWrite]] = writerUnapply(d)
  }
  object LocalResetter {
    def unapply(x: Exp[_]): Option[List[LocalReset]] = getDef(x).flatMap(resetterUnapply)
    def unapply(d: Def): Option[List[LocalReset]] = resetterUnapply(d)
  }
  object LocalReader {
    def unapply(x: Exp[_]): Option[List[LocalRead]] = getDef(x).flatMap(readerUnapply)
    def unapply(d: Def): Option[List[LocalRead]] = readerUnapply(d)
  }
  object LocalAccess {
    def unapply(x: Exp[_]): Option[List[Exp[_]]] = getDef(x).flatMap(LocalAccess.unapply)
    def unapply(d: Def): Option[List[Exp[_]]] = {
      val accessed = readerUnapply(d).map(_.map(_.mem)).getOrElse(Nil) ++
                     writerUnapply(d).map(_.map(_.mem)).getOrElse(Nil)
      if (accessed.isEmpty) None else Some(accessed)
    }
  }

  // Memory, optional value, optional indices, optional enable
  type ParLocalWrite = (Exp[_], Option[Seq[Exp[_]]], Option[Seq[Seq[Exp[Index]]]], Option[Seq[Exp[Bool]]])
  // Memory, optional indices, optional enable
  type ParLocalRead = (Exp[_], Option[Seq[Seq[Exp[Index]]]], Option[Seq[Exp[Bool]]])

  private object ParLocalWrite {
    def apply(mem: Exp[_]): List[ParLocalWrite] = List( (mem, None, None, None) )
    def apply(mem: Exp[_], value: Seq[Exp[_]] = null, addrs: Seq[Seq[Exp[Index]]] = null, ens: Seq[Exp[Bool]] = null) = {
      List( (mem, Option(value), Option(addrs), Option(ens)) )
    }
  }
  private object ParLocalRead {
    def apply(mem: Exp[_]): List[ParLocalRead] = List( (mem, None, None) )
    def apply(mem: Exp[_], addrs: Seq[Seq[Exp[Index]]] = null, ens: Seq[Exp[Bool]] = null): List[ParLocalRead] = {
      List( (mem, Option(addrs), Option(ens)) )
    }
  }
  def parWriterUnapply(d: Def): Option[List[ParLocalWrite]] = d match {
    //case BurstLoad(dram,fifo,ofs,_,_)       => Some(ParLocalWrite(fifo))
    case ParSRAMStore(mem,addrs,data,ens)     => Some(ParLocalWrite(mem, value=data, addrs=addrs, ens=ens))
    case ParFIFOEnq(fifo,data,ens)            => Some(ParLocalWrite(fifo, value=data, ens=ens))
    case ParFILOPush(filo,data,ens)            => Some(ParLocalWrite(filo, value=data, ens=ens))
    case ParStreamWrite(stream, data, ens)    => Some(ParLocalWrite(stream, value=data, ens=ens))
    case d => writerUnapply(d).map{writer => writer.map{
      case (mem, value, addr, en) => (mem, value.map{x => Seq(x)}, addr.map{a => Seq(a)}, en.map{e => Seq(e)})
    }}
  }
  def parReaderUnapply(d: Def): Option[List[ParLocalRead]] = d match {
    //case BurstStore(dram,fifo,ofs,_,_) => Some(ParLocalRead(fifo))
    case ParSRAMLoad(sram, addrs, ens)   => Some(ParLocalRead(sram, addrs=addrs, ens=ens))
    case ParFIFODeq(fifo, ens)           => Some(ParLocalRead(fifo, ens=ens))
    case ParFILOPop(filo, ens)           => Some(ParLocalRead(filo, ens=ens))
    case ParStreamRead(stream, ens)      => Some(ParLocalRead(stream, ens=ens))
    case d => readerUnapply(d).map{reader => reader.map{
      case (mem, addr, en) => (mem, addr.map{a => Seq(a)}, en.map{e => Seq(e) })
    }}
  }
  object ParLocalWriter {
    def unapply(x: Exp[_]): Option[List[ParLocalWrite]] = getDef(x).flatMap(parWriterUnapply)
    def unapply(d: Def): Option[List[ParLocalWrite]] = parWriterUnapply(d)
  }
  object ParLocalReader {
    def unapply(x: Exp[_]): Option[List[ParLocalRead]] = getDef(x).flatMap(parReaderUnapply)
    def unapply(d: Def): Option[List[ParLocalRead]] = parReaderUnapply(d)
  }

  def isReader(x: Exp[_]): Boolean = LocalReader.unapply(x).isDefined
  def isReader(d: Def): Boolean = readerUnapply(d).isDefined
  def isWriter(x: Exp[_]): Boolean = LocalWriter.unapply(x).isDefined
  def isWriter(d: Def): Boolean = writerUnapply(d).isDefined
  def isResetter(x: Exp[_]): Boolean = LocalResetter.unapply(x).isDefined
  def isResetter(d: Def): Boolean = resetterUnapply(d).isDefined
  def isAccess(x: Exp[_]): Boolean = isReader(x) || isWriter(x)
  def getAccess(x:Exp[_]):Option[Access] = x match {
    case LocalReader(reads) =>
      val ras = reads.flatMap{ case (mem, _, _) => readersOf(mem).filter { _.node == x } }
      assert(ras.size==1)
      Some(ras.head)
    case LocalWriter(writes) =>
      val was = writes.flatMap{ case (mem, _, _, _) => writersOf(mem).filter {_.node == x} }
      assert(was.size==1)
      Some(was.head)
    case LocalResetter(resetters) =>
      val ras = resetters.flatMap{ case (mem, _) => resettersOf(mem).filter {_.node == x} }
      assert(ras.size==1)
      Some(ras.head)
    case _ => None
  }
}
