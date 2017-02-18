package spatial.analysis

import spatial.SpatialExp
import org.virtualized.SourceContext

trait NodeClasses extends SpatialMetadataExp {
  this: SpatialExp =>

  /** Parallelization factors **/
  def parFactorsOf(x: Exp[_]): Seq[Const[Index]] = x match {
    case Op(CounterNew(start,end,step,par)) => List(par)
    case Op(Forever())             => List(int32(1))
    case Op(CounterChainNew(ctrs)) => ctrs.flatMap{ctr => parFactorsOf(ctr) }
    case Op(e: Gather[_])          => parFactorsOf(e.ctr)
    case Op(e: Scatter[_])         => parFactorsOf(e.ctr)
    case Op(e: BurstLoad[_])       => parFactorsOf(e.ctr)
    case Op(e: BurstStore[_])      => parFactorsOf(e.ctr)
    case Op(e: CoarseBurst[_,_])   => Seq(e.p)
    case _ => Nil
  }

  /** Control Nodes **/
  type Ctrl = (Exp[_], Boolean)
  implicit class CtrlOps(x: Ctrl) {
    def node: Exp[_] = x._1
    def isInner: Boolean = x._2
  }

  def isControlNode(e: Exp[_]): Boolean = isOuterControl(e) || isInnerControl(e)

  def isOuterControl(e: Exp[_]): Boolean = isOuterPipeline(e) || isParallel(e)
  def isInnerControl(e: Exp[_]): Boolean = isInnerPipeline(e) || isDRAMTransfer(e)
  def isOuterPipeline(e: Exp[_]): Boolean = isPipeline(e) && styleOf(e) != InnerPipe
  def isInnerPipeline(e: Exp[_]): Boolean = isPipeline(e) && styleOf(e) == InnerPipe

  def isOuterControl(e: Ctrl): Boolean = !e.isInner && isOuterControl(e.node)
  def isInnerControl(e: Ctrl): Boolean = e.isInner || isInnerControl(e.node)
  def isInnerPipeline(e: Ctrl): Boolean = e.isInner || isInnerPipeline(e.node)

  def isInnerPipe(e: Exp[_]): Boolean = styleOf(e) == InnerPipe
  def isInnerPipe(e: Ctrl): Boolean = e.isInner || isInnerPipe(e.node)
  def isMetaPipe(e: Exp[_]): Boolean = styleOf(e) == MetaPipe
  def isStreamPipe(e: Exp[_]): Boolean = styleOf(e) == StreamPipe
  def isMetaPipe(e: Ctrl): Boolean = !e.isInner && isMetaPipe(e.node)
  def isStreamPipe(e: Ctrl): Boolean = !e.isInner && isStreamPipe(e.node)

  def isDRAMTransfer(e: Exp[_]): Boolean = getDef(e).exists(isDRAMTransfer)
  def isDRAMTransfer(d: Def): Boolean = d match {
    case _:CoarseBurst[_,_] => true
    case _:BurstLoad[_]     => true
    case _:BurstStore[_]    => true
    case _:Gather[_]        => true
    case _:Scatter[_]       => true
    case _ => false
  }

  def isPipeline(e: Exp[_]): Boolean = getDef(e).exists(isPipeline)
  def isPipeline(d: Def): Boolean = d match {
    case _:Hwblock             => true
    case _:UnitPipe            => true
    case _:OpForeach           => true
    case _:OpReduce[_]         => true
    case _:OpMemReduce[_,_]    => true
    case _:UnrolledForeach     => true
    case _:UnrolledReduce[_,_] => true
    case _ => false
  }

  def isUnitPipe(e: Exp[_]): Boolean = getDef(e).exists(isUnitPipe)
  def isUnitPipe(d: Def): Boolean = d match {
    case _:UnitPipe            => true
    case _ => false
  }

  def isLoop(e: Exp[_]): Boolean = getDef(e).exists(isLoop)
  def isLoop(d: Def): Boolean = d match {
    case _:OpForeach           => true
    case _:OpReduce[_]         => true
    case _:OpMemReduce[_,_]    => true
    case _:UnrolledForeach     => true
    case _:UnrolledReduce[_,_] => true
    case _ => false
  }

  def isParallel(e: Exp[_]): Boolean = getDef(e).exists(isParallel)
  def isParallel(d: Def): Boolean = d match {
    case _:ParallelPipe => true
    case _ => false
  }

  /** Allocations **/
  def isAllocation(e: Exp[_]): Boolean = getDef(e).exists(isAllocation)
  def isAllocation(d: Def): Boolean = d match {
    case _:RegNew[_]       => true
    case _:ArgInNew[_]     => true
    case _:ArgOutNew[_]    => true
    case _:SRAMNew[_]      => true
    case _:FIFONew[_]      => true
    case _:DRAMNew[_]      => true
    case _:StreamInNew[_]  => true
    case _:StreamOutNew[_] => true
    case _:Forever         => true
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
    case _:StructAlloc[_] => true
    case _:ListVector[_]  => true
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

  def isFIFO(e: Exp[_]): Boolean = e.tp match {
    case _:FIFOType[_] => true
    case _ => false
  }

  def isLocalMemory(e: Exp[_]): Boolean = e.tp match {
    case _:SRAMType[_] | _:FIFOType[_] | _:RegType[_] => true
    case _ => false
  }

  def isOffChipMemory(e: Exp[_]): Boolean = e.tp match {
    case _:DRAMType[_]      => true
    case _:StreamInType[_]  => true
    case _:StreamOutType[_] => true
    case _:RegType[_]       => isArgIn(e) || isArgOut(e)
    case _ => false
  }

  def isVector(e:Exp[_]):Boolean = e.tp match {
    case _:VectorType[_] => true
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
    case _        => !isControlNode(e) && !isAllocation(e) && !isRegisterRead(e) && !isGlobal(e)
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
  // Memory, optional indices, optional enable
  type LocalRead = (Exp[_], Option[Seq[Exp[Index]]], Option[Exp[Bool]])

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

  def writerUnapply(d: Def): Option[List[LocalWrite]] = d match {
    case RegWrite(reg,data,en)                => Some(LocalWrite(reg, value=data, en=en))
    case SRAMStore(mem,dims,inds,ofs,data,en) => Some(LocalWrite(mem, value=data, addr=inds, en=en))
    case FIFOEnq(fifo,data,en)                => Some(LocalWrite(fifo, value=data, en=en))
    case Gather(dram,local,addrs,_,_)         => Some(LocalWrite(local))
    case e: CoarseBurst[_,_] if e.isLoad      => Some(LocalWrite(e.onchip, addr=e.iters))

    // TODO: Address and enable are in different format in parallelized accesses
    case BurstLoad(dram,fifo,ofs,_,_)         => Some(LocalWrite(fifo))
    case ParSRAMStore(mem,addr,data,en)       => Some(LocalWrite(mem,value=data))
    case ParFIFOEnq(fifo,data,ens)            => Some(LocalWrite(fifo,value=data))
    case _ => None
  }
  def readerUnapply(d: Def): Option[List[LocalRead]] = d match {
    case RegRead(reg)                  => Some(LocalRead(reg))
    case SRAMLoad(mem,dims,inds,ofs)   => Some(LocalRead(mem, addr=inds))
    case FIFODeq(fifo,en,_)            => Some(LocalRead(fifo, en=en))
    case Gather(dram,local,addrs,_,_)  => Some(LocalRead(addrs))
    case Scatter(dram,local,addrs,_,_) => Some(LocalRead(local) ++ LocalRead(addrs))
    case e: CoarseBurst[_,_] if !e.isLoad => Some(LocalRead(e.onchip, addr=e.iters))

    // TODO: Address and enable are in different format in parallelized accesses
    case BurstStore(dram,fifo,ofs,_,_) => Some(LocalRead(fifo))
    case ParSRAMLoad(sram,addr)        => Some(LocalRead(sram))
    case ParFIFODeq(fifo,ens,_)        => Some(LocalRead(fifo))
    case _ => None
  }

  object LocalWriter {
    def unapply(x: Exp[_]): Option[List[LocalWrite]] = getDef(x).flatMap(writerUnapply)
  }
  object LocalReader {
    def unapply(x: Exp[_]): Option[List[LocalRead]] = getDef(x).flatMap(readerUnapply)
  }

  def isReader(x: Exp[_]): Boolean = LocalReader.unapply(x).isDefined
  def isReader(d: Def): Boolean = readerUnapply(d).isDefined
  def isWriter(x: Exp[_]): Boolean = LocalWriter.unapply(x).isDefined
  def isWriter(d: Def): Boolean = writerUnapply(d).isDefined
  def isAccess(x: Exp[_]): Boolean = isReader(x) || isWriter(x)
}
