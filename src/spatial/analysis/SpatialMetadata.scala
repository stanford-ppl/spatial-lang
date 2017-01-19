package spatial.analysis

import argon.metadata._
import spatial._

// User-facing metadata
trait SpatialMetadataOps extends NameOps { this: SpatialOps =>

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

trait SpatialMetadataApi extends SpatialMetadataOps with NameApi { this: SpatialApi => }


// Internal metadata (compiler use only)
trait SpatialMetadataExp extends SpatialMetadataOps with NameExp { this: SpatialExp =>

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
    def update(x: Exp[_], value: BigInt): Unit = metadata.add(x, MBound(value, isExact = false, isFinal = false))
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
    def apply(value: BigInt) = MBound(value, isExact = false, isFinal = true)
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
}
