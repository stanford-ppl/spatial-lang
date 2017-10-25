package spatial.metadata

import argon.analysis._
import argon.core._
import forge._
import spatial.aliases._
import spatial.utils._

object BankableAffine {
  def unapply(pattern: IndexPattern): Option[(Array[Int], Seq[Exp[Index]], Int)] = pattern match {
    case GeneralAffine(products, offset) =>
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

case class Banking(stride: Int, banks: Int, isOuter: Boolean) // Strided bank

object NoBanking {
  def apply(stride: Int) = Banking(stride, 1, isOuter = false)
  def unapply(x: Banking): Option[Int] = x match {
    case Banking(s,banks,isOuter) if banks == 1 && !isOuter => Some(s)
    case _ => None
  }
}

@data object Banks {
  def unapply(x: Banking): Option[Int] = Some(x.banks)
}

sealed abstract class Memory {
  def depth: Int
  def nDims: Int
  def strides: Seq[Int]
  def isAccum: Boolean
  def totalBanks: Int
  def costBasisBanks: Seq[Int]
}
case class BankedMemory(dims: Seq[Banking], depth: Int, isAccum: Boolean) extends Memory {
  def nDims = dims.length
  def strides = dims.map(_.stride)
  def totalBanks = dims.map(_.banks).product
  def costBasisBanks = dims.map(_.banks)
}
case class DiagonalMemory(strides: Seq[Int], banks: Int, depth: Int, isAccum: Boolean) extends Memory {
  def nDims = strides.length
  def totalBanks = banks
  def costBasisBanks = banks +: List.fill(strides.length - 1)(1)
}

case class Channels(memory: Memory, duplicates: Int) {
  def toList: List[Memory] = List.fill(duplicates){memory}
}

/*case class InstanceGroup (
  metapipe: Option[Ctrl],         // Controller if at least some accesses require n-buffering
  accesses: Iterable[Access],     // All accesses within this group
  channels: Channels,             // Banking/buffering information + duplication
  ports: Map[Access, Set[Int]],   // Set of ports each access is connected to
  swaps: Map[Access, Ctrl]        // Swap controller for done signal for n-buffering
) {

  private def invertedPorts: Array[Set[Access]] = {
    val depth = if (ports.values.isEmpty) 1 else ports.values.map(_.max).max + 1
    Array.tabulate(depth){port =>
      accesses.filter{a => ports(a).contains(port) }.toSet
    }
  }

  lazy val revPorts: Array[Set[Access]] = invertedPorts  // Set of accesses connected for each port
  def port(x: Int): Set[Access] = if (x >= revPorts.length) Set.empty else revPorts(x)

  def depth: Int = if (ports.values.isEmpty) 1 else ports.values.map(_.max).max+1
  // Assumes a fixed size, dual ported memory which is duplicated, both to meet duplicates and banking factors
  def normalizedCost: Int = depth * channels.duplicates * channels.memory.totalBanks
}*/


/**
  * Metadata for which bank a access belongs to
  */
case class Banks(banks: Seq[List[Int]]) extends Metadata[Banks] { def mirror(f:Tx) = this } //TODO
@data object banksOf {

  /*
   * Get all bank mapping for this access for the given memory and access
   * @return a Seq of all possible bank indices for BankedMemory for each dimension
   * @return a Seq of a single List of possible bank indices for diagonal banking
   * */ 
  def apply(access: Exp[_], mem: Exp[_], idx: Int): Seq[List[Int]] = {
    metadata[Banks](mem).map(_.banks).getOrElse {
      // Default worst case: all banks
      val duplicates = duplicatesOf(mem)
      duplicates(idx) match {
        case BankedMemory(dims, depth, isAccum) => dims.map {
          case Banking(stride, banks, isOuter) => (0 until banks).toList
        }
        case DiagonalMemory(strides, banks, depth, isAccum) => Seq((0 until banks).toList)
      }
    }
  } 
  def update(access: Exp[_], mem: Exp[_], idx: Int, banks:Seq[List[Int]]) = {
    metadata.add(access, Banks(banks)) //TODO: fix this
  }
}

/**
  * Metadata for duplicates of a single coherent scratchpad.
  */
case class Duplicates(dups: Seq[Memory]) extends Metadata[Duplicates] { def mirror(f:Tx) = this }
@data object duplicatesOf {
  def apply(mem: Exp[_]): Seq[Memory] = metadata[Duplicates](mem).map(_.dups).getOrElse(Nil)
  def update(mem: Exp[_], dups: Seq[Memory]) = metadata.add(mem, Duplicates(dups))
}

/**
  * Metadata for determining which memory duplicate(s) an access should correspond to.
  */
case class AccessDispatch(mapping: Map[Exp[_], Set[Int]]) extends Metadata[AccessDispatch] {
  def mirror(f:Tx) = AccessDispatch(mapping.map{case (mem,idxs) => f(mem) -> idxs })
}
@data object dispatchOf {
  private def get(access: Exp[_]): Option[Map[Exp[_], Set[Int]]] = metadata[AccessDispatch](access).map(_.mapping)
  def get(access: Exp[_], mem: Exp[_]): Option[Set[Int]] = dispatchOf.get(access).flatMap(_.get(mem))

  def apply(access: Exp[_], mem: Exp[_]): Set[Int] = {
    dispatchOf.get(access, mem).getOrElse{ throw new spatial.UndefinedDispatchException(access, mem) }
  }

  def update(access: Exp[_], mem: Exp[_], idxs: Set[Int]): Unit = dispatchOf.get(access) match {
    case Some(map) =>
      val newMap = map.filterKeys(_ != mem) + (mem -> idxs)
      metadata.add(access, AccessDispatch(newMap))
    case None =>
      metadata.add(access, AccessDispatch(Map(mem -> idxs)))
  }

  def add(access: Exp[_], mem: Exp[_], idx: Int): Unit = dispatchOf.get(access) match {
    case Some(map) =>
      val newMap = map.filterKeys(_ != mem) + (mem -> (map.getOrElse(mem,Set.empty) + idx))
      metadata.add(access, AccessDispatch(newMap))
    case None =>
      metadata.add(access, AccessDispatch(Map(mem -> Set(idx))))
  }

  def apply(access: Access, mem: Exp[_]): Set[Int] = { dispatchOf(access.node, mem) }
  def get(access: Access, mem: Exp[_]): Option[Set[Int]] = { dispatchOf.get(access.node, mem) }
  def update(access: Access, mem: Exp[_], idxs: Set[Int]) { dispatchOf(access.node, mem) = idxs }
  def add(access: Access, mem: Exp[_], idx: Int) { dispatchOf.add(access.node, mem, idx) }

  def clear(access: Access, mem: Exp[_]): Unit = { dispatchOf(access, mem) = Set[Int]() }
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
