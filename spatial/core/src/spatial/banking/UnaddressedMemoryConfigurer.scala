package spatial.banking

import argon.core._
import spatial.aliases._
import spatial.metadata._
import spatial.utils._

class UnaddressedMemoryConfigurer(override val mem: Exp[_]) extends MemoryConfigurer(mem) {

  def bank(reads: Seq[Access], writes: Seq[Access]) = {
    val accesses = reads ++ writes
    var illegalParallelAccesses: Seq[Access] = Nil
    val accessFactors = getUnaddressedPars(accesses)
    val banking = accessFactors.max
  }

  def bankFIFOAccess(mem: Exp[_], access: Exp[_]): Channels = {
    val factors =
    // All parallelization factors relative to the memory, except the innermost, must either be empty or only contain 1s
    // Otherwise, we have multiple concurrent reads/writes
    val innerLoopParOnly =
    if (!innerLoopParOnly) {

    }

    val channels = factors.flatten.map{case Exact(c) => c.toInt}.product
    Channels(BankedMemory(Seq(Banking(1, channels, false)), depth = 1, isAccum = false), 1)
  }

  def bankStream(mem: Exp[_]): Unit = {
    dbg("")
    dbg("")
    dbg("-----------------------------------")
    dbg(u"Inferring instances for memory ${str(mem)}")

    val reads = readersOf(mem)
    val writes = writersOf(mem)
    val accesses = reads ++ writes

    accesses.foreach{access =>
      dispatchOf.add(access, mem, 0)
      portsOf(access, mem, 0) = Set(0)
    }

    val pars = accesses.map{access =>
      val factors = unrollFactorsOf(access.node) // relative to stream, which always has par of 1
      factors.flatten.map{case Exact(c) => c.toInt}.product
    }

    val par = (1 +: pars).max

    /*val bus = mem match {
      case Op(StreamInNew(bus)) => bus
      case Op(StreamOutNew(bus)) => bus
    }*/
    val dup = BankedMemory(Seq(Banking(1,par,true)),1,isAccum=false)

    dbg(s"")
    dbg(s"  stream: ${str(mem)}")
    dbg(s"  accesses: ")
    accesses.zip(pars).foreach{case (access, p) => dbg(c"    ${str(access.node)} [$p]")}
    dbg(s"  duplicate: $dup")
    duplicatesOf(mem) = List(dup)
  }


}
