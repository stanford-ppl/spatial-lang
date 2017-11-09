package spatial.banking

import argon.analysis._
import argon.core._
import forge._
import spatial.aliases._
import spatial.metadata._
import spatial.utils._

import scala.collection.mutable.HashMap

object Affine {
  def unapply(pattern: IndexPattern): Option[(Array[Int], Seq[Exp[Index]], Int)] = pattern match {
    case SymbolicAffine(products, offset) =>
      val asOpt = products.map(p => p.a.getEval{case Exact(c) => c.toInt })
      val bOpt = offset.getEval{case Exact(c) => c.toInt }
      if (asOpt.forall(_.isDefined) && bOpt.isDefined) {
        val as = asOpt.map(_.get).toArray
        val b = bOpt.get
        val is = products.map(_.i)
        Some((as, is, b))
      }
      else None
    case _ => None
  }
}

/**
  * Abstract class for any banking strategy
  * (Currently, the only strategy is (possibly hierarchical) mod banking)
  */
sealed abstract class Banking {
  def nBanks: Int
  def stride: Int
  def dims: Seq[Int]
  @internal def bankAddress(ndAddr: Seq[Exp[Index]]): Exp[Index]
}

/**
  * Banking address function (alpha*A / B) mod N
  */
case class ModBanking(N: Int, B: Int, alpha: Seq[Int], dims: Seq[Int]) extends Banking {
  override def nBanks: Int = N
  override def stride: Int = B

  @internal def bankAddress(ndAddr: Seq[Exp[Index]]): Exp[Index] = {
    val mults = alpha.zip(dims).map{case (a,dim) => wrap(ndAddr(dim))*a }
    val addr  = (spatial.lang.Math.sumTree(mults) / B) % N
    addr.s
  }
}


/**
  * Used during memory analysis to track intermediate results
  */
case class InstanceGroup(
  var reads:    Set[AccessMatrix],      // All reads within this group
  var writes:   Set[AccessMatrix],      // All writes within this group
  var ctrls:    Set[Ctrl],              // Set of controllers these accesses are in
  var metapipe: Option[Ctrl],           // Controller if at least some accesses require n-buffering
  var banking:  Seq[Banking],           // Banking information
  var depth:    Int,                    // Depth of n-buffer
  var cost:     Int,                    // Cost estimate of this configuration
  var ports:    Map[Access,Int]         // Ports
)

/**
  * Used internally in memory analysis to track finalized results
  */
case class MemoryInstance(
  reads:    Set[UnrolledAccess],           // All reads within this group
  writes:   Set[UnrolledAccess],           // All writes within this group
  metapipe: Option[Ctrl],                  // Controller if at least some accesses require n-buffering
  banking:  Seq[Banking],                  // Banking information
  depth:    Int,                           // Depth of n-buffer
  ports:    Map[Access,Set[Int]],          // Ports
  isAccum:  Boolean                        // Flag whether this instance is an accumulator
)

/**
  * Abbreviated version of MemoryInstance for use outside memory analysis
  */
case class Memory(
  banking: Seq[Banking],  // Banking information
  depth:   Int,           // Buffer depth
  isAccum: Boolean        // Flags whether this instance is an accumulator
) {
  @internal def bankOffset(mem: Exp[_], addr: Seq[Exp[Index]]): Exp[Index] = {
    val w = constDimsOf(mem)
    val d = w.length

    val n = banking.map(_.nBanks).product
    val b = banking.find(_.dims.contains(d-1)).map(_.stride).getOrElse(1)

    // TODO: Need to confirm correctness for non-flat case
    spatial.lang.Math.sumTree((0 until d).map{t =>
      val xt = wrap(addr(t))
      if (t < d - 1) { xt * (w.slice(t+1,d-2).product * math.ceil(w(d-1).toDouble / (n*b)).toInt * b) }
      else           { (xt / (n*b)) * b + xt % b }
    }).s
  }
}


/**
  * Metadata for duplicates of a single coherent memory.
  */
case class Duplicates(dups: Seq[Memory]) extends Metadata[Duplicates] { def mirror(f:Tx) = this }
@data object duplicatesOf {
  def apply(mem: Exp[_]): Seq[Memory] = metadata[Duplicates](mem).map(_.dups).getOrElse(Nil)
  def update(mem: Exp[_], dups: Seq[Memory]): Unit = metadata.add(mem, Duplicates(dups))
}


/**
  * Metadata for determining which memory duplicate(s) an access should connect to.
  */
case class AccessDispatch(mapping: HashMap[Exp[_], HashMap[Seq[Int],Set[Int]]]) extends Metadata[AccessDispatch] {
  def mirror(f:Tx) = AccessDispatch(mapping.map{case (mem,idxs) => f(mem) -> idxs })
}
@data object dispatchOf {
  private def getOrAdd(access: Exp[_]): HashMap[Exp[_],HashMap[Seq[Int],Set[Int]]] = {
    metadata[AccessDispatch](access).map(_.mapping) match {
      case Some(map) => map
      case None =>
        val map = HashMap.empty[Exp[_],HashMap[Seq[Int],Set[Int]]]
        metadata.add(access, AccessDispatch(map))
        map
    }
  }
  private def getOrAdd(access: Exp[_], mem: Exp[_]): HashMap[Seq[Int],Set[Int]] = {
    val map = getOrAdd(access)
    if (map.contains(mem)) map(mem) else {
      val innerMap = HashMap.empty[Seq[Int],Set[Int]]
      map += mem -> innerMap
      innerMap
    }
  }

  def get(access: UAccess, mem: Exp[_]): Option[Set[Int]] = {
    metadata[AccessDispatch](access.node).map(_.mapping).flatMap(_.get(mem)).flatMap(_.get(access.id))
  }

  def apply(access: UAccess, mem: Exp[_]): Set[Int] = {
    dispatchOf.get(access, mem).getOrElse{
      bug(c"Access $access had no dispatch information for memory $mem")
      bug(access.node.ctx)
      Set.empty
    }
  }

  /**
    * Sets the dispatches of the unrolled access for the given memory to the given indices
    */
  def update(access: UAccess, mem: Exp[_], dispatches: Set[Int]): Unit = {
    getOrAdd(access.node, mem) += access.id -> dispatches
  }

  def add(access: UAccess, mem: Exp[_], dispatch: Int): Unit = {
    val map = getOrAdd(access.node, mem)
    map += access.id -> (map.getOrElse(access.id, Set.empty[Int]) + dispatch)
  }

  /*def apply(access: Access, mem: Exp[_]): Set[Int] = { dispatchOf(access.node, mem) }
  def get(access: Access, mem: Exp[_]): Option[Set[Int]] = { dispatchOf.get(access.node, mem) }
  def update(access: Access, mem: Exp[_], idxs: Set[Int]) { dispatchOf(access.node, mem) = idxs }
  def add(access: Access, mem: Exp[_], idx: Int) { dispatchOf.add(access.node, mem, idx) }*/

  //def clear(access: Access, mem: Exp[_]): Unit = { dispatchOf(access, mem) = Set[Int]() }
}


/**
  * Metadata for which n-buffered ports a given access should connect to. Ports should either be:
  * - Undefined (for unbuffered cases)
  * - A single port (for buffered cases)
  * - All ports of the given memory (for writes time multiplexed with the buffer)
  */
case class PortIndex(mapping: Map[Exp[_], Map[Int, Set[Int]]]) extends Metadata[PortIndex] {
  def mirror(f:Tx) = PortIndex(mapping.map{case (mem,idxs) => f(mem) -> idxs })
}
@data object portsOf {
  private def get(access: Exp[_]): Option[Map[Exp[_], Map[Int, Set[Int]]]] = metadata[PortIndex](access).map(_.mapping)

  def get(access: Exp[_], mem: Exp[_]): Option[Map[Int, Set[Int]]] = portsOf.get(access).flatMap(_.get(mem))

  // Get all port mappings for this access for the given memory
  def apply(access: Exp[_], mem: Exp[_]): Map[Int, Set[Int]] = {
    portsOf.get(access, mem).getOrElse { throw new spatial.UndefinedPortsException(access, mem, None) }
  }

  // Get ports for this access for the given memory and instance index
  def apply(access: Exp[_], mem: Exp[_], idx: Int): Set[Int] = {
    val ports = portsOf(access, mem)
    ports.getOrElse(idx, throw new spatial.UndefinedPortsException(access, mem, Some(idx)))
  }

  // Override all ports for this access for the given memory and instance index
  def update(access: Exp[_], mem: Exp[_], idx: Int, ports: Set[Int]): Unit = portsOf.get(access) match {
    case Some(map) =>
      val newMap = map.filterKeys(_ != mem) + (mem -> (map.getOrElse(mem,Map.empty) + (idx -> ports)))
      metadata.add(access, PortIndex(newMap))
    case None =>
      metadata.add(access, PortIndex(Map(mem -> Map(idx -> ports))))
  }

  // Override all ports for this access for the given memory for all instance indices
  def update(access: Exp[_], mem: Exp[_], ports: Map[Int, Set[Int]]): Unit = {
    ports.foreach{case (idx,portSet) => portsOf(access, mem, idx) = portSet }
  }

  def apply(access: Access, mem: Exp[_]): Map[Int,Set[Int]] = portsOf(access.node, mem)
  def get(access: Access, mem: Exp[_]): Option[Map[Int,Set[Int]]] = portsOf.get(access.node, mem)
  def apply(access: Access, mem: Exp[_], idx: Int): Set[Int] = { portsOf(access.node, mem, idx) }
  def update(access: Access, mem: Exp[_], idx: Int, ports: Set[Int]): Unit = { portsOf(access.node, mem, idx) = ports }
  def update(access: Access, mem: Exp[_], ports: Map[Int,Set[Int]]): Unit = { portsOf(access.node, mem) = ports }

  def set(access: Exp[_], mem: Exp[_], ports: Map[Int,Set[Int]]): Unit = portsOf.get(access) match {
    case Some(map) =>
      val newMap = map.filterKeys(_ != mem) + (mem -> ports)
      metadata.add(access, PortIndex(newMap))
    case None =>
      metadata.add(access, PortIndex(Map(mem -> ports)))
  }
}


/**
  * Metadata for the controller determining the done signal for a buffered read or write
  * Set per memory and per instance index
  */
case class TopController(mapping: Map[Exp[_], Map[Int,Ctrl]]) extends Metadata[TopController] {
  def mirror(f:Tx) = TopController(mapping.map{case (mem,ctrls) => f(mem) -> ctrls.map{case (i,ctrl) => i -> mirrorCtrl(ctrl,f) }})
}
@data object topControllerOf {
  private def get(access: Exp[_]): Option[Map[Exp[_],Map[Int,Ctrl]]] = metadata[TopController](access).map(_.mapping)

  // Get the top controller for the given access, memory, and instance index
  def apply(access: Exp[_], mem: Exp[_], idx: Int): Option[Ctrl] = {
    topControllerOf.get(access).flatMap(_.get(mem)).flatMap(_.get(idx))
  }

  // Set top controller for the given access, memory, and instance index
  def update(access: Exp[_], mem: Exp[_], idx: Int, ctrl: Ctrl): Unit = topControllerOf.get(access) match {
    case Some(map) =>
      val newMap = map.filterKeys(_ != mem) + (mem -> (map.getOrElse(mem,Map.empty) + (idx -> ctrl)))
      metadata.add(access, TopController(newMap))
    case None =>
      metadata.add(access, TopController(Map(mem -> Map(idx -> ctrl))))
  }

  def update(access: Access, mem: Exp[_], idx: Int, ctrl: Ctrl): Unit = { topControllerOf(access.node, mem, idx) = ctrl }
  def apply(access: Access, mem: Exp[_], idx: Int): Option[Ctrl] = { topControllerOf(access.node, mem, idx) }
}


