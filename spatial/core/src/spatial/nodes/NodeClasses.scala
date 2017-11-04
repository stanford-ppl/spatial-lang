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
  def dim: Int
}

trait LocalReader[T] extends EnabledAccess[T] { this: Op[T] =>
  def localReads: Seq[LocalRead]
  override def enables: Seq[Exp[Bit]] = localReads.flatMap(_.en)
}
object LocalReader {
  @stateful def unapply(x: Exp[_]): Option[Seq[LocalRead]] = getDef(x).flatMap(LocalReader.unapply)
  def unapply(d: Def): Option[Seq[LocalRead]] = d match {
    case reader: LocalReader[_] if reader.localReads.nonEmpty => Some(reader.localReads)
    case _ => None
  }
}

trait LocalWriter[T] extends EnabledAccess[T] { this: Op[T] =>
  def localWrites: Seq[LocalWrite]
  override def enables: Seq[Exp[Bit]] = localWrites.flatMap(_.en)
}
object LocalWriter {
  @stateful def unapply(x: Exp[_]): Option[Seq[LocalWrite]] = getDef(x).flatMap(LocalWriter.unapply)
  def unapply(d: Def): Option[Seq[LocalWrite]] = d match {
    case writer: LocalWriter[_] if writer.localWrites.nonEmpty => Some(writer.localWrites)
    case _ => None
  }
}

trait VectorReader[T] extends LocalReader[T] with VectorAccess[T] { this: Op[T] => }
object VectorReader {
  @stateful def unapply(x: Exp[_]): Option[Seq[LocalRead]] = getDef(x).flatMap(VectorReader.unapply)
  def unapply(d: Def): Option[Seq[LocalRead]] = d match {
    case reader: VectorReader[_] if reader.localReads.nonEmpty => Some(reader.localReads)
    case _ => None
  }
}

trait VectorWriter[T] extends LocalWriter[T] with VectorAccess[T] { this: Op[T] => }
object VectorWriter {
  @stateful def unapply(x: Exp[_]): Option[Seq[LocalWrite]] = getDef(x).flatMap(VectorWriter.unapply)
  def unapply(d: Def): Option[Seq[LocalWrite]] = d match {
    case writer: VectorWriter[_] if writer.localWrites.nonEmpty => Some(writer.localWrites)
    case _ => None
  }
}


trait LocalReadStatus[T] { this: Op[T] =>
  def memory: Exp[_]
}
object LocalReadStatus {
  @stateful def unapply(x: Exp[_]): Option[Exp[_]] = getDef(x).flatMap(LocalReadStatus.unapply)
  def unapply(d: Def): Option[Exp[_]] = d match {
    case reader: LocalReadStatus[_] => Some(reader.memory)
    case _ => None
  }
}

trait LocalReadModify[T] extends LocalReader[T] { this: Op[T] =>
  override def enables: Seq[Exp[Bit]] = localReads.flatMap(_.en)
}
object LocalReadModify {
  @stateful def unapply(x: Exp[_]): Option[Seq[LocalRead]] = getDef(x).flatMap(LocalReadModify.unapply)
  def unapply(d: Def): Option[Seq[LocalRead]] = d match {
    case reader: LocalReadModify[_] if reader.localReads.nonEmpty => Some(reader.localReads)
    case _ => None
  }
}

object LocalAccess {
  @stateful def unapply(x: Exp[_]): Option[Seq[Exp[_]]] = getDef(x).flatMap(LocalAccess.unapply)
  def unapply(d: Def): Option[Seq[Exp[_]]] = {
    val accessed = {
        LocalReader.unapply(d).map(reads => reads.map(_.mem)).getOrElse(Nil) ++
        LocalWriter.unapply(d).map(writes => writes.map(_.mem)).getOrElse(Nil) ++
        LocalReadModify.unapply(d).map(reads => reads.map(_.mem)).getOrElse(Nil)
    }
    if (accessed.isEmpty) None else Some(accessed)
  }
}


trait LocalResetter[T] extends EnabledPrimitive[T] { this: Op[T] =>
  def localReset: LocalReset
  override def enables: Seq[Exp[Bit]] = localReset.en.toSeq
}
object LocalResetter {
  @stateful def unapply(x: Exp[_]): Option[LocalReset] = getDef(x).flatMap(LocalResetter.unapply)
  def unapply(d: Def): Option[LocalReset] = d match {
    case resetter: LocalResetter[_] => Some(resetter.localReset)
    case _ => None
  }
}


abstract class EnabledOp[T:Type](ens: Exp[Bit]*) extends Op[T] with EnabledPrimitive[T] {
  override def enables: Seq[Exp[Bit]] = ens.toSeq
}
abstract class LocalReaderOp[T:Type:Bits,R:Type:Bits](
  mem:  Exp[_],
  addr: Seq[Exp[Index]] = null,
  en:   Exp[Bit] = null
) extends Op[R] with LocalReader[R] {
  final override def localReads: Seq[LocalRead] = LocalRead(mem,addr,en)
  val mT = typ[T]
  val bT = bits[T]
  override def address: Option[Seq[Exp[Index]]] = Option(addr)
}
abstract class LocalWriterOp[T:Type:Bits](
  mem:  Exp[_],
  data: Exp[_] = null,
  addr: Seq[Exp[Index]] = null,
  en:   Exp[Bit] = null
) extends Op[MUnit] with LocalWriter[MUnit] {
  final override def localWrites: Seq[LocalWrite] = LocalWrite(mem,data,addr,en)
  val mT = typ[T]
  val bT = bits[T]
  override def address: Option[Seq[Exp[Index]]] = Option(addr)
}

abstract class VectorReaderOp[T:Type:Bits](
  mem:  Exp[_],
  addr: Seq[Exp[Index]] = null,
  en:   Exp[Bit] = null,
  dim:  Int,                      // Dimension of vector read (e.g. for 2D, 0 = row slice, 1 = col slice)
  len:  Int                       // Length of vector slice
)(implicit vT: Type[VectorN[T]]) extends Op[VectorN[T]] with VectorReader[VectorN[T]] {
  final override def localReads: Seq[LocalRead] = LocalRead(mem, addr, en)
  override def accessWidth: Int = len
  val mT = typ[T]
  val bT = bits[T]
  override def address: Option[Seq[Exp[Index]]] = Option(addr)
}

abstract class VectorWriterOp[T:Type:Bits](
  mem:  Exp[_],
  data: Exp[VectorN[T]],
  addr: Seq[Exp[Index]] = null,
  en:   Exp[Bit] = null,
  dim:  Int,
  len:  Int
) extends Op[MUnit] with VectorWriter[MUnit] {
  final override def localWrites: Seq[LocalWrite] = LocalWrite(mem,data,addr,en)
  override def accessWidth: Int = len
  val mT = typ[T]
  val bT = bits[T]
  override def address: Option[Seq[Exp[Index]]] = Option(addr)
}


abstract class LocalReadModifyOp[T:Type:Bits,R:Type:Bits](
  mem:  Exp[_],
  addr: Seq[Exp[Index]] = null,
  en:   Exp[Bit] = null
) extends Op[R] with LocalReadModify[R] {
  final override def localReads: Seq[LocalRead] = LocalRead(mem,addr,en)
  val mT = typ[T]
  val bT = bits[T]
  override def address: Option[Seq[Exp[Index]]] = Option(addr)
}
abstract class LocalReadStatusOp[T:Type:Bits,R:Type:Bits](
  mem: Exp[_]
) extends Op[R] with LocalReadStatus[R] {
  final override def memory: Exp[_] = mem
  val mT = typ[T]
  val bT = bits[T]
}

abstract class LocalResetterOp(
  mem: Exp[_],
  en:  Exp[Bit] = null
) extends Op[MUnit] with LocalResetter[MUnit] {
  final override def localReset: LocalReset = LocalReset(mem,en=en)
}


/** Vectorized primitive nodes **/
/*trait ParLocalReader[T] extends LocalReader[T] { this: Op[T] =>
  def parLocalReads: Seq[ParLocalRead]
  override def accessWidth: Int = enables.length / parLocalReads.length

  override def enables: Seq[Exp[Bit]] = parLocalReads.flatMap(_.ens.getOrElse(Nil))
  final override def localReads: Seq[LocalRead] = parLocalReads.flatMap{case (mem,inds,ens) => LocalRead(mem) }
}
object ParLocalReader {
  @stateful def unapply(x: Exp[_]): Option[Seq[ParLocalRead]] = getDef(x).flatMap(ParLocalReader.unapply)
  def unapply(d: Def): Option[Seq[ParLocalRead]] = d match {
    case reader: ParLocalReader[_] if reader.parLocalReads.nonEmpty => Some(reader.parLocalReads)
    case reader: LocalReader[_] if reader.localReads.nonEmpty =>
      Some(reader.localReads.map{case (mem,addr,en) => (mem,addr.map(Seq(_)),en.map(Seq(_))) })
    case _ => None
  }
}

trait ParLocalWriter[T] extends LocalWriter[T] { this: Op[T] =>
  def parLocalWrites: Seq[ParLocalWrite]
  override def accessWidth: Int = enables.length / parLocalWrites.length

  override def enables: Seq[Exp[Bit]] = parLocalWrites.flatMap(_.ens.getOrElse(Nil))
  override def localWrites: Seq[LocalWrite] = parLocalWrites.flatMap{case (mem,datas,inds,ens) => LocalWrite(mem)}
}
object ParLocalWriter {
  @stateful def unapply(x: Exp[_]): Option[Seq[ParLocalWrite]] = getDef(x).flatMap(ParLocalWriter.unapply)
  def unapply(d: Def): Option[Seq[ParLocalWrite]] = d match {
    case writer: ParLocalWriter[_] if writer.parLocalWrites.nonEmpty => Some(writer.parLocalWrites)
    case writer: LocalWriter[_] if writer.localWrites.nonEmpty =>
      Some(writer.localWrites.map{case (mem,value,addr,en) => (mem,value.map(Seq(_)),addr.map(Seq(_)),en.map(Seq(_))) })
    case _ => None
  }
}

trait ParLocalReadModify[T] extends LocalReadModify[T] with ParLocalReader[T] { this: Op[T] =>
  def parLocalReads: Seq[ParLocalRead]
  override def enables: Seq[Exp[Bit]] = parLocalReads.flatMap(_.ens.getOrElse(Nil))
}
object ParLocalReadModify {
  @stateful def unapply(x: Exp[_]): Option[Seq[ParLocalRead]] = getDef(x).flatMap(ParLocalReadModify.unapply)
  def unapply(d: Def): Option[Seq[ParLocalRead]] = d match {
    case reader: ParLocalReadModify[_] if reader.parLocalReads.nonEmpty => Some(reader.parLocalReads)
    case reader: LocalReadModify[_] if reader.localReads.nonEmpty =>
      Some(reader.localReads.map{case (mem,addr,en) => (mem,addr.map(Seq(_)),en.map(Seq(_))) })
    case _ => None
  }
}

abstract class ParLocalReaderOp[T:Type](
  mem:   Exp[_],
  addrs: Seq[Seq[Exp[Index]]] = null,
  ens:   Seq[Exp[Bit]] = null
) extends Op[T] with ParLocalReader[T] {
  final override def parLocalReads: Seq[ParLocalRead] = ParLocalRead(mem, addrs=addrs, ens=ens)
}
abstract class ParLocalWriterOp(
  mem:    Exp[_],
  values: Seq[Exp[_]] = null,
  addrs:  Seq[Seq[Exp[Index]]] = null,
  ens:    Seq[Exp[Bit]] = null
) extends Op[MUnit] with ParLocalWriter[MUnit] {
  final override def parLocalWrites: Seq[ParLocalWrite] = ParLocalWrite(mem, values, addrs=addrs, ens=ens)
}
abstract class ParLocalReadModifyOp[T:Type](
  mem:   Exp[_],
  addrs: Seq[Seq[Exp[Index]]] = null,
  ens:   Seq[Exp[Bit]] = null
) extends Op[T] with ParLocalReadModify[T] {
  final override def parLocalReads: Seq[ParLocalRead] = ParLocalRead(mem, addrs=addrs, ens=ens)
}*/


/** Banked primitive nodes **/
trait BankedReader[T] extends LocalReader[T] { this: Op[T] =>
  def bankedReads: Seq[BankedRead]
  override def accessWidth: Int = enables.length

  override def enables: Seq[Exp[Bit]] = bankedReads.flatMap(_.ens.getOrElse(Nil))
  final override def localReads: Seq[LocalRead] = bankedReads.flatMap{read => LocalRead(read.mem) }
}
object BankedReader {
  @stateful def unapply(x: Exp[_]): Option[Seq[BankedRead]] = getDef(x).flatMap(BankedReader.unapply)
  def unapply(d: Def): Option[Seq[BankedRead]] = d match {
    case reader: BankedReader[_] if reader.bankedReads.nonEmpty => Some(reader.bankedReads)
    case _ => None
  }
}


trait BankedWriter[T] extends LocalWriter[T] { this: Op[T] =>
  def bankedWrites: Seq[BankedWrite]
  override def accessWidth: Int = enables.length

  override def enables: Seq[Exp[Bit]] = bankedWrites.flatMap(_.ens.getOrElse(Nil))
  override def localWrites: Seq[LocalWrite] = bankedWrites.flatMap{write => LocalWrite(write.mem)}
}
object BankedWriter {
  @stateful def unapply(x: Exp[_]): Option[Seq[BankedWrite]] = getDef(x).flatMap(BankedWriter.unapply)
  def unapply(d: Def): Option[Seq[BankedWrite]] = d match {
    case writer: BankedWriter[_] if writer.bankedWrites.nonEmpty => Some(writer.bankedWrites)
    case _ => None
  }
}

trait BankedReadModify[T] extends LocalReadModify[T] with BankedReader[T] { this: Op[T] =>
  def bankedReads: Seq[BankedRead]
  override def enables: Seq[Exp[Bit]] = bankedReads.flatMap(_.ens.getOrElse(Nil))
}
object BankedReadModify {
  @stateful def unapply(x: Exp[_]): Option[Seq[BankedRead]] = getDef(x).flatMap(BankedReadModify.unapply)
  def unapply(d: Def): Option[Seq[BankedRead]] = d match {
    case reader: BankedReadModify[_] if reader.bankedReads.nonEmpty => Some(reader.bankedReads)
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
abstract class BankedReadModifyOp[T:Type:Bits](
  mem:   Exp[_],
  bank: Seq[Seq[Exp[Index]]] = null,
  addr: Seq[Exp[Index]] = null,
  ens:  Seq[Exp[Bit]] = null
)(implicit vT: Type[VectorN[T]]) extends Op[VectorN[T]] with BankedReadModify[VectorN[T]] {
  final override def bankedReads: Seq[BankedRead] = BankedRead(mem, bank, addr, ens)
  val mT = typ[T]
  val bT = bits[T]
}


