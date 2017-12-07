package spatial.analysis

import argon.core._
import argon.traversal.CompilerPass
import org.virtualized.SourceContext
import spatial.aliases._
import spatial.metadata._
import spatial.models._
import spatial.nodes._
import spatial.utils._

trait MemoryAnalyzer extends CompilerPass with AffineMemoryAnalysis {
  override val name = "Memory Analyzer"
  var enableWarn = true

  def localMems: Seq[Exp[_]]

  type Channels = (Memory, Int)
  implicit class ChannelOps(x: Channels) {
    def memory = x._1
    def duplicates = x._2
    def toList: List[Memory] = List.fill(duplicates)(memory)
  }

  def mergeBanking(mem: Exp[_], a: Banking, b: Banking, d: Int): Banking = (a,b) match {
    case (Banking(s1,p,o1), Banking(s2,q,o2)) if s1 == s2 => Banking(s1, Math.min(lcm(p,q),d), o1 || o2)
    case (NoBanking(s1), Banking(s2,_,_)) if s1 == s2 => b
    case (Banking(s1,_,_), NoBanking(s2)) if s1 == s2 => a
    case _ =>
      warn(mem.ctx, u"${mem.tp}, defined here, appears to be addressed with mismatched strides")
      warn(mem.ctx)
      NoBanking(1) // TODO
  }

  def mergeChannels(mem: Exp[_], a: Channels, b: Channels): Channels = {
    val memA = a.memory
    val memB = b.memory
    val dupA = a.duplicates
    val dupB = b.duplicates

    val memC = if (memA.nDims != memB.nDims) {
      if (spatialConfig.useAffine) {
        mergeMismatchedChannels(mem, a, b)
      }
      else {
        new spatial.DimensionMismatchError(mem, memA.nDims, memB.nDims)(mem.ctx, state)
        BankedMemory(List.fill(memA.nDims)(NoBanking(1)), Math.max(memA.depth, memB.depth), memA.isAccum || memB.isAccum)
      }
    }
    else (memA,memB) match {
      case (DiagonalMemory(s1,p,d1,a1), DiagonalMemory(s2,q,d2,a2)) =>
        if (s1.zip(s2).forall{case (x,y) => x == y}) {
          DiagonalMemory(s1, lcm(p,q), Math.max(d1,d2), a1 || a2)
        }
        else {
          warn(mem.ctx, u"${mem.tp}, defined here, appears to be addressed with mismatched strides")
          warn(mem.ctx)
          BankedMemory(s1.map{_ => NoBanking(1)}, Math.max(d1,d2), memA.isAccum || memB.isAccum)
        }

      case (BankedMemory(b1,d1,a1), BankedMemory(b2, d2,a2)) => (b1,b2) match {
        case (List(Banks(1), Banking(s1,p,o1)), List(Banking(s2,q,o2), Banks(1))) if p > 1 && q > 1 && !o1 && !o2 =>
          DiagonalMemory(List(s2,s1), lcm(p,q), Math.max(d1,d2), a1 || a2)
        case (List(Banking(s1,p,o1), Banks(1)), List(Banks(1), Banking(s2,q,o2))) if p > 1 && q > 1 && !o1 && !o2 =>
          DiagonalMemory(List(s1,s2), lcm(p,q), Math.max(d1,d2), a1 || a2)
        case _ =>
          val banking = (b1,b2,constDimsOf(mem)).zipped.map{case (x,y,d) => mergeBanking(mem,x,y,d) }
          BankedMemory(banking, Math.max(d1,d2), a1 || a2)
      }
      case (DiagonalMemory(strides,p,d1,a1), BankedMemory(b2,d2,a2)) =>
        val b1 = strides.map{x => Banking(x,p,isOuter = false) }
        val banking = (b1,b2,constDimsOf(mem)).zipped.map{case (x,y,d) => mergeBanking(mem,x,y,d) }
        BankedMemory(banking, Math.max(d1,d2), a1 || a2)

      case (BankedMemory(b1,d1,a1), DiagonalMemory(strides,p,d2,a2)) =>
        val b2 = strides.map{x => Banking(x,p,isOuter = false) }
        val banking = (b1,b2,constDimsOf(mem)).zipped.map{case (x,y,d) => mergeBanking(mem,x,y,d) }
        BankedMemory(banking, Math.max(d1,d2), a1 || a2)
    }
    // Calculate duplicates
    // During merging, the requirement of duplication cannot be destroyed, only created
    val channelsA = memA.totalBanks * dupA  // Ab0 * ... * AbN * Adup   e.g. 16 * 2 * 2 = 64 or 3 * 2 * 4 = 24
    val channelsB = memB.totalBanks * dupB  // Bb0 * ... * BbN * Bdup   e.g. 1  * 1 * 1 = 1  or 2 * 2 * 4 = 16
    val channelsC = Math.max(channelsA, channelsB) // = Cb0 * ... * CbN * Cdup
    val banksC = memC.totalBanks // mrg(Ab0,Bb0) * ... * mrg(AbN,BbN)   e.g. 1  * 1     = 1  or 6 * 2 = 12

    val origDups  = Math.max(dupA, dupB)
    val extraDups = (channelsC + banksC - 1) / banksC //  = ceiling(channelsC / banksC)
    val dupC = Math.max(origDups,extraDups)
    (memC, dupC)
  }

  class InstanceGroup (
    val metapipe: Option[Ctrl],         // Controller if at least some accesses require n-buffering
    val accesses: Iterable[Access],     // All accesses within this group
    val channels: Channels,             // Banking/buffering information + duplication
    val ports: Map[Access, Set[Int]],   // Set of ports each access is connected to
    val swaps: Map[Access, Ctrl]        // Swap controller for done signal for n-buffering
  ) {

    lazy val revPorts: Array[Set[Access]] = invertPorts(accesses, ports)   // Set of accesses connected for each port
    def port(x: Int): Set[Access] = if (x >= revPorts.length) Set.empty else revPorts(x)

    def depth: Int = if (ports.values.isEmpty) 1 else ports.values.map(_.max).max+1
    // Assumes a fixed size, dual ported memory which is duplicated, both to meet duplicates and banking factors
    def normalizedCost: Int = depth * channels.duplicates * channels.memory.totalBanks

    val id: Int = { InstanceGroup.id += 1; InstanceGroup.id }
    override def toString = s"IG$id"
  }

  object InstanceGroup {
    var id = 0
  }

  def printGroup(mem: Exp[_], group: InstanceGroup, showErrors: Boolean = false): Unit = {
    val writers = writersOf(mem)
    val readers = readersOf(mem)
    dbg("")
    dbg(c"  Name: $group")
    dbg(c"  Instance: ${group.channels.memory}")
    dbg(c"  Duplicates: ${group.channels.duplicates}")
    dbg(c"  Controller: ${group.metapipe}")
    dbg(c"  Buffer Ports: ")
    group.revPorts.zipWithIndex.foreach{case (portAccesses,port) =>
      val writes = portAccesses.filter(access => writers.contains(access))
      val reads  = portAccesses.filter(access => readers.contains(access))
      val concurrentWrites = findAccesses(writes.toList){(a,b) => areConcurrent(a,b) || arePipelined(a,b) }
      if (concurrentWrites.nonEmpty && showErrors) {
        bug(mem.ctx, s"Instance $group for writer $mem appears to have multiple concurrent writers on port #$port")
        concurrentWrites.foreach{case (a,b) =>
          error(c"$a / $b [LCA = ${lcaWithCoarseDistance(a,b)}]")
        }
        error(mem.ctx)
      }
      dbg(c"    $port [Wr]: " + writes.mkString(", "))
      dbg(c"    $port [Rd]: " + reads.mkString(", "))
    }

    // Time multiplexed writes are allowed (e.g. for preloading the memory or clearing it)
    val pipelinedWrites = writers.filterNot{write => group.revPorts.forall{accesses => accesses.contains(write) }}
    val portsWithWrites = group.revPorts.filter{portAccesses => portAccesses.exists{access => pipelinedWrites.contains(access) } }

    if (portsWithWrites.length > 1 && !isExtraBufferable(mem) && showErrors) {
      val obj = if (isSRAM(mem)) "SRAM" else if (isReg(mem)) "Reg" else if (isRegFile(mem)) "RegFile" else "???"
      error(mem.ctx, u"Memory $mem was inferred to be an N-Buffer with writes at multiple ports.")
      error("This behavior is disallowed by default, as it is usually not correct.")
      error(u"To enable this behavior, declare the memory using:")
      error(u"""  val ${mem.name.getOrElse("sram")} = $obj.buffer[${mem.tp.typeArguments.head}](dimensions)""")
      error("Otherwise, disable outer loop pipelining using the Sequential controller tag.")
      error(mem.ctx)
    }
  }

  def invertPorts(accesses: Iterable[Access], ports: Map[Access, Set[Int]]): Array[Set[Access]] = {
    val depth = if (ports.values.isEmpty) 1 else ports.values.map(_.max).max + 1
    Array.tabulate(depth){port =>
      accesses.filter{a => ports(a).contains(port) }.toSet
    }
  }


  def bankAccessGroup(
    mem:     Exp[_],
    writers: Seq[Access],
    reader:  Option[Access],
    bankAccess: (Exp[_], Exp[_]) => Channels
  ): InstanceGroup = {
    dbg(c"  Banking group: ")
    dbg(c"    Reader: $reader")
    dbg(c"    Writers: $writers")

    val accesses = writers ++ reader

    val group = {
      if (accesses.isEmpty) new InstanceGroup(None, Nil, (BankedMemory(Nil,1,false), 1), Map.empty, Map.empty)
      else {
        val bankings = accesses.map{a => bankAccess(mem, a.node) }
        val channels = bankings.reduce{(a,b) => mergeChannels(mem, a, b) }

        if (writers.isEmpty && reader.isDefined) {
          new InstanceGroup(None, accesses, channels, Map(reader.get -> Set(0)), Map.empty)
        }
        else {
          // TODO: A memory is an accumulator if a writer depends on a reader in the same pipe
          // or if this memory is used as an accumulator by a Reduce or MemReduce
          // and at least one of the writers is in the same control node as the reader
          val isAccum = reader.exists{read => writers.exists(_.node.dependsOn(read.node)) } || (mem match {
            case s: Dyn[_] => s.dependents.exists{
              case Def(e: OpReduce[_])      => e.accum == s && reader.exists{read => writers.exists(_.ctrl == read.ctrl)}
              case Def(e: OpMemReduce[_,_]) => e.accum == s && reader.exists{read => writers.exists(_.ctrl == read.ctrl)}
              case _ => false
            }
            case _ => false
          })

          val (metapipe, ports) = findMetaPipe(mem, reader.toList, writers)
          val depth = ports.values.max + 1
          // Update memory instance with correct depth
          val bufferedMemory = channels.memory match {
            case BankedMemory(banks, _, _) => BankedMemory(banks, depth, isAccum)
            case DiagonalMemory(strides, banks, _, _) => DiagonalMemory(strides, banks, depth, isAccum)
          }
          val bufferedChannels = (bufferedMemory, channels.duplicates)

          metapipe match {
            // Metapipelined case: partition accesses based on whether they're n-buffered or time multiplexed w/ buffer
            case Some(parent) =>
              val (nbuf, tmux) = accesses.partition{access => lca(access.ctrl, parent).get == parent }

              def allPorts = List.tabulate(depth){i=>i}.toSet
              val bufPorts = Map(nbuf.map{a => a -> Set(ports(a)) } ++ tmux.map{a => a -> allPorts} : _*)
              val bufSwaps = Map(nbuf.map{a => a -> childContaining(parent, a) } : _*)
              new InstanceGroup(metapipe, accesses, bufferedChannels, bufPorts, bufSwaps)

            // Time-multiplexed case:
            case None =>
              val muxPorts = ports.map{case (key, port) => key -> Set(port)}
              new InstanceGroup(None, accesses, bufferedChannels, muxPorts, Map.empty)
          }
        }
      }
    }

    printGroup(mem, group)
    group
  }


  def reachingWrites(mem: Exp[_], reader: Access) = writersOf(mem) // TODO: Account for "killing" writes, write ordering

  // TODO: Other cases for coalescing?
  def coalesceMemories(mem: Exp[_], instances: List[InstanceGroup]): List[InstanceGroup] = {
    val writers = writersOf(mem).toSet
    val readers = readersOf(mem).toSet

    def canMerge(grps: Set[InstanceGroup]): (Boolean, Int, Option[Ctrl]) = if (grps.size > 1) {
      //dbg(c"  Checking merge of $grps: ")
      val depth = grps.map(_.depth).max
      val parents = grps.map(_.metapipe)
      val isLegal = (parents.size == 1) && (0 until depth).forall{p =>
        val accesses = grps.flatMap{g => g.port(p) }
        val readPorts  = accesses intersect readers
        val writePorts = accesses intersect writers
        //dbg(c"    $p: READ:  $readPorts [${readPorts.size}]")
        //dbg(c"    $p: WRITE: $writePorts [${writePorts.size}]")
        readPorts.size <= 1 && writePorts.size <= 1
      }
      //dbg(c"    canMerge: $isLegal")
      (isLegal, depth, parents.head)
    }
    else if (grps.size == 1) (true, grps.head.depth, grps.head.metapipe)
    else (false, 0, None)

    def isLegalPartition(grps: Set[InstanceGroup]): Boolean = {
      if (grps.size > 1) canMerge(grps)._1
      else if (grps.size == 1) true
      else false
    }

    def merge(groups: Set[InstanceGroup]): InstanceGroup = if (groups.size > 1) {
      val (can, depth, parent) = canMerge(groups)
      assert(can)
      val accesses = groups.flatMap(_.accesses)
      val ports = accesses.map{access =>
        access -> groups.flatMap(_.ports.getOrElse(access, Set.empty))
      }.toMap

      new InstanceGroup(
        parent,
        accesses,
        channels = groups.map(_.channels).reduce{(a,b) => mergeChannels(mem, a, b) },
        ports,
        groups.map(_.swaps).reduce{(a,b) => a ++ b}
      )
    } else {
      assert(groups.nonEmpty, "Can't merge empty Set")
      groups.head
    }

    // TODO: Good functional way to express this stuff?
    // Find the groupings with the smallest resulting estimated cost
    // Unfortunately, "merge everything all the time if possible" isn't necessarily the best course of action
    // e.g. if we have a buffer with depth 2, banking of 2 and buffer depth 3 with banking of 3,
    // merging the two together will require depth 3 with banking of 6
    // Fortunately, the number of groups here is generally small (1 - 5), so runtime shouldn't be too much of an issue

    // 1. Greedily merge all cases where the the lcm is bounded by either a or b
    // cost(x) = Product( x.bankings ) * x.depth * x.duplicates
    // cost( Merge(x,y) ) = Merge(x,y).depth * Merge(x,y).duplicates * Product( Merge(x,y).bankings )
    //                    = max(x.depth, y.depth) * max(x.duplicates,y.duplicates) * Product( lcm(x.b0,y.b0), ... )
    //
    // Want: cost( Merge(x,y) ) <= cost(x) + cost(y)
    //
    // This condition holds if lcm is less than or equal to both banking factors (i.e. one is a divisor of the other)
    def greedyBufferMerge(instances: Set[InstanceGroup]): Set[InstanceGroup] = {
      // Group by port conflicts
      var instsIn = instances
      var exclusiveGrps = Set.empty[Set[InstanceGroup]]
      while (instsIn.nonEmpty) {
        val grp = instsIn.head
        val exclusive = instsIn.filter{g => !isLegalPartition(Set(grp, g)) } + grp
        exclusiveGrps += exclusive
        instsIn --= exclusive
      }

      // Combine zero or one groups from each mutually exclusive group
      var instsMerge = Set.empty[InstanceGroup]
      while (exclusiveGrps.exists(_.nonEmpty)) {
        val grp = exclusiveGrps.find(_.nonEmpty).get.head
        val mergeGrp = exclusiveGrps.flatMap{g =>
          g.find(_.channels.memory.costBasisBanks.zip(grp.channels.memory.costBasisBanks).forall{case (a,b) => a % b == 0 || b % a == 0 })
        }
        val merged = merge(mergeGrp)

        instsMerge += merged
        exclusiveGrps = exclusiveGrps.map(_ diff mergeGrp)
      }
      instsMerge
    }

    def exhaustiveBufferMerge(instances: Set[InstanceGroup]): Set[InstanceGroup] = {
      // This function generates all possible partitions of the given set.
      // The size of this is the Bell number, which doesn't have an entirely straightforward
      // representation in terms of N, but looks roughly exponential.
      // Note that by N = 10, the size of the output is over 100,000 and grows by roughly an order of magnitude
      // with every increment in N thereafter.
      // First 10 Bell numbers (excluding B0): 1, 2, 5, 15, 52, 203, 877, 4140, 21147, 115975
      // FIXME: Will see some repeats of some partitions - need to figure out how to avoid this
      def partitions[T](elements: Set[T], canUse: Set[T] => Boolean = {x: Set[T] => true}): Iterator[Set[Set[T]]] = {
        val N = elements.size

        def getPartitions(elems: Set[T], max: Int, depth: Int = 0): Iterator[Set[Set[T]]] = {
          if (elems.isEmpty) Set(Set.empty[Set[T]]).iterator
          else {
            Iterator.range(1, max+1).flatMap{c: Int =>
              val subsets = elems.subsets(c)
              val subs = if (c == 1) subsets.take(1) else subsets // FIXME: repeats

              subs.filter(canUse).flatMap{ part: Set[T] =>
                getPartitions(elems -- part, c, depth+1).map{more: Set[Set[T]] => more + part}
              }
            }
          }
        }
        getPartitions(elements, N)
      }

      def costOf(x: Set[InstanceGroup]) = x.toList.map(_.normalizedCost).sum

      if (instances.size > 1 && instances.size < 9) {
        dbg("")
        dbg("")
        //dbg(c"Attempting to coalesce instances: ")
        //instances.foreach(printGroup)

        var best = instances
        var bestCost: Int = costOf(best)

        // !!!VERY, VERY EXPENSIVE!!! ONLY USE IF NUMBER OF GROUPS IS < 10
        partitions(best, isLegalPartition).foreach{proposed =>
          val part = proposed.map(merge)
          val cost = costOf(part)

          dbg("  Proposed partitioning: ")
          part.foreach(part => printGroup(mem, part))
          dbg(s"  Cost: $cost")

          if (cost < bestCost) {
            best = part
            bestCost = cost
          }
        }

        best
      }
      else instances
    }


    instances.groupBy(_.metapipe).toList.flatMap{
      // Merging for buffered memories
      case (Some(metapipe), instances) =>
        val insts = greedyBufferMerge(instances.toSet)

        dbg(c"After greedy: ")
        insts.foreach(inst => printGroup(mem, inst))

        // 2. Coalesce remaining instance groups based on brute force search, if it's feasible
        exhaustiveBufferMerge(insts).toList

      // TODO: Merging for time multiplexed?
      case (None, instances) => instances
    }
  }

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
    dbg("")
    dbg("-----------------------------------")
    dbg(u"Inferring instances for memory ${str(mem)}")

    val writers = writersOf(mem)
    val readers = readersOf(mem)

    if (writers.isEmpty && !isOffChipMemory(mem) && !isLUT(mem) && initialDataOf(mem).isEmpty) {
      warn(mem.ctx, u"${mem.tp} $mem defined here has no writers.")
      warn(mem.ctx)
    }
    if (readers.isEmpty && !isOffChipMemory(mem)) {
      warn(mem.ctx, u"${mem.tp} $mem defined here has no readers.")
      warn(mem.ctx)
    }

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

    val coalescedGroups = if (spatialConfig.enableBufferCoalescing) coalesceMemories(mem, instanceGroups) else instanceGroups

    dbg("")
    dbg("")
    dbg(u"  SUMMARY for memory $mem:")
    var i = 0
    coalescedGroups.zipWithIndex.foreach{case (grp,x) =>
      printGroup(mem, grp, showErrors = x == 0)

      grp.accesses.foreach{access =>
        if (writers.contains(access)) {
          for (j <- i until i+grp.channels.duplicates) {
            dispatchOf.add(access, mem, j)
            portsOf(access, mem, j) = grp.ports(access)
          }
        }
        else {
          dispatchOf.add(access, mem, i)
          portsOf(access, mem, i) = grp.ports(access)
        }
      }

      i += grp.channels.duplicates
    }

    duplicatesOf(mem) = coalescedGroups.flatMap{grp => grp.channels.toList }
  }





  // --- Memory-specific banking rules
  object SRAMSettings extends BankSettings
  object RegSettings extends BankSettings
  object FIFOSettings extends BankSettings {
    override def allowMultipleReaders: Boolean   = true
    override def allowMultipleWriters: Boolean   = true
    override def allowConcurrentReaders: Boolean = false
    override def allowConcurrentWriters: Boolean = false
    override def allowPipelinedReaders: Boolean  = false
    override def allowPipelinedWriters: Boolean  = false
  }
  object FILOSettings extends BankSettings {
    override def allowMultipleReaders: Boolean   = true
    override def allowMultipleWriters: Boolean   = true
    override def allowConcurrentReaders: Boolean = false
    override def allowConcurrentWriters: Boolean = false
    override def allowPipelinedReaders: Boolean  = false
    override def allowPipelinedWriters: Boolean  = false
  }
  object LineBufferSettings extends BankSettings
  object RegFileSettings extends BankSettings


  override protected def process[S:Type](block: Block[S]): Block[S] = {
    run()
    shouldWarn = false // Don't warn user after first run (avoid duplicate warnings)
    block
  }

  def run(): Unit = {
    // Reset metadata prior to running memory analysis
    metadata.clearAll[AccessDispatch]
    metadata.clearAll[PortIndex]

    localMems.foreach {mem => mem.tp match {
      case _:FIFOType[_] => bank(mem, bankFIFOAccess, FIFOSettings)
      case _:FILOType[_] => bank(mem, bankFIFOAccess, FILOSettings)
      case _:SRAMType[_] => bank(mem, bankSRAMAccess, SRAMSettings)
      case _:RegType[_]  => bank(mem, bankRegAccess, RegSettings)
      case _:LineBufferType[_] => bank(mem, bankLineBufferAccess, LineBufferSettings)
      case _:RegFileType[_]   => bank(mem, bankRegFileAccess, RegFileSettings)
      case _:LUTType[_]  => bank(mem, bankRegFileAccess, RegFileSettings)

      case _:StreamInType[_]  => bankStream(mem)
      case _:StreamOutType[_] => bankStream(mem)
      case _:BufferedOutType[_] => bankBufferOut(mem)
      case tp => throw new spatial.UndefinedBankingException(tp)(mem.ctx, state)
    }}

    if (config.verbosity > 0) {
      import scala.language.existentials
      val target = spatialConfig.target
      val areaModel = target.areaModel

      withLog(config.logDir, "Memories.report") {
        localMems.map{case mem @ Def(d) =>
          val area = areaModel.areaOf(mem,d, inHwScope = true, inReduce = false)
          mem -> area
        }.sortWith((a,b) => a._2 < b._2).foreach{case (mem,area) =>
          dbg(u"${mem.ctx}: ${mem.tp}: ${mem}")
          dbg(mem.ctx.lineContent.getOrElse(""))
          dbg(c"  ${str(mem)}")
          val duplicates = duplicatesOf(mem)
          dbg(c"Duplicates: ${duplicates.length}")
          dbg(c"Area: " + area.toString)
          duplicates.zipWithIndex.foreach{
            case (BankedMemory(banking, depth, _), i) =>
              val banks = banking.map(_.banks).mkString(", ")
              dbg(c"  #$i: Banked. Banks: ($banks), Depth: $depth")
            case (DiagonalMemory(strides,banks,depth,_), i) =>
              dbg(c"  #$i: Diagonal. Banks: $banks, Depth: $depth")
          }
          dbg("")
          dbg("")
        }
      }
    }
  }


  def indexPatternsToBanking(mem: Exp[_], access: Exp[_], patterns: Seq[IndexPattern], strides: Seq[Int]): Seq[Banking] = {
    var used: Set[Exp[Index]] = Set.empty

    def bankFactor(i: Exp[Index]): Int = {
      if (!used.contains(i)) {
        used += i
        val p = parFactorOf(i) match {case Exact(c) => c.toInt }

        dbgs(s"  bank factor of $i: $p [first use]")
        p
      }
      else {
        dbgs(s"  bank factor of $i: 1 [second+ use]")
        1
      }
    }
    def isOuter(i: Exp[Index]): Boolean = ctrlOf(i).exists(isOuterControl)

    val banking = if (patterns.exists(_.isGeneral) && spatialConfig.useAffine) {

      import argon.analysis._


      dbg(u"$mem uses generalized affine patterns")
      dbg(u"${str(access)}")
      dbg(access.ctx.lineContent.getOrElse(""))
      dbg(patterns.mkString(", "))
      dbg("")
      patterns.zip(strides).flatMap{
        case (AffineAccess(Exact(a),i,b),s) => Seq(Banking(a.toInt*s, bankFactor(i), isOuter(i)))
        case (StridedAccess(Exact(a),i), s) => Seq(Banking(a.toInt*s, bankFactor(i), isOuter(i)))
        case (OffsetAccess(i,b), s)         => Seq(Banking(s, bankFactor(i), isOuter(i)))
        case (LinearAccess(i), s)           => Seq(Banking(s, bankFactor(i), isOuter(i)))
        case (InvariantAccess(b), s)        => Seq(NoBanking(s))
        case (RandomAccess, s)              => Seq(NoBanking(s))
        case (GeneralAffine(products,offset),s) =>
          if (products.nonEmpty) {
            // TODO: Insert check to make sure all these accesses are definitely disjoint
            // i.e. the stride of each index should be guaranteed to be at least the
            // product of the dimensions of the iteration space below it
            //
            // If this check fails, the addresses aren't guaranteed to be distinct.. so what then?
            val is = products.map(_.i).distinct
            if (is.length == products.length) { // NOTE: this should always be true... i + i is remapped to (1 + 1)*i because I'm smart like that
              val banks = products.map(p => bankFactor(p.i)).product
              val isOut = products.exists(p => isOuter(p.i))
              Seq(Banking(s, banks, isOut))
            }
            else {
              // TODO: is this really the correct thing to do in general?
              products.map{case AffineProduct(af, i) =>
                Banking(af.eval { case Exact(c) => c.toInt } * s, bankFactor(i), isOuter(i))
              }.sortBy(x => -x.stride)
            }
          }
          else Seq(NoBanking(s))
      }
    }
    else (patterns, strides).zipped.map{ case (pattern, stride) => pattern match {
      case AffineAccess(Exact(a),i,b) => Banking(a.toInt*stride, bankFactor(i), isOuter(i))
      case StridedAccess(Exact(a),i)  => Banking(a.toInt*stride, bankFactor(i), isOuter(i))
      case OffsetAccess(i,b)          => Banking(stride, bankFactor(i), isOuter(i))
      case LinearAccess(i)            => Banking(stride, bankFactor(i), isOuter(i))
      case InvariantAccess(b)         => NoBanking(stride) // Single "bank" in this dimension
      case RandomAccess               => NoBanking(stride) // Single "bank" in this dimension
      case _                          => NoBanking(stride)
    }}

    banking
  }

  def bankSRAMAccess(mem: Exp[_], access: Exp[_]): Channels = {
    val patterns = accessPatternOf(access)
    // TODO: SRAM Views: dimensions may change depending on view
    val dims: Seq[Int] = stagedDimsOf(mem).map{case Exact(c) => c.toInt}
    val allStrides = constDimsToStrides(dims)
    val strides = if (patterns.length == 1) List(allStrides.last) else allStrides

    // Parallelization factors relative to the accessed memory
    val factors = unrollFactorsOf(access) diff unrollFactorsOf(mem)
    dbg(c"  Unroll factors: " + factors.flatten.map{case x @ Exact(c) => s"$x ($c)"}.mkString(", "))
    val channels = factors.flatten.map{case Exact(c) => c.toInt}.product

    val banking = indexPatternsToBanking(mem, access, patterns, strides)

    val banks = banking.map(_.banks).product
    val duplicates = Math.max(channels / banks, 1)

    dbg(s"")
    dbg(s"  access: ${str(access)}")
    dbg(s"  pattern: $patterns")
    dbg(s"  channels: $channels")
    dbg(s"  banking: $banking")
    dbg(s"  duplicates: $duplicates")
    // Generally unsafe to have a writer which requires duplication (reads are ok though)
    if (duplicates > 1 && writersOf(mem).exists(_.node == access)) {
      error(access.ctx, u"Unsafe parallelization of write to ${mem.tp} $mem")
      error(c"Either move memory relative to write or decrease parallelization.")
      error(access.ctx)
    }
    (BankedMemory(banking, depth = 1, isAccum = false), duplicates)
  }


  def bankFIFOAccess(mem: Exp[_], access: Exp[_]): Channels = {
    val factors = unrollFactorsOf(access) diff unrollFactorsOf(mem)
    // All parallelization factors relative to the memory, except the innermost, must either be empty or only contain 1s
    // Otherwise, we have multiple concurrent reads/writes
    val innerLoopParOnly = factors.drop(1).forall{x => x.isEmpty || x.forall{case Exact(c) => c == 1; case _ => false} }
    if (!innerLoopParOnly) {
      error(access.ctx, u"Access to memory $mem has outer loop parallelization relative to the memory definition")
      error("Concurrent readers and writers of the same memory are disallowed for FIFOs.")
      error(access.ctx)
    }

    val channels = factors.flatten.map{case Exact(c) => c.toInt}.product
    (BankedMemory(Seq(Banking(1, channels, false)), depth = 1, isAccum = false), 1)
  }

  // TODO: Concurrent writes to registers should be illegal
  def bankRegAccess(mem: Exp[_], access: Exp[_]): Channels = {
    val factors = unrollFactorsOf(access) diff unrollFactorsOf(mem)
    val duplicates = factors.flatten.map{case Exact(c) => c.toInt}.product

    (BankedMemory(Seq(NoBanking(1)), depth = 1, isAccum = false), duplicates)
  }

  def bankLineBufferAccess(mem: Exp[_], access: Exp[_]): Channels = {
    val factors  = unrollFactorsOf(access) diff unrollFactorsOf(mem)
    val channels = factors.flatten.map{case Exact(c) => c.toInt}.product

    val dims: Seq[Int] = stagedDimsOf(mem.asInstanceOf[Exp[SRAM[_]]]).map{case Exact(c) => c.toInt}
    val strides = constDimsToStrides(dims)

    // TODO: Like FIFO, should not allow outer loop parallelization w.r.t. LineBuffer for enqueue operations

    // Simple check, fragile if load structure ever changes.  If this is ParLineBufferRotateEnq with rows =/= lb stride, then this is transient. We get rows from parent of parent of access' counter
    val rowstride = mem match {case Def(LineBufferNew(_,_,stride)) => stride match {case Exact(c) => c.toInt; case _ => 0}; case _ => 0}
    val rowsWritten = access match {
      case Def(DenseTransfer(_,_,_,dims,strides,_,_,_,_)) => dims.zip(strides).dropRight(1).last match {case (Exact(c: BigInt), Exact(st: BigInt)) => c/st}
      case Def(_: LineBufferLoad[_]) => rowstride // Not transient
      case Def(_: ParLineBufferLoad[_]) => rowstride // Not transient
      case Def(_: LineBufferColSlice[_]) => rowstride // Not transient
      case _ => 
        if (parentOf(access).isDefined) {
          if (parentOf(parentOf(access).get).isDefined) {
            // Console.println(s"parent 1 is ${parentOf(access).get}, parent 2 is ${parentOf(parentOf(access).get).get}")
            val prnt = parentOf((parentOf(access).get)).get
            val counter_holder = if (styleOf(prnt) == StreamPipe) {prnt} else parentOf(prnt).get
            counter_holder match {
              case Def(UnrolledForeach(_,cchain,_,_,_)) => 
                cchain match {case Def(CounterChainNew(ctrs)) => ctrs.last match {
                  case Def(CounterNew(s,e,str,p)) => (s,e) match {
                    case (Exact(st), Exact(e)) => e - st
                    case _ => throw new Exception(s"Cannot load variable number of rows into linebuffer $mem, since we cannot determine which is the transient")
                  }
                }
              }
              case Def(OpForeach(_,cchain,_,_)) =>             
                cchain match {case Def(CounterChainNew(ctrs)) => ctrs.last match {
                  case Def(CounterNew(s,e,str,p)) => (s,e) match {
                    case (Exact(st), Exact(e)) => e - st
                    case _ => throw new Exception(s"Cannot load variable number of rows into linebuffer $mem, since we cannot determine which is the transient")
                  }
                }
              }
              case Def(UnitPipe(_,_)) => 1
              case Def(Hwblock(_,_)) => // This seems to be first mem analyzer pass
                access match {
                  case Def(DenseTransfer(_,_,_,dims,strides,_,_,_,_)) => 
                    dims.zip(strides).dropRight(1).last match {case (Exact(c: BigInt), Exact(st: BigInt)) => c/st}
                }
              case _ => 0
            }        

          } else 0
        } else 0
      }
    // Console.println(s"checking $mem $access for $rowstride $rowsWritten")
    isTransient(access) = if (rowstride == rowsWritten) false else true

    // TODO: Is inner loop here?
    val banking = access match {
      case Def(LineBufferColSlice(_,row,col,Exact(len))) => Seq(Banking(strides.head, dims.head, false), Banking(strides(1), len.toInt, true))
      case Def(LineBufferRowSlice(_,row,Exact(len),col)) => Seq(Banking(strides.head, dims.head, true),  NoBanking(1))
      case Def(LineBufferEnq(_,_,_))                     => Seq(Banking(strides.head, dims.head, false), Banking(strides(1), channels, true))
      case Def(LineBufferRotateEnq(_,_,_,_))             => Seq(Banking(strides.head, dims.head, false), Banking(strides(1), channels, true))
      case Def(LineBufferLoad(_,row,col,_)) =>
        val patterns = accessPatternOf(access)
        indexPatternsToBanking(mem, access, patterns, strides)

      case _ =>
        val patterns = accessPatternOf(access)
        NoBanking(strides.head) +: indexPatternsToBanking(mem, access, patterns, strides) // Everything else uses 1D view of line buffer
    }

    val banks = banking.map(_.banks).product
    val duplicates = Math.max(channels / banks, 1)

    (BankedMemory(banking, depth=1, isAccum=false), duplicates)
  }

  def bankRegFileAccess(mem: Exp[_], access: Exp[_]): Channels = {
    val dims: Seq[Int] = stagedDimsOf(mem).map{case Exact(c) => c.toInt}
    val strides = constDimsToStrides(dims)

    val factors  = unrollFactorsOf(access) diff unrollFactorsOf(mem)
    val channels = factors.flatten.map{case Exact(c) => c.toInt}.product

    def bankFactor(i: Exp[Index]) = parFactorOf(i) match { case Exact(c) => c.toInt }

    val patterns = accessPatternOf(access)
    val banking = indexPatternsToBanking(mem, access, patterns, strides)

    val banks = banking.map(_.banks).product
    val duplicates = Math.max(channels / banks, 1)

    (BankedMemory(banking, 1, isAccum = false), duplicates)
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



  def bankBufferOut(buffer: Exp[_]): Unit = {
    dbg("")
    dbg("")
    dbg("-----------------------------------")
    dbg(u"Inferring instances for memory ${str(buffer)}")

    val dims: Seq[Int] = stagedDimsOf(buffer).map{case Exact(c) => c.toInt}
    val allStrides = constDimsToStrides(dims)

    val reads = readersOf(buffer)
    val writes = writersOf(buffer)
    val accesses = reads ++ writes

    if (reads.nonEmpty) {
      error(reads.head.node.ctx, s"BufferedOut had read ${str(reads.head.node)}")
      error(reads.head.node.ctx)
    }

    accesses.foreach{access =>
      dispatchOf.add(access, buffer, 0)
      portsOf(access, buffer, 0) = Set(0)
    }

    val channels = accesses.map{access =>
      val patterns = accessPatternOf(access.node)
      val strides = if (patterns.length == 1) List(allStrides.last) else allStrides

      // Parallelization factors relative to the accessed memory
      val factors = unrollFactorsOf(access.node)
      val channels = factors.flatten.map{case Exact(c) => c.toInt}.product

      val banking = indexPatternsToBanking(buffer, access.node, patterns, strides)
      val banks = banking.map(_.banks).product
      val duplicates = channels / banks

      if (duplicates > 1) {
        error(access.node.ctx, u"Not able to parallelize random write to BufferedOut $buffer")
        error(access.node.ctx)
      }

      dbg(c"  access:   ${str(access.node)}")
      dbg(c"  patterns: $patterns")
      dbg(c"  banking:  $banking")

      (BankedMemory(banking, depth = 1, isAccum = false), duplicates) : Channels
    }

    if (channels.nonEmpty) {
      val instance = channels.reduce{(a,b) => mergeChannels(buffer, a, b) }

      dbg(c"instance for $buffer: $instance")

      duplicatesOf(buffer) = List(instance._1)
    }
    else {
      duplicatesOf(buffer) = Nil
    }
  }

}
