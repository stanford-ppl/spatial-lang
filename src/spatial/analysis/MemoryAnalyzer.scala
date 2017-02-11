package spatial.analysis

import argon.traversal.CompilerPass
import org.virtualized.SourceContext
import spatial.SpatialExp

trait MemoryAnalyzer extends CompilerPass {
  val IR: SpatialExp
  import IR._

  def localMems: Seq[Exp[_]]

  override val name = "Memory Analyzer"

  def mergeBanking(mem: Exp[_], a: Banking, b: Banking): Banking = (a,b) match {
    case (StridedBanking(s1,p), StridedBanking(s2,q)) if s1 == s2 => StridedBanking(s1, lcm(p,q))
    case (NoBanking, _) => NoBanking
    case (_, NoBanking) => NoBanking
    case _ =>
      warn(ctxOrHere(mem), u"${mem.tp}, defined here, appears to be addressed with mismatched strides")
      warn(ctxOrHere(mem))
      NoBanking
  }

  def mergeMemory(mem: Exp[_], a: Memory, b: Memory): Memory = {
    if (a.nDims != b.nDims) {
      new DimensionMismatchError(mem, a.nDims, b.nDims)(ctxOrHere(mem))
      BankedMemory(List.fill(a.nDims)(NoBanking), Math.max(a.depth,b.depth), a.isAccum || b.isAccum)
    }
    else (a,b) match {
      case (DiagonalMemory(s1,p,d1,a1), DiagonalMemory(s2,q,d2,a2)) =>
        if (s1.zip(s2).forall{case (x,y) => x == y}) {
          DiagonalMemory(s1, lcm(p,q), Math.max(d1,d2), a1 || a2)
        }
        else {
          warn(ctxOrHere(mem), u"${mem.tp}, defined here, appears to be addressed with mismatched strides")
          warn(ctxOrHere(mem))
          BankedMemory(s1.map{_ => NoBanking}, Math.max(d1,d2), a.isAccum || b.isAccum)
        }

      case (BankedMemory(b1,d1,a1), BankedMemory(b2, d2,a2)) => (b1,b2) match {
        case (List(Banking(1), StridedBanking(s1,p)), List(StridedBanking(s2,q), Banking(1))) if p > 1 && q > 1 =>
          DiagonalMemory(List(s2,s1), lcm(p,q), Math.max(d1,d2), a1 || a2)
        case (List(StridedBanking(s1,p), Banking(1)), List(Banking(1), StridedBanking(s2,q))) if p > 1 && q > 1 =>
          DiagonalMemory(List(s1,s2), lcm(p,q), Math.max(d1,d2), a1 || a2)
        case _ =>
          BankedMemory(b1.zip(b2).map{case(x,y) => mergeBanking(mem,x,y)}, Math.max(d1,d2), a1 || a2)
      }
      case (DiagonalMemory(strides,p,d1,a1), BankedMemory(s2,d2,a2)) =>
        val a = strides.map{x => StridedBanking(x,p) }
        BankedMemory(s2.zip(a).map{case (x,y) => mergeBanking(mem,x,y) }, Math.max(d1,d2), a1 || a2)

      case (BankedMemory(s1,d1,a1), DiagonalMemory(strides,p,d2,a2)) =>
        val a = strides.map{x => StridedBanking(x, p) }
        BankedMemory(s1.zip(a).map{case (x,y) => mergeBanking(mem,x,y) }, Math.max(d1,d2), a1 || a2)
    }
  }

  case class InstanceGroup (
    metapipe: Option[Ctrl],         // Controller if at least some accesses require n-buffering
    accesses: Seq[Access],          // All accesses within this group
    instance: Memory,               // Banking/buffering information
    duplicates: Int,                // Duplicates
    ports: Map[Access, Set[Int]],   // Set of ports each access is connected to
    swaps: Map[Access, Ctrl]        // Swap controller for done signal for n-buffering
  )


  def bankAccessGroup(
    mem:     Exp[_],
    writers: Seq[Access],
    reader:  Option[Access],
    bankAccess: (Exp[_], Exp[_]) => (Memory,Int)
  ): InstanceGroup = {
    dbg(c"  Banking group: ")
    dbg(c"    Reader: $reader")
    dbg(c"    Writers: $writers")

    val accesses = writers ++ reader

    val group = {
      if (accesses.isEmpty) InstanceGroup(None, Nil, BankedMemory(Nil,1,false), 1, Map.empty, Map.empty)
      else {
        val bankings = accesses.map{a => bankAccess(mem, a.node) }
        val memory = bankings.map(_._1).reduce{(a,b) => mergeMemory(mem, a, b) }
        val duplicates = bankings.map(_._2).max

        if (writers.isEmpty && reader.isDefined) {
          InstanceGroup(None, accesses, memory, duplicates, Map(reader.get -> Set(0)), Map.empty)
        }
        else {
          // TODO: A memory is an accumulator if a writer depends on a reader in the same pipe
          // or if this memory is used as an accumulator by a Reduce or MemReduce
          // and at least one of the writers is in the same control node as the reader
          val isAccum = reader.exists{read => writers.exists(_.node.dependsOn(read.node)) } || (mem match {
            case s: Symbol[_] => s.dependents.exists{
              case Def(e: OpReduce[_])      => e.accum == s && reader.exists{read => writers.exists(_.ctrl == read.ctrl)}
              case Def(e: OpMemReduce[_,_]) => e.accum == s && reader.exists{read => writers.exists(_.ctrl == read.ctrl)}
              case _ => false
            }
            case _ => false
          })

          val (metapipe, ports) = findMetaPipe(mem, reader.toList, writers)
          val depth = ports.values.max + 1
          val bufferedMemory = memory match {
            case BankedMemory(banks, _, _) => BankedMemory(banks, depth, isAccum)
            case DiagonalMemory(strides, banks, _, _) => DiagonalMemory(strides, banks, depth, isAccum)
          }

          metapipe match {
            // Metapipelined case: partition accesses based on whether they're n-buffered or time multiplexed w/ buffer
            case Some(parent) =>
              val (nbuf, tmux) = accesses.partition{access => lca(access.ctrl, parent).get == parent }

              def allPorts = List.tabulate(depth){i=>i}.toSet
              val bufPorts = Map(nbuf.map{a => a -> Set(ports(a)) } ++ tmux.map{a => a -> allPorts} : _*)
              val bufSwaps = Map(nbuf.map{a => a -> childContaining(parent, a) } : _*)
              InstanceGroup(metapipe, accesses, bufferedMemory, duplicates, bufPorts, bufSwaps)

            // Time-multiplexed case:
            case None =>
              val muxPorts = ports.map{case (key, port) => key -> Set(port)}
              InstanceGroup(None, accesses, bufferedMemory, duplicates, muxPorts, Map.empty)
          }
        }
      }
    }

    dbg("")
    dbg(c"  Instance: ${group.instance}")
    dbg(c"  Controller: ${group.metapipe}")
    dbg(c"  Duplicates: ${group.duplicates}")
    dbg(c"  Buffer Ports: ")
    (0 until group.instance.depth).foreach{port =>
      val portAccesses = accesses.filter{a => group.ports(a).contains(port) }
      dbg(c"    $port: " + portAccesses.mkString(", "))
    }
    group
  }


  def reachingWrites(mem: Exp[_], reader: Access) = writersOf(mem) // TODO: Account for "killing" writes, write ordering
  def coalesceMemories(mem: Exp[Any], instances: List[InstanceGroup]) = instances // TODO: Cases for coalescing?

  trait BankSettings {
    def allowMultipleReaders: Boolean   = true
    def allowMultipleWriters: Boolean   = true
    def allowConcurrentReaders: Boolean = true
    def allowConcurrentWriters: Boolean = false // Writers directly in parallel
    def allowPipelinedReaders: Boolean  = true
    def allowPipelinedWriters: Boolean  = true
  }

  def bank(mem: Exp[_], bankAccess: (Exp[_], Exp[_]) => (Memory, Int), settings: BankSettings) {
    dbg("")
    dbg("-----------------------------------")
    dbg(u"Inferring instances for memory $mem ")

    val writers = writersOf(mem)
    val readers = readersOf(mem)

    if (writers.isEmpty && !isArgIn(mem))  warn(ctxOrHere(mem), u"${mem.tp} $mem defined here has no writers!")
    if (readers.isEmpty && !isArgOut(mem)) warn(ctxOrHere(mem), u"${mem.tp} $mem defined here has no readers!")

    if (!settings.allowMultipleReaders)   checkMultipleReaders(mem)
    if (!settings.allowMultipleWriters)   checkMultipleWriters(mem)
    if (!settings.allowConcurrentReaders) checkConcurrentReaders(mem)
    if (!settings.allowConcurrentWriters) checkConcurrentWriters(mem)
    if (!settings.allowPipelinedReaders)  checkPipelinedReaders(mem)
    if (!settings.allowPipelinedWriters)  checkPipelinedWriters(mem)

    val instanceGroups = if (readers.isEmpty) {
      List(bankAccessGroup(mem, writers, None, bankAccess))
    }
    else {
      readers.map{reader =>
        val reaching = reachingWrites(mem, reader)
        bankAccessGroup(mem, reaching, Some(reader), bankAccess)
      }
    }

    val coalescedInsts = coalesceMemories(mem, instanceGroups)

    dbg("Instances inferred (after memory coalescing): ")
    var i = 0
    instanceGroups.foreach{case InstanceGroup(metapipe, accesses, instance, dups, ports, swaps) =>
      dbg(c"  #$i - ${i+dups}: $instance (x$dups)")

      accesses.foreach{access =>
        dispatchOf.add(access, mem, i)
        portsOf(access, mem, i) = ports(access)

        dbg(s"""   - $access (ports: ${ports(access).mkString(", ")}) [swap: ${swaps.get(access)}]""")
      }

      i += dups
    }

    duplicatesOf(mem) = instanceGroups.flatMap{grp => List.fill(grp.duplicates)(grp.instance) }
  }


  // --- Memory-specific banking rules

  override protected def process[S:Staged](block: Block[S]): Block[S] = {
    localMems.foreach {mem => mem.tp match {
      case _:FIFOType[_] => bank(mem, bankFIFOAccess, FIFOSettings)
      case _:SRAMType[_] => bank(mem, bankSRAMAccess, SRAMSettings)
      case _:RegType[_]  => bank(mem, bankRegAccess, RegSettings)
      case tp => throw new UndefinedBankingException(tp)(ctxOrHere(mem))
    }}
    block
  }

  object SRAMSettings extends BankSettings
  object RegSettings extends BankSettings
  object FIFOSettings extends BankSettings {
    override def allowMultipleReaders: Boolean   = false
    override def allowMultipleWriters: Boolean   = false
    override def allowConcurrentReaders: Boolean = false
    override def allowConcurrentWriters: Boolean = false
    override def allowPipelinedReaders: Boolean  = false
    override def allowPipelinedWriters: Boolean  = false
  }

  def bankSRAMAccess(mem: Exp[_], access: Exp[_]): (Memory, Int) = {
    val patterns = accessPatternOf(access)
    // TODO: SRAM Views: dimensions may change depending on view
    val dims: Seq[Int] = stagedDimsOf(mem.asInstanceOf[Exp[SRAM[_]]]).map{case Exact(c) => c.toInt}
    val allStrides = constDimsToStrides(dims)
    val strides = if (patterns.length == 1) List(allStrides.last) else allStrides

    var used: Set[Bound[Index]] = Set.empty

    // Parallelization factors relative to the accessed memory
    val factors = unrollFactorsOf(access) diff unrollFactorsOf(mem)
    val channels = factors.map{case Exact(c) => c.toInt}.product

    def bankFactor(i: Bound[Index]): Int = {
      if (!used.contains(i)) {
        used += i
        parFactorOf(i) match {case Exact(c) => c.toInt }
      }
      else 1
    }

    val banking = (patterns, strides).zipped.map{ case (pattern, stride) => pattern match {
      case AffineAccess(Exact(a),i,b) => StridedBanking(a.toInt*stride, bankFactor(i))
      case StridedAccess(Exact(a),i)  => StridedBanking(a.toInt*stride, bankFactor(i))
      case OffsetAccess(i,b)          => StridedBanking(stride, bankFactor(i))
      case LinearAccess(i)            => StridedBanking(stride, bankFactor(i))
      case InvariantAccess(b)         => NoBanking // Single "bank" in this dimension
      case RandomAccess               => NoBanking // Single "bank" in this dimension
    }}

    val banks = banking.map(_.banks).product
    val duplicates = channels / banks

    dbg(s"")
    dbg(s"  access: ${str(access)}")
    dbg(s"  pattern: $patterns")
    dbg(s"  channels: $channels")
    dbg(s"  banking: $banking")
    dbg(s"  duplicates: $duplicates")
    (BankedMemory(banking, depth = 1, isAccum = false), duplicates)
  }

  // TODO: We need to check that there is only the innermost loop parallelized relative to the FIFO
  // Otherwise, we have multiple concurrent reads/writes
  def bankFIFOAccess(mem: Exp[_], access: Exp[_]): (Memory, Int) = {
    val factors = unrollFactorsOf(access) diff unrollFactorsOf(mem)
    val channels = factors.map{case Exact(c) => c.toInt}.product
    (BankedMemory(Seq(StridedBanking(1, channels)), depth = 1, isAccum = false), 1)
  }

  def bankRegAccess(mem: Exp[_], access: Exp[_]): (Memory, Int) = {
    (BankedMemory(Seq(NoBanking), depth = 1, isAccum = false), 1)
  }
}
