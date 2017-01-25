package spatial.analysis

import org.virtualized.SourceContext
import argon.analysis._
import spatial._

// User-facing metadata
trait SpatialMetadataOps extends NameOps with IndexPatternOps { this: SpatialOps =>

  object bound {
    def update[T:Staged](x: T, value: Long): Unit = setBound(x, BigInt(value))
  }

  sealed abstract class ControlStyle
  case object InnerPipe  extends ControlStyle
  case object SeqPipe    extends ControlStyle
  case object MetaPipe   extends ControlStyle
  case object StreamPipe extends ControlStyle

  private[spatial] def setBound[T:Staged](x: T, value: BigInt): Unit
}

trait SpatialMetadataApi extends SpatialMetadataOps with NameApi with IndexPatternApi { this: SpatialApi => }


// Internal metadata (compiler use only)
trait SpatialMetadataExp extends SpatialMetadataOps with NameExp with IndexPatternExp { this: SpatialExp =>

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
  private[spatial] def setBound[T:Staged](x: T, value: BigInt): Unit = { boundOf(x.s) = value }
  private[spatial] def getBound(x: Exp[_]): Option[MBound] = x match {
    case Const(c: BigInt) => Some(Final(c))
    case Param(c: BigInt) => Some(Exact(c))
    case _ => metadata[MBound](x)
  }

  object Bound {
    def apply(value: BigInt) = MBound(value, isExact = false, isFinal = false)
    def unapply(x: Exp[_]): Option[BigInt] = boundOf.get(x)
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
  case class ControlType(style: ControlStyle) extends Metadata[ControlType] { def mirror(f:Tx) = this }
  object styleOf {
    def apply(x: Exp[_]): ControlStyle = styleOf.get(x).get
    def update(x: Exp[_], style: ControlStyle): Unit = metadata.add(x, ControlType(style))
    def get(x: Exp[_]): Option[ControlStyle] = metadata[ControlType](x).map(_.style)
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
  case class Children(children: Set[Exp[_]]) extends Metadata[Children] { def mirror(f:Tx) = Children(f.tx(children)) }
  object childrenOf {
    def apply(x: Exp[_]): Set[Exp[_]] = metadata[Children](x).map(_.children).getOrElse(Set.empty[Exp[_]])
    def update(x: Exp[_], children: Set[Exp[_]]) = metadata.add(x, Children(children))

    def apply(x: Ctrl): Set[Ctrl] = {
      val children = childrenOf(x.node).map{child => (child, false) }
      if (!x.isInner) children + ((x.node,true))
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


  case class ExternalReaders(readers: List[Exp[_]]) extends Metadata[ExternalReaders] {
    def mirror(f:Tx) = ExternalReaders(f.tx(readers))
  }
  object externalReadersOf {
    def apply(x: Exp[_]): List[Exp[_]] = metadata[ExternalReaders](x).map(_.readers).getOrElse(Nil)
    def update(x: Exp[_], readers: List[Exp[_]]) = metadata.add(x, ExternalReaders(readers))
  }

  case class ParFactor(factor: Const[Index]) extends Metadata[ParFactor] {
    def mirror(f:Tx) = ParFactor(f(factor).asInstanceOf[Const[Index]])
  }
  object parFactorOf {
    def apply(x: Exp[_]): Const[Index] = metadata[ParFactor](x).map(_.factor).getOrElse(int32(1))
    def update(x: Exp[_], factor: Const[Index]) = metadata.add(x, ParFactor(factor))
  }

  case class UnrollFactors(factors: Seq[Const[Index]]) extends Metadata[UnrollFactors] {
    def mirror(f:Tx) = UnrollFactors(factors.map{x => f(x).asInstanceOf[Const[Index]] })
  }
  object unrollFactorsOf {
    def apply(x: Exp[_]): Seq[Const[Index]] = metadata[UnrollFactors](x).map(_.factors).getOrElse(Nil)
    def update(x: Exp[_], factors: Seq[Const[Index]]) = metadata.add(x, UnrollFactors(factors))
  }

  case class MAccum(is: Boolean) extends Metadata[MAccum] { def mirror(f:Tx) = this }
  object isAccum {
    def apply(x: Exp[_]): Boolean = metadata[MAccum](x).exists(_.is)
    def update(x: Exp[_], is: Boolean) = metadata.add(x, MAccum(is))
  }
}
