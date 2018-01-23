package spatial.metadata

import argon.core._
import forge._
import spatial.aliases._
import spatial.utils._

/** User-facing metadata **/
/**************************/

/** Internal metadata **/
/***********************/

/**
  * Symbol bounds
  * Tracks the MAXIMUM value for a given symbol, along with data about this bound
  * - Final: final value for all future time (constants or finalized parameters)
  * - Exact: constant value but which MAY be changed (e.g. un-finalized parameters)
  * - Bound: any other upper bound
  * ASSUMPTION: Used only for non-negative size and index calculation
  **/
case class MBound(bound: BigInt, isExact: Boolean, isFinal: Boolean) extends Metadata[MBound] {
  def mirror(f:Tx) = this
  override def meet(that: MBound) = MBound(
    bound   = this.bound max that.bound,
    isExact = this.isExact && that.isExact && this.bound == that.bound,
    isFinal = this.isFinal && that.isFinal && this.bound == that.bound
  )
  override def isEmpiric = false
}

@data object boundOf {
  def update(x: Exp[_], value: BigInt): Unit = metadata.add(x, Bound(value))
  def update(x: Exp[_], value: MBound): Unit = metadata.add(x, value)
  def update(x: Exp[_], value: Option[MBound]): Unit = value.foreach{v => boundOf(x) = v }
  def get(x: Exp[_]): Option[BigInt] = Bound.get(x).map(_.bound)
  def apply(x: Exp[_]): BigInt = boundOf.get(x).get
}


@data object Bound {
  def apply(value: BigInt) = MBound(value, isExact = false, isFinal = false)
  def unapply(x: Exp[_]): Option[BigInt] = Bound.get(x) match {
    case Some(MBound(value, _, _)) => Some(value)
    case _ => None
  }

  def update[T:Type](x: T, value: scala.Long): Unit = set(x, BigInt(value))

  def set[T:Type](x: T, value: BigInt): Unit = { boundOf(x.s) = value }
  def get(x: Exp[_]): Option[MBound] = x match {
    case Param(c: FixedPoint) => Some(Exact(c.toBigInt))
    case Const(c: FixedPoint) => Some(Final(c.toBigInt))
    case _ => metadata[MBound](x)
  }
}
@data object Exact {
  def apply(value: BigInt) = MBound(value, isExact = true, isFinal = false)
  def unapply(x: Exp[_]): Option[BigInt] = Bound.get(x) match {
    case Some(MBound(value, true, _)) => Some(value)
    case _ => None
  }
}
@data object Final {
  def apply(value: BigInt) = MBound(value, isExact = true, isFinal = true)
  def unapply(x: Exp[_]): Option[BigInt] = Bound.get(x) match {
    case Some(MBound(value, _, true)) => Some(value)
    case _ => None
  }
}

@data object Bounded {
  def unapply(x: Exp[_]): Option[MBound] = Bound.get(x)
}


/**
  * Control Style
  * Used to track the scheduling type for controller nodes
  **/
sealed abstract class ControlStyle
case object InnerPipe  extends ControlStyle
case object SeqPipe    extends ControlStyle
case object MetaPipe   extends ControlStyle
case object StreamPipe extends ControlStyle
case object ForkJoin   extends ControlStyle
case object ForkSwitch extends ControlStyle

sealed abstract class ControlLevel
case object InnerControl extends ControlLevel
case object OuterControl extends ControlLevel

case class ControlType(style: ControlStyle) extends Metadata[ControlType] { def mirror(f:Tx) = this }
@data object styleOf {
  def apply(x: Exp[_]): ControlStyle = styleOf.get(x).getOrElse{throw new spatial.UndefinedControlStyleException(x)}
  def update(x: Exp[_], style: ControlStyle): Unit = metadata.add(x, ControlType(style))
  def get(x: Exp[_]): Option[ControlStyle] = metadata[ControlType](x).map(_.style)
  def set(x: Exp[_], style: ControlStyle): Unit = metadata.add(x, ControlType(style))
}

case class MControlLevel(level: ControlLevel) extends Metadata[MControlLevel] { def mirror(f:Tx) = this }
@data object levelOf {
  def apply(x: Exp[_]): ControlLevel = levelOf.get(x).getOrElse{throw new spatial.UndefinedControlLevelException(x)}
  def update(x: Exp[_], level: ControlLevel): Unit = metadata.add(x, MControlLevel(level))
  def get(x: Exp[_]): Option[ControlLevel] = metadata[MControlLevel](x).map(_.level)
}

/**
  * Parameter Range
  * Tracks minimum, step, and maximum for a given design Param
  */
case class ParamRange(min: Int, step: Int, max: Int) extends Metadata[ParamRange] { def mirror(f:Tx) = this }
@data object domainOf {
  def get(x: Param[Int32]): Option[(Int,Int,Int)] = metadata[ParamRange](x).map{d => (d.min,d.step,d.max) }
  def apply(x: Param[Int32]): (Int,Int,Int) = metadata[ParamRange](x).map{d => (d.min,d.step,d.max) }.getOrElse((1,1,1))
  def update(x: Param[Int32], rng: (Int,Int,Int)): Unit = metadata.add(x, ParamRange(rng._1,rng._2,rng._3))
}

/**
  * Global values
  * Tracks values that are computed at most once (constants or outside all controllers)
  */
case class MGlobal(isGlobal: Boolean) extends Metadata[MGlobal] { def mirror(f:Tx) = this }
@data object isGlobal {
  def apply(x: Exp[_]): Boolean = x match {
    case Const(_) => true
    case Param(_) => true
    case _ => metadata[MGlobal](x).exists(_.isGlobal)
  }
  def update(x: Exp[_], global: Boolean): Unit = metadata.add(x, MGlobal(global))
}

/**
  * Soft dimensions
  * Dimensions of offchip memories computed on host
  */
case class SoftDims(dims: Seq[Exp[Index]]) extends Metadata[SoftDims] { def mirror(f:Tx) = SoftDims(f(dims)) }
@data object softDimsOf {
  def apply(x: Exp[_]): Seq[Exp[Index]] = metadata[SoftDims](x).map(_.dims).getOrElse(Nil)
  def update(x: Exp[_], dims: Seq[Exp[Index]]): Unit = metadata.add(x, SoftDims(dims))
}

/**
  * List of readers for a given memory
  **/
case class Readers(readers: List[Access]) extends Metadata[Readers] {
  def mirror(f:Tx) = Readers(readers.map(mirrorAccess(_,f)))
}
@data object readersOf {
  def apply(x: Exp[_]): List[Access] = metadata[Readers](x).map(_.readers).getOrElse(Nil)
  def update(x: Exp[_], readers: List[Access]) = metadata.add(x, Readers(readers))
}

/**
  * List of writers for a given memory
  **/
case class Writers(writers: List[Access]) extends Metadata[Writers] {
  def mirror(f:Tx) = Writers(writers.map(mirrorAccess(_,f)))
}
@data object writersOf {
  def apply(x: Exp[_]): List[Access] = metadata[Writers](x).map(_.writers).getOrElse(Nil)
  def update(x: Exp[_], writers: List[Access]) = metadata.add(x, Writers(writers))
}

/**
  * List of resetters for a given memory
  **/
case class Resetters(resetters: List[Access]) extends Metadata[Resetters] {
  def mirror(f:Tx) = Resetters(resetters.map(mirrorAccess(_,f)))
}
@data object resettersOf {
  def apply(x: Exp[_]): List[Access] = metadata[Resetters](x).map(_.resetters).getOrElse(Nil)
  def update(x: Exp[_], resetters: List[Access]) = metadata.add(x, Resetters(resetters))
}

/**
  * Controller children
  * An unordered set of control nodes inside given (outer) control node.
  **/
case class Children(children: List[Exp[_]]) extends Metadata[Children] { def mirror(f:Tx) = Children(f.tx(children)) }
@data object childrenOf {
  def apply(x: Exp[_]): List[Exp[_]] = metadata[Children](x).map(_.children).getOrElse(Nil)
  def update(x: Exp[_], children: List[Exp[_]]) = metadata.add(x, Children(children))

  def apply(x: Ctrl): List[Ctrl] = {
    val children = childrenOf(x.node).map{child => (child, -1) }
    if (x.block < 0) addImplicitChildren(x, children)  // Include controllers without nodes (e.g. the reduction of a Reduce)
    else children
  }
}

/**
  * Controller parent
  * Defines the controller which controls the reset of the given node.
  **/
case class Parent(parent: Exp[_]) extends Metadata[Parent] { def mirror(f:Tx) = Parent(f(parent)) }
@data object parentOf {
  def apply(x: Exp[_]): Option[Exp[_]] = metadata[Parent](x).map(_.parent)
  def update(x: Exp[_], parent: Exp[_]) = metadata.add(x, Parent(parent))

  def apply(x: Ctrl): Option[Ctrl] = if (x.block >= 0) Some((x.node, -1)) else parentOf(x.node).map{x => (x,-1) }
}

case class CtrlDeps(deps: Set[Exp[_]]) extends Metadata[CtrlDeps] { def mirror(f:Tx) = CtrlDeps(f.tx(deps)) }
@data object ctrlDepsOf {
  def apply(x: Exp[_]): Set[Exp[_]] = metadata[CtrlDeps](x).map(_.deps).getOrElse(Set.empty)
  def update(x: Exp[_], deps: Set[Exp[_]]) = metadata.add(x, CtrlDeps(deps))
}

case class IndexController(ctrl: Ctrl) extends Metadata[IndexController] {
  def mirror(f:Tx) = this
  override def ignoreOnTransform: Boolean = true
}
@data object ctrlOf {
  def apply(x: Exp[_]): Option[Ctrl] = metadata[IndexController](x).map(_.ctrl)
  def update(x: Exp[_], ctrl: Ctrl): Unit = metadata.add(x, IndexController(ctrl))
}

case class IndexCounter(ctr: Exp[Counter]) extends Metadata[IndexCounter] {
  def mirror(f:Tx) = this
  override def ignoreOnTransform: Boolean = true
}
@data object ctrOf {
  def apply(x: Exp[_]): Option[Exp[Counter]] = metadata[IndexCounter](x).map(_.ctr)
  def update(x: Exp[_], ctr: Exp[Counter]): Unit = metadata.add(x, IndexCounter(ctr))
}

/**
  * List of memories written in a given controller
  **/
case class WrittenMems(written: List[Exp[_]]) extends Metadata[WrittenMems] {
  def mirror(f:Tx) = WrittenMems(f.tx(written))
}
@data object writtenIn {
  def apply(x: Exp[_]): List[Exp[_]] = metadata[WrittenMems](x).map(_.written).getOrElse(Nil)
  def update(x: Exp[_], written: List[Exp[_]]): Unit = metadata.add(x, WrittenMems(written))

  def apply(x: Ctrl): List[Exp[_]] = writtenIn(x.node)
  def update(x: Ctrl, written: List[Exp[_]]): Unit = writtenIn(x.node) = written
}

/**
  * List of fifos or streams popped in a given controller, for handling streampipe control flow
  **/
case class ListenStreams(listen: List[StreamInfo]) extends Metadata[ListenStreams] {
  def mirror(f:Tx) = ListenStreams(listen.map(mirrorStreamInfo(_,f)))
}
@data object listensTo {
  def apply(x: Exp[_]): List[StreamInfo] = metadata[ListenStreams](x).map(_.listen).getOrElse(Nil)
  def update(x: Exp[_], listen: List[StreamInfo]): Unit = metadata.add(x, ListenStreams(listen))

  def apply(x: Ctrl): List[StreamInfo] = listensTo(x.node)
  def update(x: Ctrl, listen: List[StreamInfo]): Unit = listensTo(x.node) = listen
}

/**
  * List of fifos or streams popped in a given controller, for handling streampipe control flow
  **/
case class AlignedTransfer(is: Boolean) extends Metadata[AlignedTransfer] {
  def mirror(f:Tx) = this
}
@data object isAligned {
  def apply(x: Exp[_]): Boolean = metadata[AlignedTransfer](x).exists(_.is)
  def update(x: Exp[_], is: Boolean) = metadata.add(x, AlignedTransfer(is))
}


/**
  * Map for tracking which control nodes are the tile transfer nodes for a given memory, since this
  * alters the enable signal
  **/
case class LoadMemCtrl(ctrl: List[Exp[_]]) extends Metadata[LoadMemCtrl] {
  def mirror(f:Tx) = LoadMemCtrl(f.tx(ctrl))
}
@data object loadCtrlOf {
  def apply(x: Exp[_]): List[Exp[_]] = metadata[LoadMemCtrl](x).map(_.ctrl).getOrElse(Nil)
  def update(x: Exp[_], ctrl: List[Exp[_]]): Unit = metadata.add(x, LoadMemCtrl(ctrl))

  def apply(x: Ctrl): List[Exp[_]] = loadCtrlOf(x.node)
  def update(x: Ctrl, ctrl: List[Exp[_]]): Unit = loadCtrlOf(x.node) = ctrl
}

/**
  * List of fifos or streams pushed in a given controller, for handling streampipe control flow
  **/
case class PushStreams(push: List[StreamInfo]) extends Metadata[PushStreams] {
  def mirror(f:Tx) = PushStreams(push.map(mirrorStreamInfo(_,f)))
}
@data object pushesTo {
  def apply(x: Exp[_]): List[StreamInfo] = metadata[PushStreams](x).map(_.push).getOrElse(Nil)
  def update(x: Exp[_], push: List[StreamInfo]): Unit = metadata.add(x, PushStreams(push))

  def apply(x: Ctrl): List[StreamInfo] = pushesTo(x.node)
  def update(x: Ctrl, push: List[StreamInfo]): Unit = pushesTo(x.node) = push
}

/**
  * Metadata for determining which memory duplicate(s) an access should correspond to.
  */
case class ArgMap(map: PortMap) extends Metadata[ArgMap] {
  def mirror(f:Tx) = this
}
@data object argMapping {
  private def get(arg: Exp[_]): Option[PortMap] = {
    Some(metadata[ArgMap](arg).map{a => (a.map.memId, a.map.argInId, a.map.argOutId)}.getOrElse(-1,-1,-1))
  }

  def apply(arg: Exp[_]): PortMap = argMapping.get(arg).get

  def update(arg: Exp[_], id: PortMap ): Unit = metadata.add(arg, ArgMap(id._1, id._2, id._3))

}

/**
  * Fringe that the StreamIn or StreamOut is associated with
  **/
case class Fringe(fringe: Exp[_]) extends Metadata[Fringe] {
  def mirror(f:Tx) = Fringe(f(fringe))
}
@data object fringeOf {
  def apply(x: Exp[_]): Option[Exp[_]] = metadata[Fringe](x).map(_.fringe)
  def update(x: Exp[_], fringe: Exp[_]) = metadata.add(x, Fringe(fringe))
}

/**
  * List of consumers of reads (primarily used for register reads)
  */
case class ReadUsers(users: Set[Access]) extends Metadata[ReadUsers] {
  def mirror(f:Tx) = this
  override val ignoreOnTransform = true // Not necessarily reliably mirrorable
}
@data object usersOf {
  def apply(x: Exp[_]): Set[Access] = metadata[ReadUsers](x).map(_.users).getOrElse(Set.empty)
  def update(x: Exp[_], users: Set[Access]) = metadata.add(x, ReadUsers(users))
}

/**
  * Parallelization factor associated with a given loop index, prior to unrolling
  */
case class ParFactor(factor: Const[Index]) extends Metadata[ParFactor] {
  def mirror(f:Tx) = ParFactor(f(factor).asInstanceOf[Const[Index]])
}
@data object parFactorOf {
  @internal def apply(x: Exp[_]): Const[Index] = metadata[ParFactor](x).map(_.factor).getOrElse(int32s(1))
  def update(x: Exp[_], factor: Const[Index]) = metadata.add(x, ParFactor(factor))
}

/**
  * Parallelization factors which a given node will be unrolled by, prior to unrolling
  */
case class UnrollFactors(factors: Seq[Seq[Const[Index]]]) extends Metadata[UnrollFactors] {
  def mirror(f:Tx) = this // Don't mirror constants for now...
}
@data object unrollFactorsOf {
  def apply(x: Exp[_]): Seq[Seq[Const[Index]]] = metadata[UnrollFactors](x).map(_.factors).getOrElse(Nil)
  def update(x: Exp[_], factors: Seq[Seq[Const[Index]]]) = metadata.add(x, UnrollFactors(factors))
}

/**
  * Defines which unrolled duplicate the given symbol is on, for all levels of the control tree above it
  */
case class UnrollNumbers(nums: Seq[Int]) extends Metadata[UnrollNumbers] { def mirror(f:Tx) = this }
@data object unrollNumsFor {
  def apply(x: Exp[_]): Seq[Int] = metadata[UnrollNumbers](x).map(_.nums).getOrElse(Nil)
  def update(x: Exp[_], nums: Seq[Int]) = metadata.add(x, UnrollNumbers(nums))
}

/**
  * Identifies whether a memory or associated write is an accumulator / accumulating write
  */
case class MTransferChannel(ch: Int) extends Metadata[MTransferChannel] { def mirror(f:Tx) = this }
@data object transferChannel {
  def apply(x: Exp[_]): Int = metadata[MTransferChannel](x).map(_.ch).getOrElse(-1)
  def update(x: Exp[_], ch: Int) = metadata.add(x, MTransferChannel(ch))
}

/**
  * Identifies whether a memory or associated write is an accumulator / accumulating write
  */
case class MAccum(is: Boolean) extends Metadata[MAccum] { def mirror(f:Tx) = this }
@data object isAccum {
  def apply(x: Exp[_]): Boolean = metadata[MAccum](x).exists(_.is)
  def update(x: Exp[_], is: Boolean) = metadata.add(x, MAccum(is))
}

/**
  * Identifies whether a writer to a linebuffer is one doing a transient load or a steady state load
  */
case class MTransientLoad(is: Boolean) extends Metadata[MTransientLoad] { def mirror(f:Tx) = this }
@data object isTransient {
  def apply(x: Exp[_]): Boolean = metadata[MTransientLoad](x).exists(_.is)
  def update(x: Exp[_], is: Boolean) = metadata.add(x, MTransientLoad(is))
}

/**
  * Identifies whether a memory is an accumulator
  */
case class MInnerAccum(is: Boolean) extends Metadata[MInnerAccum] { def mirror(f:Tx) = this }
@data object isInnerAccum {
  def apply(x: Exp[_]): Boolean = metadata[MInnerAccum](x).exists(_.is)
  def update(x: Exp[_], is: Boolean) = metadata.add(x, MInnerAccum(is))
}

/**
  * Tracks start of address space of given DRAM
  */
case class DRAMAddress(insts: Long) extends Metadata[DRAMAddress] { def mirror(f:Tx) = this }
@data object dramAddr {
  def apply(e: Exp[_]): Long = metadata[DRAMAddress](e).map(_.insts).getOrElse(0)
  def update(e: Exp[_], a: Long) = metadata.add(e, DRAMAddress(a))
}

/**
  * Identifies reduction function for an accumulation, if any
  */
sealed abstract class ReduceFunction
case object FixPtSum extends ReduceFunction
case object FltPtSum extends ReduceFunction
case object FixPtMin extends ReduceFunction
case object FixPtMax extends ReduceFunction
case object OtherReduction extends ReduceFunction

case class MReduceType(func: Option[ReduceFunction]) extends Metadata[MReduceType] { def mirror(f:Tx) = this }

@data object reduceType {
  def apply(e: Exp[_]): Option[ReduceFunction] = metadata[MReduceType](e).flatMap(_.func)
  def update(e: Exp[_], func: ReduceFunction) = metadata.add(e, MReduceType(Some(func)))
  def update(e: Exp[_], func: Option[ReduceFunction]) = metadata.add(e, MReduceType(func))
}

case class UnrolledResult(isIt: Boolean) extends Metadata[UnrolledResult] { def mirror(f:Tx) = this }
@data object isReduceResult {
  def apply(e: Exp[_]) = metadata[UnrolledResult](e).exists(_.isIt)
  def update(e: Exp[_], isIt: Boolean) = metadata.add(e, UnrolledResult(isIt))
}

case class ReduceStarter(isIt: Boolean) extends Metadata[ReduceStarter] { def mirror(f:Tx) = this }
@data object isReduceStarter {
  def apply(e: Exp[_]) = metadata[ReduceStarter](e).exists(_.isIt)
  def update(e: Exp[_], isIt: Boolean) = metadata.add(e, ReduceStarter(isIt))
}

case class PartOfTree(node: Exp[_]) extends Metadata[PartOfTree] { def mirror(f:Tx) = this }
@data object rTreeMap {
  // FIXME: The return type of this is Object (join of Exp[_] and Nil) -- maybe want an Option here? Or .get?
  def apply(e: Exp[_]) = metadata[PartOfTree](e).map(_.node).getOrElse(Nil)
  def update(e: Exp[_], node: Exp[_]) = metadata.add(e, PartOfTree(node))
}

/**
  * Flag for stateless nodes which require duplication for each use
  */
case class MShouldDuplicate(should: Boolean) extends Metadata[MShouldDuplicate] { def mirror(f:Tx) = this }
@data object shouldDuplicate {
  def apply(e: Exp[_]) = metadata[MShouldDuplicate](e).exists(_.should)
  def update(e: Exp[_], should: Boolean) = metadata.add(e, MShouldDuplicate(should))
}

/**
  * Latency of a given inner pipe body - used for control signal generation
  */
case class MBodyLatency(latency: Seq[Double]) extends Metadata[MBodyLatency] { def mirror(f:Tx) = this }
@data object bodyLatency {
  def apply(e: Exp[_]): Seq[Double] = metadata[MBodyLatency](e).map(_.latency).getOrElse(Nil)
  def update(e: Exp[_], latency: Seq[Double]): Unit = metadata.add(e, MBodyLatency(latency))
  def update(e: Exp[_], latency: Double): Unit = metadata.add(e, MBodyLatency(Seq(latency)))

  def sum(e: Exp[_]): Double = if (spatialConfig.enableRetiming || spatialConfig.enablePIRSim) bodyLatency(e).sum else 0L
}

/**
  * Gives the delay of the given symbol from the start of its parent controller
  */
case class MDelay(latency: Double) extends Metadata[MDelay] { def mirror(f:Tx) = this }
@data object symDelay {
  def apply(e: Exp[_]): Double = metadata[MDelay](e).map(_.latency).getOrElse(0L)
  def update(e: Exp[_], delay: Double): Unit = metadata.add(e, MDelay(delay))
}

/**
  * Flag for primitive nodes which are innermost loop invariant
  */
case class MLoopInvariant(is: Boolean) extends Metadata[MLoopInvariant] { def mirror(f:Tx) = this }
@data object isLoopInvariant {
  def apply(e: Exp[_]): Boolean = metadata[MLoopInvariant](e).exists(_.is)
  def update(e: Exp[_], is: Boolean): Unit = metadata.add(e, MLoopInvariant(is))
}

case class InitiationInterval(interval: Double) extends Metadata[InitiationInterval] { def mirror(f:Tx) = this }
@data object iiOf {
  def apply(e: Exp[_]): Double = metadata[InitiationInterval](e).map(_.interval).getOrElse(1)
  def update(e: Exp[_], interval: Double): Unit = metadata.add(e, InitiationInterval(interval))
}

case class UserII(interval: Double) extends Metadata[UserII] { def mirror(f:Tx) = this }
@data object userIIOf {
  def apply(e: Exp[_]): Option[Double] = metadata[UserII](e).map(_.interval)
  def update(e: Exp[_], interval: Option[Double]): Unit = interval.foreach{ii => metadata.add(e, UserII(ii)) }
}

case class MemoryContention(contention: Int) extends Metadata[MemoryContention] { def mirror(f:Tx) = this }
@data object contentionOf {
  def apply(e: Exp[_]): Int = metadata[MemoryContention](e).map(_.contention).getOrElse(1)
  def update(e: Exp[_], contention: Int): Unit = metadata.add(e, MemoryContention(contention))
}

case class MemoryDependencies(mems: Set[Exp[_]]) extends Metadata[MemoryDependencies] {
  def mirror(f:Tx) = this
  override def ignoreOnTransform: Boolean = true
}
@data object memDepsOf {
  def apply(e: Exp[_]): Set[Exp[_]] = metadata[MemoryDependencies](e).map(_.mems).getOrElse(Set.empty)
  def update(e: Exp[_], deps: Set[Exp[_]]): Unit = if (deps.nonEmpty) metadata.add(e, MemoryDependencies(deps))
}


/**
  * Data to be preloaded into SRAM
  */
case class InitialData(data: Seq[Exp[_]]) extends Metadata[InitialData] {
  def mirror(f:Tx) = InitialData(f.tx(data))
}
@data object initialDataOf {
  def apply(e: Exp[_]): Seq[Exp[_]] = metadata[InitialData](e).map(_.data).getOrElse(Nil)
  def update(e: Exp[_], data: Seq[Exp[_]]): Unit = metadata.add(e, InitialData(data))
}