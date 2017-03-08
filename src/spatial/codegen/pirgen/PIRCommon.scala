package spatial.codegen.pirgen

import scala.collection.mutable

// Common PIR operations (which don't need Spatial IR mixin)
trait PIRCommon extends PIR {
  private def collectX[T](a: Any)(func: Any => Set[T]): Set[T] = a match {
    case cu: ComputeUnit => func(cu.allStages) ++ func(cu.cchains) ++ func(cu.srams)

    case cchain: CChainInstance => func(cchain.counters)
    case cchain: CChainCopy => func(cchain.inst)
    case cchain: UnitCChain => Set.empty

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
  def localScalar(x:Any):LocalScalar = x match {
    case x: ConstReg => x
    case x: MemLoadReg => x
    case x => throw new Exception(s"Cannot use $x as a LocalMem")
  }
  def globalInputs(a: Any): Set[GlobalBus] = a match {
    case glob: GlobalBus => Set(glob)
    case ScalarIn(in) => Set(in)
    case VectorIn(in) => Set(in)
    case mem: CUMemory => globalInputs(mem.readAddr ++ mem.writeAddr ++ mem.writeStart ++ mem.writeEnd ++ mem.writePort ++ mem.readPort)
    case counter: CUCounter => globalInputs(List(counter.start, counter.end, counter.stride))
    case stage:Stage => globalInputs(stage.inputMems)
    case _ => collectX[GlobalBus](a)(globalInputs)
  }
  def globalOutputs(a: Any): Set[GlobalBus] = a match {
    case glob: GlobalBus => Set(glob)
    case ScalarOut(out) => Set(out)
    case VectorOut(out) => Set(out)
    case stage: Stage => globalOutputs(stage.outputMems)
    case _ => collectX[GlobalBus](a)(globalOutputs)
  }

  def scalarInputs(a: Any): Set[ScalarBus]  = globalInputs(a).collect{case x: ScalarBus => x}
  def scalarOutputs(a: Any): Set[ScalarBus] = globalOutputs(a).collect{case x: ScalarBus => x}

  def vectorInputs(a: Any): Set[VectorBus]  = globalInputs(a).collect{case x: VectorBus => x}
  def vectorOutputs(a: Any): Set[VectorBus] = globalOutputs(a).collect{case x: VectorBus => x}

  def usedCChains(a: Any): Set[CUCChain] = a match {
    case cu: ComputeUnit => usedCChains(cu.allStages) ++ usedCChains(cu.srams)

    case stage: Stage => stage.inputMems.collect{case CounterReg(cchain,_) => cchain}.toSet
    case sram: CUMemory =>
      (sram.readAddr.collect{case CounterReg(cchain,_) => cchain} ++
       sram.writeAddr.collect{case CounterReg(cchain,_) => cchain} ++
       sram.swapRead ++ sram.swapWrite ++ sram.writeCtrl).toSet

    case iter: Iterator[Any] => iter.flatMap(usedCChains).toSet
    case iter: Iterable[Any] => usedCChains(iter.iterator)
    case _ => Set.empty
  }

  def isReadable(x: LocalComponent): Boolean = x match {
    case _:ScalarOut | _:VectorOut => false
    case _:ScalarIn  | _:VectorIn  => true
    case _:MemLoadReg => true
    case _:TempReg | _:AccumReg | _:ReduceReg => true
    case _:WriteAddrWire | _:ReadAddrWire | _:FeedbackAddrReg | _:FeedbackDataReg => false
    case _:ControlReg => true
    case _:ValidReg | _:ConstReg | _:CounterReg => true
  }
  def isWritable(x: LocalComponent): Boolean = x match {
    case _:ScalarOut | _:VectorOut => true
    case _:ScalarIn  | _:VectorIn  => false
    case _:MemLoadReg => false
    case _:TempReg | _:AccumReg | _:ReduceReg => true
    case _:WriteAddrWire | _:ReadAddrWire | _:FeedbackAddrReg | _:FeedbackDataReg => true
    case _:ControlReg => true
    case _:ValidReg | _:ConstReg | _:CounterReg => false
  }
  def isControl(x: LocalComponent): Boolean = x match {
    case _:ValidReg | _:ControlReg => true
    case _ => false
  }

  def isInterCU(x: GlobalBus): Boolean = x match {
    case _:PIRDRAMBus | _:InputArg | _:OutputArg => false
    case LocalVectorBus => false
    case _ => true
  }

}
