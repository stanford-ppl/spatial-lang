package spatial.metadata

import argon.analysis._
import argon.core._
import forge._
import spatial.aliases._
import spatial.utils._

/*case class Banking(stride: Int, banks: Int, isOuter: Boolean) // Strided bank

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
}*

