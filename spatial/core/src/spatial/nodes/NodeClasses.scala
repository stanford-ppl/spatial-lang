package spatial.nodes

import argon.core._
import argon.transform.SubstTransformer
import forge._
import spatial.aliases._
import spatial.utils._
import org.virtualized.EmptyContext
import spatial.aliases

/** Memory Allocations **/

abstract class Alloc[C:Type] extends Op[C]
abstract class DynamicAlloc[T:Type] extends Alloc[T]
abstract class PrimitiveAlloc[T:Type] extends DynamicAlloc[T]

/** Fringe **/

abstract class FringeNode[T:Type] extends Op[T]

/** Control Nodes **/

abstract class ControlNode[T:Type] extends Op[T]
abstract class DRAMTransfer extends ControlNode[MUnit]

abstract class EnabledControlNode extends ControlNode[Controller] {
  def en: Seq[Exp[Bit]]
  final def mirror(f:Tx): Exp[Controller] = mirrorWithEn(f, Nil)
  final def mirrorAndEnable(f: Tx, addEn: Seq[Exp[Bit]])(implicit state: State): Exp[Controller] = {
    this.IR = state
    mirrorWithEn(f, addEn)
  }
  def mirrorWithEn(f: Tx, addEn: Seq[Exp[Bit]]): Exp[Controller]
}

abstract class Pipeline extends EnabledControlNode
abstract class Loop extends Pipeline


/** Primitive Nodes **/

trait EnabledPrimitive[T] { this: Op[T] =>
  def enables: Seq[Exp[Bit]]

  /** Mirrors this node, also adding ANDs with the current enables and the given additional enable bit. **/
  def mirrorAndEnable(f: SubstTransformer, addEn: () => Exp[Bit])(implicit state: State): Exp[T] = {
    this.IR = state
    val en = addEn()
    val newEns: Seq[Exp[Bit]] = f(enables).map{Bit.and(_,en)(EmptyContext, state) }
    f.withSubstScope(enables.zip(newEns):_*){ this.mirror(f) }
  }
}

trait EnabledAccess[T] extends EnabledPrimitive[T] { this: Op[T] =>
  def accessWidth: Int = 1
  def address: Option[Seq[Exp[Index]]]
}
trait VectorAccess[T] extends EnabledAccess[T] { this: Op[T] =>
  def axis: Int
}

trait StreamAccess[T] extends EnabledAccess[T] { this: Op[T] => }

trait Reader[T] extends EnabledAccess[T] { this: Op[T] =>
  def localReads: Seq[LocalRead]
  override def enables: Seq[Exp[Bit]] = localReads.flatMap(_.en)
}
object Reader {
  @stateful def unapply(x: Exp[_]): Option[Seq[LocalRead]] = getDef(x).flatMap(Reader.unapply)
  def unapply(d: Def): Option[Seq[LocalRead]] = d match {
    case reader: Reader[_] if reader.localReads.nonEmpty => Some(reader.localReads)
    case _ => None
  }
}

trait Writer[T] extends EnabledAccess[T] { this: Op[T] =>
  def localWrites: Seq[LocalWrite]
  override def enables: Seq[Exp[Bit]] = localWrites.flatMap(_.en)
}
object Writer {
  @stateful def unapply(x: Exp[_]): Option[Seq[LocalWrite]] = getDef(x).flatMap(Writer.unapply)
  def unapply(d: Def): Option[Seq[LocalWrite]] = d match {
    case writer: Writer[_] if writer.localWrites.nonEmpty => Some(writer.localWrites)
    case _ => None
  }
}

trait VectorReader[T] extends Reader[T] with VectorAccess[T] { this: Op[T] => }
object VectorReader {
  @stateful def unapply(x: Exp[_]): Option[Seq[LocalRead]] = getDef(x).flatMap(VectorReader.unapply)
  def unapply(d: Def): Option[Seq[LocalRead]] = d match {
    case reader: VectorReader[_] if reader.localReads.nonEmpty => Some(reader.localReads)
    case _ => None
  }
}

trait VectorWriter[T] extends Writer[T] with VectorAccess[T] { this: Op[T] => }
object VectorWriter {
  @stateful def unapply(x: Exp[_]): Option[Seq[LocalWrite]] = getDef(x).flatMap(VectorWriter.unapply)
  def unapply(d: Def): Option[Seq[LocalWrite]] = d match {
    case writer: VectorWriter[_] if writer.localWrites.nonEmpty => Some(writer.localWrites)
    case _ => None
  }
}


trait StatusReader[T] { this: Op[T] =>
  def memory: Exp[_]
}
object StatusReader {
  @stateful def unapply(x: Exp[_]): Option[Exp[_]] = getDef(x).flatMap(StatusReader.unapply)
  def unapply(d: Def): Option[Exp[_]] = d match {
    case reader: StatusReader[_] => Some(reader.memory)
    case _ => None
  }
}

trait DequeueLike[T] extends Reader[T] with StreamAccess[T] { this: Op[T] => }
object DequeueLike {
  @stateful def unapply(x: Exp[_]): Option[Seq[LocalRead]] = getDef(x).flatMap(DequeueLike.unapply)
  def unapply(d: Def): Option[Seq[LocalRead]] = d match {
    case reader: DequeueLike[_] if reader.localReads.nonEmpty => Some(reader.localReads)
    case _ => None
  }
}

trait EnqueueLike[T] extends Writer[T] with StreamAccess[T] { this: Op[T] => }
object EnqueueLike {
  @stateful def unapply(x: Exp[_]): Option[Seq[LocalWrite]] = getDef(x).flatMap(EnqueueLike.unapply)
  def unapply(d: Def): Option[Seq[LocalWrite]] = d match {
    case reader: EnqueueLike[_] if reader.localWrites.nonEmpty => Some(reader.localWrites)
    case _ => None
  }
}


object LocalAccess {
  @stateful def unapply(x: Exp[_]): Option[Seq[Exp[_]]] = getDef(x).flatMap(LocalAccess.unapply)
  def unapply(d: Def): Option[Seq[Exp[_]]] = {
    val accessed = {
        Reader.unapply(d).map(reads => reads.map(_.mem)).getOrElse(Nil) ++
        Writer.unapply(d).map(writes => writes.map(_.mem)).getOrElse(Nil) ++
        DequeueLike.unapply(d).map(reads => reads.map(_.mem)).getOrElse(Nil)
    }
    if (accessed.isEmpty) None else Some(accessed)
  }
}


trait Resetter[T] extends EnabledPrimitive[T] { this: Op[T] =>
  def localReset: LocalReset
  override def enables: Seq[Exp[Bit]] = localReset.en.toSeq
}
object Resetter {
  @stateful def unapply(x: Exp[_]): Option[LocalReset] = getDef(x).flatMap(Resetter.unapply)
  def unapply(d: Def): Option[LocalReset] = d match {
    case resetter: Resetter[_] => Some(resetter.localReset)
    case _ => None
  }
}


abstract class EnabledOp[T:Type](ens: Exp[Bit]*) extends Op[T] with EnabledPrimitive[T] {
  override def enables: Seq[Exp[Bit]] = ens.toSeq
}
abstract class ReaderOp[T:Type:Bits,R:Type:Bits](
  mem:  Exp[_],
  addr: Seq[Exp[Index]] = null,
  en:   Exp[Bit] = null
) extends Op[R] with Reader[R] {
  final override def localReads: Seq[LocalRead] = LocalRead(mem,addr,en)
  val mT = typ[T]
  val bT = bits[T]
  override def address: Option[Seq[Exp[Index]]] = Option(addr)
}
abstract class WriterOp[T:Type:Bits](
  mem:  Exp[_],
  data: Exp[_] = null,
  addr: Seq[Exp[Index]] = null,
  en:   Exp[Bit] = null
) extends Op[MUnit] with Writer[MUnit] {
  final override def localWrites: Seq[LocalWrite] = LocalWrite(mem,data,addr,en)
  val mT = typ[T]
  val bT = bits[T]
  override def address: Option[Seq[Exp[Index]]] = Option(addr)
}

abstract class VectorReaderOp[T:Type:Bits](
  mem:  Exp[_],
  addr: Seq[Exp[Index]] = null,
  en:   Exp[Bit] = null,
  ax:   Int,                      // Dimension of vector read (e.g. for 2D, 0 = row slice, 1 = col slice)
  len:  Int                       // Length of vector slice
)(implicit vT: Type[VectorN[T]]) extends Op[VectorN[T]] with VectorReader[VectorN[T]] {
  final override def localReads: Seq[LocalRead] = LocalRead(mem, addr, en)
  override def accessWidth: Int = len
  val mT = typ[T]
  val bT = bits[T]
  override def address: Option[Seq[Exp[Index]]] = Option(addr)
  override def axis: Int = ax
}

abstract class VectorWriterOp[T:Type:Bits](
  mem:  Exp[_],
  data: Exp[Vector[T]],
  addr: Seq[Exp[Index]] = null,
  en:   Exp[Bit] = null,
  ax:   Int,
  len:  Int
) extends Op[MUnit] with VectorWriter[MUnit] {
  final override def localWrites: Seq[LocalWrite] = LocalWrite(mem,data,addr,en)
  override def accessWidth: Int = len
  val mT = typ[T]
  val bT = bits[T]
  override def address: Option[Seq[Exp[Index]]] = Option(addr)
  override def axis: Int = ax
}

abstract class DequeueLikeOp[T:Type:Bits,R:Type:Bits](
  mem:  Exp[_],
  addr: Seq[Exp[Index]] = null,
  en:   Exp[Bit] = null
) extends Op[R] with DequeueLike[R] {
  final override def localReads: Seq[LocalRead] = LocalRead(mem,addr,en)
  val mT = typ[T]
  val bT = bits[T]
  override def address: Option[Seq[Exp[Index]]] = Option(addr)
}

abstract class EnqueueLikeOp[T:Type:Bits](
  mem:  Exp[_],
  data: Exp[T],
  addr: Seq[Exp[Index]] = null,
  en:   Exp[Bit] = null
) extends Op[MUnit] with EnqueueLike[MUnit] {
  final override def localWrites: Seq[LocalWrite] = LocalWrite(mem,data,addr,en)
  val mT = typ[T]
  val bT = bits[T]
  override def address: Option[Seq[Exp[Index]]] = Option(addr)
}

abstract class VectorEnqueueLikeOp[T:Type:Bits](
  mem:  Exp[_],
  data: Exp[Vector[T]],
  addr: Seq[Exp[Index]] = null,
  en:   Exp[Bit] = null,
  ax:   Int
) extends Op[MUnit] with EnqueueLike[MUnit] with VectorWriter[MUnit] {
  final override def localWrites: Seq[LocalWrite] = LocalWrite(mem,data,addr,en)
  val mT = typ[T]
  val bT = bits[T]
  override def address: Option[Seq[Exp[Index]]] = Option(addr)
  override def axis: Int = ax
}


abstract class StatusReaderOp[T:Type:Bits,R:Type:Bits](
  mem: Exp[_]
) extends Op[R] with StatusReader[R] {
  final override def memory: Exp[_] = mem
  val mT = typ[T]
  val bT = bits[T]
}

abstract class ResetterOp(
  mem: Exp[_],
  en:  Exp[Bit] = null
) extends Op[MUnit] with Resetter[MUnit] {
  final override def localReset: LocalReset = LocalReset(mem,en=en)
}


/** Banked primitive nodes **/
trait BankedReader[T] extends EnabledAccess[T] { this: Op[T] =>
  def bankedReads: Seq[BankedRead]
  override def accessWidth: Int = enables.length
  override def enables: Seq[Exp[Bit]] = bankedReads.flatMap(_.ens.getOrElse(Nil))
  def address = None
}
object BankedReader {
  @stateful def unapply(x: Exp[_]): Option[Seq[BankedRead]] = getDef(x).flatMap(BankedReader.unapply)
  def unapply(d: Def): Option[Seq[BankedRead]] = d match {
    case reader: BankedReader[_] if reader.bankedReads.nonEmpty => Some(reader.bankedReads)
    case _ => None
  }
}


trait BankedWriter[T] extends EnabledAccess[T] { this: Op[T] =>
  def bankedWrites: Seq[BankedWrite]
  override def accessWidth: Int = enables.length
  override def enables: Seq[Exp[Bit]] = bankedWrites.flatMap(_.ens.getOrElse(Nil))
  def address = None
}
object BankedWriter {
  @stateful def unapply(x: Exp[_]): Option[Seq[BankedWrite]] = getDef(x).flatMap(BankedWriter.unapply)
  def unapply(d: Def): Option[Seq[BankedWrite]] = d match {
    case writer: BankedWriter[_] if writer.bankedWrites.nonEmpty => Some(writer.bankedWrites)
    case _ => None
  }
}

trait BankedDequeueLike[T] extends BankedReader[T] with StreamAccess[T] { this: Op[T] =>
  def bankedReads: Seq[BankedRead]
  override def enables: Seq[Exp[Bit]] = bankedReads.flatMap(_.ens.getOrElse(Nil))
}
object BankedDequeueLike {
  @stateful def unapply(x: Exp[_]): Option[Seq[BankedRead]] = getDef(x).flatMap(BankedDequeueLike.unapply)
  def unapply(d: Def): Option[Seq[BankedRead]] = d match {
    case reader: BankedDequeueLike[_] if reader.bankedReads.nonEmpty => Some(reader.bankedReads)
    case _ => None
  }
}

trait BankedEnqueueLike[T] extends BankedWriter[T] with StreamAccess[T] { this: Op[T] =>
  def bankedWrites: Seq[BankedWrite]
  override def enables: Seq[Exp[Bit]] = bankedWrites.flatMap(_.ens.getOrElse(Nil))
}
object BankedEnqueueLike {
  @stateful def unapply(x: Exp[_]): Option[Seq[BankedWrite]] = getDef(x).flatMap(BankedEnqueueLike.unapply)
  def unapply(d: Def): Option[Seq[BankedWrite]] = d match {
    case writer: BankedEnqueueLike[_] if writer.bankedWrites.nonEmpty => Some(writer.bankedWrites)
    case _ => None
  }
}


abstract class BankedReaderOp[T:Type:Bits](
  mem:  Exp[_],
  bank: Seq[Seq[Exp[Index]]] = null,
  addr: Seq[Exp[Index]] = null,
  ens:  Seq[Exp[Bit]] = null
)(implicit vT: Type[VectorN[T]]) extends Op[VectorN[T]] with BankedReader[VectorN[T]] {
  final override def bankedReads: Seq[BankedRead] = BankedRead(mem, bank, addr, ens)
  val mT = typ[T]
  val bT = bits[T]
}
abstract class BankedWriterOp[T:Type:Bits](
  mem:  Exp[_],
  data: Seq[Exp[_]] = null,
  bank: Seq[Seq[Exp[Index]]] = null,
  addr: Seq[Exp[Index]] = null,
  ens:  Seq[Exp[Bit]] = null
) extends Op[MUnit] with BankedWriter[MUnit] {
  final override def bankedWrites: Seq[BankedWrite] = BankedWrite(mem, data, bank, addr, ens)
  val mT = typ[T]
  val bT = bits[T]
}
abstract class BankedDequeueLikeOp[T:Type:Bits](
  mem:   Exp[_],
  bank: Seq[Seq[Exp[Index]]] = null,
  addr: Seq[Exp[Index]] = null,
  ens:  Seq[Exp[Bit]] = null
)(implicit vT: Type[VectorN[T]]) extends Op[VectorN[T]] with BankedDequeueLike[VectorN[T]] {
  final override def bankedReads: Seq[BankedRead] = BankedRead(mem, bank, addr, ens)
  val mT = typ[T]
  val bT = bits[T]
}
abstract class BankedEnqueueLikeOp[T:Type:Bits](
  mem:  Exp[_],
  data: Seq[Exp[_]] = null,
  bank: Seq[Seq[Exp[Index]]] = null,
  addr: Seq[Exp[Index]] = null,
  ens:  Seq[Exp[Bit]] = null
) extends Op[MUnit] with BankedEnqueueLike[MUnit] {
  final override def bankedWrites: Seq[BankedWrite] = BankedWrite(mem, data, bank, addr, ens)
  val mT = typ[T]
  val bT = bits[T]
}


