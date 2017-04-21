package spatial.analysis

import argon.analysis._
import spatial._
import forge._
import org.virtualized.SourceContext

// User-facing metadata
trait SpatialMetadataApi { this: SpatialApi =>

  object bound {
    @api def update[T:Type](x: T, value: scala.Long): Unit = setBound(x, BigInt(value))
  }
}


// Internal metadata (compiler use only)
trait SpatialMetadataExp extends IndexPatternExp { this: SpatialExp =>
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

  object boundOf {
    def update(x: Exp[_], value: BigInt): Unit = metadata.add(x, Bound(value))
    def update(x: Exp[_], value: MBound): Unit = metadata.add(x, value)
    def update(x: Exp[_], value: Option[MBound]): Unit = value.foreach{v => boundOf(x) = v }
    def get(x: Exp[_]): Option[BigInt] = metadata[MBound](x).map(_.bound)
    def apply(x: Exp[_]): BigInt = boundOf.get(x).get
  }
  private[spatial] def setBound[T:Type](x: T, value: BigInt): Unit = { boundOf(x.s) = value }
  private[spatial] def getBound(x: Exp[_]): Option[MBound] = x match {
    case Const(c: BigDecimal) if c.isWhole => Some(Final(c.toBigInt))
    case Param(c: BigDecimal) if c.isWhole => Some(Exact(c.toBigInt))
    case _ => metadata[MBound](x)
  }

  object Bound {
    def apply(value: BigInt) = MBound(value, isExact = false, isFinal = false)
    def unapply(x: Exp[_]): Option[BigInt] = getBound(x) match {
      case Some(MBound(value, _, _)) => Some(value)
      case _ => None
    }
  }
  object Exact {
    def apply(value: BigInt) = MBound(value, isExact = true, isFinal = false)
    def unapply(x: Exp[_]): Option[BigInt] = getBound(x) match {
      case Some(MBound(value, true, _)) => Some(value)
      case _ => None
    }
  }
  object Final {
    def apply(value: BigInt) = MBound(value, isExact = true, isFinal = true)
    def unapply(x: Exp[_]): Option[BigInt] = getBound(x) match {
      case Some(MBound(value, _, true)) => Some(value)
      case _ => None
    }
  }

  object Bounded {
    def unapply(x: Exp[_]): Option[MBound] = getBound(x)
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
  object styleOf {
    def apply(x: Exp[_]): ControlStyle = styleOf.get(x).getOrElse{throw new UndefinedControlStyleException(x)}
    def update(x: Exp[_], style: ControlStyle): Unit = metadata.add(x, ControlType(style))
    def get(x: Exp[_]): Option[ControlStyle] = metadata[ControlType](x).map(_.style)
  }

  case class MControlLevel(level: ControlLevel) extends Metadata[MControlLevel] { def mirror(f:Tx) = this }
  object levelOf {
    def apply(x: Exp[_]): ControlLevel = levelOf.get(x).getOrElse{throw new UndefinedControlLevelException(x)}
    def update(x: Exp[_], level: ControlLevel): Unit = metadata.add(x, MControlLevel(level))
    def get(x: Exp[_]): Option[ControlLevel] = metadata[MControlLevel](x).map(_.level)
  }

  /**
    * Parameter Range
    * Tracks minimum, step, and maximum for a given design Param
    */
  case class ParamRange(min: Int, step: Int, max: Int) extends Metadata[ParamRange] { def mirror(f:Tx) = this }
  object domainOf {
    def apply(x: Param[Int32]): Option[(Int,Int,Int)] = metadata[ParamRange](x).map{d => (d.min,d.step,d.max) }
    def update(x: Param[Int32], rng: (Int,Int,Int)) = metadata.add(x, ParamRange(rng._1,rng._2,rng._3))
  }

  /**
    * Global values
    * Tracks values that are computed at most once (constants or outside all controllers)
    */
  case class MGlobal(isGlobal: Boolean) extends Metadata[MGlobal] { def mirror(f:Tx) = this }
  object isGlobal {
    def apply(x: Exp[_]): Boolean = x match {
      case Const(_) => true
      case Param(_) => true
      case _ => metadata[MGlobal](x).exists(_.isGlobal)
    }
    def update(x: Exp[_], global: Boolean) = metadata.add(x, MGlobal(global))
  }

  /**
    * Soft dimensions
    * Dimensions of offchip memories computed on host
    */
  case class SoftDims(dims: Seq[Exp[Index]]) extends Metadata[SoftDims] { def mirror(f:Tx) = SoftDims(f(dims)) }
  object softDimsOf {
    def apply(x: Exp[_]): Seq[Exp[Index]] = metadata[SoftDims](x).map(_.dims).getOrElse(Nil)
    def update(x: Exp[_], dims: Seq[Exp[Index]]) = metadata.add(x, SoftDims(dims))
  }

  def mirrorCtrl(x: Ctrl, f: Tx): Ctrl = (f(x.node), x.isInner)
  def mirrorAccess(x: Access, f: Tx): Access = (f(x.node), mirrorCtrl(x.ctrl, f))

  /**
    * List of readers for a given memory
    **/
  case class Readers(readers: List[Access]) extends Metadata[Readers] {
    def mirror(f:Tx) = Readers(readers.map(mirrorAccess(_,f)))
  }
  object readersOf {
    def apply(x: Exp[_]): List[Access] = metadata[Readers](x).map(_.readers).getOrElse(Nil)
    def update(x: Exp[_], readers: List[Access]) = metadata.add(x, Readers(readers))
  }

  /**
    * List of writers for a given memory
    **/
  case class Writers(writers: List[Access]) extends Metadata[Writers] {
    def mirror(f:Tx) = Writers(writers.map(mirrorAccess(_,f)))
  }
  object writersOf {
    def apply(x: Exp[_]): List[Access] = metadata[Writers](x).map(_.writers).getOrElse(Nil)
    def update(x: Exp[_], writers: List[Access]) = metadata.add(x, Writers(writers))
  }

  /**
    * Controller children
    * An unordered set of control nodes inside given (outer) control node.
    **/
  case class Children(children: List[Exp[_]]) extends Metadata[Children] { def mirror(f:Tx) = Children(f.tx(children)) }
  object childrenOf {
    def apply(x: Exp[_]): List[Exp[_]] = metadata[Children](x).map(_.children).getOrElse(Nil)
    def update(x: Exp[_], children: List[Exp[_]]) = metadata.add(x, Children(children))

    def apply(x: Ctrl): List[Ctrl] = {
      val children = childrenOf(x.node).map{child => (child, false) }
      if (!x.isInner) children :+ ((x.node,true))
      else children
    }
  }

  /**
    * Controller parent
    * Defines the controller which controls the reset of the given node.
    **/
  case class Parent(parent: Exp[_]) extends Metadata[Parent] { def mirror(f:Tx) = Parent(f(parent)) }
  object parentOf {
    def apply(x: Exp[_]): Option[Exp[_]] = metadata[Parent](x).map(_.parent)
    def update(x: Exp[_], parent: Exp[_]) = metadata.add(x, Parent(parent))

    def apply(x: Ctrl): Option[Ctrl] = if (x.isInner) Some((x.node, false)) else parentOf(x.node).map{x => (x,false)}
  }

  case class CtrlDeps(deps: Set[Exp[_]]) extends Metadata[CtrlDeps] { def mirror(f:Tx) = CtrlDeps(f.tx(deps)) }
  object ctrlDepsOf {
    def apply(x: Exp[_]): Set[Exp[_]] = metadata[CtrlDeps](x).map(_.deps).getOrElse(Set.empty)
    def update(x: Exp[_], deps: Set[Exp[_]]) = metadata.add(x, CtrlDeps(deps))
  }

  /**
    * List of memories written in a given controller
    **/
  case class WrittenMems(written: List[Exp[_]]) extends Metadata[WrittenMems] {
    def mirror(f:Tx) = WrittenMems(f.tx(written))
  }
  object writtenIn {
    def apply(x: Exp[_]): List[Exp[_]] = metadata[WrittenMems](x).map(_.written).getOrElse(Nil)
    def update(x: Exp[_], written: List[Exp[_]]): Unit = metadata.add(x, WrittenMems(written))

    def apply(x: Ctrl): List[Exp[_]] = writtenIn(x.node)
    def update(x: Ctrl, written: List[Exp[_]]): Unit = writtenIn(x.node) = written
  }

  /**
    * List of fifos or streams popped in a given controller, for handling streampipe control flow
    **/
  case class ListenStreams(listen: List[Exp[_]]) extends Metadata[ListenStreams] {
    def mirror(f:Tx) = ListenStreams(f.tx(listen))
  }
  object listensTo {
    def apply(x: Exp[_]): List[Exp[_]] = metadata[ListenStreams](x).map(_.listen).getOrElse(Nil)
    def update(x: Exp[_], listen: List[Exp[_]]): Unit = metadata.add(x, ListenStreams(listen))

    def apply(x: Ctrl): List[Exp[_]] = listensTo(x.node)
    def update(x: Ctrl, listen: List[Exp[_]]): Unit = listensTo(x.node) = listen
  }

  /**
    * Total latency of a controller
    **/

  case class AggregateLatency(latency: Int) extends Metadata[AggregateLatency] {
    def mirror(f:Tx) = this
  }
  object aggregateLatencyOf {
    def apply(x: Exp[_]): Int = metadata[AggregateLatency](x).map{a => a.latency}.getOrElse(0)
    def update(x: Exp[_], latency: Int): Unit = metadata.add(x, AggregateLatency(latency))
  }

  /**
    * Map for tracking which control nodes are the tile transfer nodes for a given memory, since this
    * alters the enable signal
    **/
  case class LoadMemCtrl(ctrl: List[Exp[_]]) extends Metadata[LoadMemCtrl] {
    def mirror(f:Tx) = LoadMemCtrl(f.tx(ctrl))
  }
  object loadCtrlOf {
    def apply(x: Exp[_]): List[Exp[_]] = metadata[LoadMemCtrl](x).map(_.ctrl).getOrElse(Nil)
    def update(x: Exp[_], ctrl: List[Exp[_]]): Unit = metadata.add(x, LoadMemCtrl(ctrl))

    def apply(x: Ctrl): List[Exp[_]] = loadCtrlOf(x.node)
    def update(x: Ctrl, ctrl: List[Exp[_]]): Unit = loadCtrlOf(x.node) = ctrl
  }

  /**
    * List of fifos or streams pushed in a given controller, for handling streampipe control flow
    **/
  case class PushStreams(push: List[Exp[_]]) extends Metadata[PushStreams] {
    def mirror(f:Tx) = PushStreams(f.tx(push))
  }
  object pushesTo {
    def apply(x: Exp[_]): List[Exp[_]] = metadata[PushStreams](x).map(_.push).getOrElse(Nil)
    def update(x: Exp[_], push: List[Exp[_]]): Unit = metadata.add(x, PushStreams(push))

    def apply(x: Ctrl): List[Exp[_]] = pushesTo(x.node)
    def update(x: Ctrl, push: List[Exp[_]]): Unit = pushesTo(x.node) = push
  }

  /**
    * Metadata for determining which memory duplicate(s) an access should correspond to.
    */
  case class ArgMap(argId: Int, memIdIn: Int, memIdOut: Int) extends Metadata[ArgMap] {
    def mirror(f:Tx) = this
  }
  object argMapping {
    private def get(arg: Exp[_]): Option[(Int,Int,Int)] = {
        Some(metadata[ArgMap](arg).map{a => (a.argId, a.memIdIn, a.memIdOut)}.getOrElse(-1,-1,-1))  
    }

    def apply(arg: Exp[_]): (Int,Int,Int) = argMapping.get(arg).get

    def update(arg: Exp[_], id: (Int, Int,Int) ): Unit = metadata.add(arg, ArgMap(id._1, id._2, id._3))

  }

  /**
    * Fringe that the StreamIn or StreamOut is associated with
    **/
  case class Fringe(fringe: Exp[_]) extends Metadata[Fringe] {
    def mirror(f:Tx) = Fringe(f(fringe))
  }
  object fringeOf {
    def apply(x: Exp[_]): Option[Exp[_]] = metadata[Fringe](x).map(_.fringe)
    def update(x: Exp[_], fringe: Exp[_]) = metadata.add(x, Fringe(fringe))
  }

  /**
    * List of consumers of reads (primarily used for register reads)
    */
  case class ReadUsers(users: List[Access]) extends Metadata[ReadUsers] {
    def mirror(f:Tx) = this
    override val ignoreOnTransform = true // Not necessarily reliably mirrorable
  }
  object usersOf {
    def apply(x: Exp[_]): List[Access] = metadata[ReadUsers](x).map(_.users).getOrElse(Nil)
    def update(x: Exp[_], users: List[Access]) = metadata.add(x, ReadUsers(users))
  }

  /**
    * Parallelization factor associated with a given loop index, prior to unrolling
    */
  case class ParFactor(factor: Const[Index]) extends Metadata[ParFactor] {
    def mirror(f:Tx) = ParFactor(f(factor).asInstanceOf[Const[Index]])
  }
  object parFactorOf {
    def apply(x: Exp[_]): Const[Index] = metadata[ParFactor](x).map(_.factor).getOrElse(int32(1))
    def update(x: Exp[_], factor: Const[Index]) = metadata.add(x, ParFactor(factor))
  }

  /**
    * Parallelization factors which a given node will be unrolled by, prior to unrolling
    */
  case class UnrollFactors(factors: Seq[Seq[Const[Index]]]) extends Metadata[UnrollFactors] {
    def mirror(f:Tx) = this // Don't mirror constants for now...
  }
  object unrollFactorsOf {
    def apply(x: Exp[_]): Seq[Seq[Const[Index]]] = metadata[UnrollFactors](x).map(_.factors).getOrElse(Nil)
    def update(x: Exp[_], factors: Seq[Seq[Const[Index]]]) = metadata.add(x, UnrollFactors(factors))
  }

  /**
    * Defines which unrolled duplicate the given symbol is on, for all levels of the control tree above it
    */
  case class UnrollNumbers(nums: Seq[Int]) extends Metadata[UnrollNumbers] { def mirror(f:Tx) = this }
  object unrollNumsFor {
    def apply(x: Exp[_]): Seq[Int] = metadata[UnrollNumbers](x).map(_.nums).getOrElse(Nil)
    def update(x: Exp[_], nums: Seq[Int]) = metadata.add(x, UnrollNumbers(nums))
  }

  /**
    * Identifies whether a memory is an accumulator
    */
  case class MAccum(is: Boolean) extends Metadata[MAccum] { def mirror(f:Tx) = this }
  object isAccum {
    def apply(x: Exp[_]): Boolean = metadata[MAccum](x).exists(_.is)
    def update(x: Exp[_], is: Boolean) = metadata.add(x, MAccum(is))
  }

  /**
    * Identifies whether a memory is an accumulator
    */
  case class MInnerAccum(is: Boolean) extends Metadata[MInnerAccum] { def mirror(f:Tx) = this }
  object isInnerAccum {
    def apply(x: Exp[_]): Boolean = metadata[MInnerAccum](x).exists(_.is)
    def update(x: Exp[_], is: Boolean) = metadata.add(x, MInnerAccum(is))
  }

  /**
    * Tracks start of address space of given DRAM
    */
  case class DRAMAddress(insts: Long) extends Metadata[DRAMAddress] { def mirror(f:Tx) = this }
  object dramAddr {
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

  object reduceType {
    def apply(e: Exp[_]): Option[ReduceFunction] = metadata[MReduceType](e).flatMap(_.func)
    def update(e: Exp[_], func: ReduceFunction) = metadata.add(e, MReduceType(Some(func)))
    def update(e: Exp[_], func: Option[ReduceFunction]) = metadata.add(e, MReduceType(func))
  }

  case class UnrolledResult(isIt: Boolean) extends Metadata[UnrolledResult] { def mirror(f:Tx) = this }
  object isReduceResult {
    def apply(e: Exp[_]) = metadata[UnrolledResult](e).exists(_.isIt)
    def update(e: Exp[_], isIt: Boolean) = metadata.add(e, UnrolledResult(isIt))
  }

  case class ReduceStarter(isIt: Boolean) extends Metadata[ReduceStarter] { def mirror(f:Tx) = this }
  object isReduceStarter {
    def apply(e: Exp[_]) = metadata[ReduceStarter](e).exists(_.isIt)
    def update(e: Exp[_], isIt: Boolean) = metadata.add(e, ReduceStarter(isIt))
  }

  case class PartOfTree(node: Exp[_]) extends Metadata[PartOfTree] { def mirror(f:Tx) = this }
  object rTreeMap {
    // FIXME: The return type of this is Object (join of Exp[_] and Nil) -- maybe want an Option here? Or .get?
    def apply(e: Exp[_]) = metadata[PartOfTree](e).map(_.node).getOrElse(Nil)
    def update(e: Exp[_], node: Exp[_]) = metadata.add(e, PartOfTree(node))
  }


  case class MShouldDuplicate(should: Boolean) extends Metadata[MShouldDuplicate] { def mirror(f:Tx) = this }
  object shouldDuplicate {
    def apply(e: Exp[_]) = metadata[MShouldDuplicate](e).exists(_.should)
    def update(e: Exp[_], should: Boolean) = metadata.add(e, MShouldDuplicate(should))
  }

}
