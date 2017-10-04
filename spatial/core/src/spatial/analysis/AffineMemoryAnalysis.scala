package spatial.analysis

import argon.core._
import spatial.aliases._
import spatial.metadata._
import spatial.utils._

// This is separated out for now because I don't know if it fully works or not
trait AffineMemoryAnalysis { this: MemoryAnalyzer =>

  /*def mergeMismatchedBanking(mem: Exp[_], a: Banking, b: Banking, d: Int): Banking = {
    val Banking(s1,p,o1) = a
    val Banking(s2,q,o2) = b

    (a,b) match {
    case (Banking(s1,p,o1), Banking(s2,q,o2)) if s1 == s2 => Banking(s1, lcm(p,q), o1 || o2)
    case (NoBanking(s1), Banking(s2,_,_)) if s1 == s2 => b
    case (Banking(s1,_,_), NoBanking(s2)) if s1 == s2 => a
    case _ =>
      warn(mem.ctx, u"${mem.tp}, defined here, appears to be addressed with mismatched strides")
      warn(mem.ctx)
      NoBanking(1) // TODO
  }*/

  def mergeMismatchedChannels(mem: Exp[_], a: Channels, b: Channels): Memory = {
    val size = constDimsOf(mem).product
    val memA = a.memory
    val memB = b.memory
    val dupA = a.duplicates
    val dupB = b.duplicates

    val nDims = Math.max(memA.nDims, memB.nDims)

    def lengthenMem(x: Memory) = x match {
      case BankedMemory(dims, depth, isAccum) =>
        val dims2 = List.fill(nDims-dims.length)(Banking(1,1,isOuter=false)) ++ dims
        BankedMemory(dims2, depth, isAccum)
      case DiagonalMemory(strides, banks, depth, isAccum) =>
        val dims2 = List.fill(nDims-strides.length)(Banking(1,1,isOuter=false)) ++ strides.map{s => Banking(s,banks,isOuter=false) }
        BankedMemory(dims2, depth, isAccum)
    }

    val mA = lengthenMem(memA)
    val mB = lengthenMem(memB)

    val stridesA = mA.dims.map(_.stride)
    val dimsA    = stridesToDims(mem, stridesA)
    val banksA   = mA.dims.map(_.banks)
    val stridesB = mB.dims.map(_.stride)
    val dimsB    = stridesToDims(mem, stridesB)
    val banksB   = mB.dims.map(_.banks)

    // TODO: I'm not quite sure when this is safe yet.. is it always safe?
    val dims = if (memA.nDims < memB.nDims) dimsB else dimsA
    val keep = if (memA.nDims < memB.nDims) mB else mA
    val move = if (memA.nDims < memB.nDims) mA else mB

    dbg(c"keep: $keep")
    dbg(c"move: $move")

    var p = 1
    val banks = List.tabulate(dims.length){i =>
      val j = dims.length - i - 1
      val d = dims(j)
      val Banking(s1,b1,o1) = keep.dims(j)
      val Banking(s2,b2,o2) = move.dims(j)
      val banks = Math.min(d, lcm(b1,b2*p))
      val rem = Math.ceil(b2*p / banks.toDouble).toInt
      if (rem > 1) p *= rem
      Banking(s1, banks, o1 || o2)
    }.reverse

    val memC = BankedMemory(banks, Math.max(memA.depth,memB.depth), memA.isAccum || memB.isAccum)

    dbg(c"combined: $memC")

    val channelsA = memA.totalBanks * dupA  // Ab0 * ... * AbN * Adup   e.g. 16 * 2 * 2 = 64 or 3 * 2 * 4 = 24
    val channelsB = memB.totalBanks * dupB  // Bb0 * ... * BbN * Bdup   e.g. 1  * 1 * 1 = 1  or 2 * 2 * 4 = 16
    val channelsC = Math.max(channelsA, channelsB) // = Cb0 * ... * CbN * Cdup
    val banksC = memC.totalBanks // mrg(Ab0,Bb0) * ... * mrg(AbN,BbN)   e.g. 1  * 1     = 1  or 6 * 2 = 12

    val origDups  = Math.max(dupA, dupB)
    val extraDups = (channelsC + banksC - 1) / banksC //  = ceiling(channelsC / banksC)
    val dupC = Math.max(origDups,extraDups)

    dbg(u"Memory $mem has mismatched accesses: ")
    dbg(u"    $memA [$dupA]")
    dbg(u"    $memB [$dupB]")
    dbg("   Combined: ")
    dbg(u"    $memC [$dupC]")

    memC
  }

}
