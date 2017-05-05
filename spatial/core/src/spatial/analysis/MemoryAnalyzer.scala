package spatial.analysis

import argon.traversal.CompilerPass
import spatial._
import org.virtualized.SourceContext

trait MemoryAnalyzer extends CompilerPass {
  val IR: SpatialExp
  import IR._

  def localMems: Seq[Exp[_]]

  override val name = "Memory Analyzer"
  var enableWarn = true

  type Channels = (Memory, Int)
  implicit class ChannelOps(x: Channels) {
    def memory = x._1
    def duplicates = x._2
    def toList: List[Memory] = List.fill(duplicates)(memory)
  }

  def mergeBanking(mem: Exp[_], a: Banking, b: Banking): Banking = (a,b) match {
    case (StridedBanking(s1,p), StridedBanking(s2,q)) if s1 == s2 => StridedBanking(s1, lcm(p,q))
    case (NoBanking, _) => b
    case (_, NoBanking) => a
    case _ =>
      warn(mem.ctx, u"${mem.tp}, defined here, appears to be addressed with mismatched strides")
      warn(mem.ctx)
      NoBanking
  }

  def mergeChannels(mem: Exp[_], a: Channels, b: Channels): Channels = {
    val memA = a.memory
    val memB = b.memory
    val dupA = a.duplicates
    val dupB = b.duplicates

    val memC = if (memA.nDims != memB.nDims) {
      new DimensionMismatchError(mem, memA.nDims, memB.nDims)(mem.ctx)
      BankedMemory(List.fill(memA.nDims)(NoBanking), Math.max(memA.depth,memB.depth), memA.isAccum || memB.isAccum)
    }
    else (memA,memB) match {
      case (DiagonalMemory(s1,p,d1,a1), DiagonalMemory(s2,q,d2,a2)) =>
        if (s1.zip(s2).forall{case (x,y) => x == y}) {
          DiagonalMemory(s1, lcm(p,q), Math.max(d1,d2), a1 || a2)
        }
        else {
          warn(mem.ctx, u"${mem.tp}, defined here, appears to be addressed with mismatched strides")
          warn(mem.ctx)
          BankedMemory(s1.map{_ => NoBanking}, Math.max(d1,d2), memA.isAccum || memB.isAccum)
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

    def depth = if (ports.values.isEmpty) 1 else ports.values.map(_.max).max+1
    // Assumes a fixed size, dual ported memory which is duplicated, both to meet duplicates and banking factors
    def normalizedCost = depth * channels.duplicates * channels.memory.totalBanks

    val id = { InstanceGroup.id += 1; InstanceGroup.id }
    override def toString = s"IG$id"
  }

  object InstanceGroup {
    var id = 0
  }

  def printGroup(group: InstanceGroup): Unit = {
    dbg("")
    dbg(c"  Name: $group")
    dbg(c"  Instance: ${group.channels.memory}")
    dbg(c"  Duplicates: ${group.channels.duplicates}")
    dbg(c"  Controller: ${group.metapipe}")
    dbg(c"  Buffer Ports: ")
    group.revPorts.zipWithIndex.foreach{case (portAccesses,port) =>
      dbg(c"    $port: " + portAccesses.mkString(", "))
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

    printGroup(group)
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
          part.foreach(printGroup)
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
        insts.foreach(printGroup)

        // 2. Coalesce remaining instance groups based on brute force search, if it's feasible
        exhaustiveBufferMerge(insts).toList

      // TODO: Merging for time multiplexed
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
    dbg(u"Inferring instances for memory $mem ")

    val writers = writersOf(mem)
    val readers = readersOf(mem)

    if (writers.isEmpty && !isOffChipMemory(mem)) {
      warn(mem.ctx, u"${mem.tp} $mem defined here has no writers!")
      warn(mem.ctx)
    }
    if (readers.isEmpty && !isOffChipMemory(mem)) {
      warn(mem.ctx, u"${mem.tp} $mem defined here has no readers!")
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

    val coalescedGroups = coalesceMemories(mem, instanceGroups)

    dbg("")
    dbg("")
    dbg(u"  SUMMARY for memory $mem:")
    var i = 0
    coalescedGroups.foreach{grp =>
      printGroup(grp)

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
    override def allowMultipleReaders: Boolean   = false
    override def allowMultipleWriters: Boolean   = false
    override def allowConcurrentReaders: Boolean = false
    override def allowConcurrentWriters: Boolean = false
    override def allowPipelinedReaders: Boolean  = false
    override def allowPipelinedWriters: Boolean  = false
  }
  object LineBufferSettings extends BankSettings
  object RegFileSettings extends BankSettings


  override protected def process[S:Type](block: Block[S]): Block[S] = {
    // Reset metadata prior to running memory analysis
    metadata.clearAll[AccessDispatch]
    metadata.clearAll[PortIndex]

    localMems.foreach {mem => mem.tp match {
      case _:FIFOType[_] => bank(mem, bankFIFOAccess, FIFOSettings)
      case _:SRAMType[_] => bank(mem, bankSRAMAccess, SRAMSettings)
      case _:RegType[_]  => bank(mem, bankRegAccess, RegSettings)
      case _:LineBufferType[_] => bank(mem, bankLineBufferAccess, LineBufferSettings)
      case _:RegFileType[_]   => bank(mem, bankRegFileAccess, RegFileSettings)
      case _:StreamInType[_]  => bankStream(mem)
      case _:StreamOutType[_] => bankStream(mem)
      case _:BufferedOutType[_] => bankBufferOut(mem)
      case tp => throw new UndefinedBankingException(tp)(mem.ctx)
    }}

    shouldWarn = false // Don't warn user after first run (avoid duplicate warnings)
    block
  }


  def indexPatternsToBanking(patterns: Seq[IndexPattern], strides: Seq[Int]): Seq[Banking] = {
    var used: Set[Bound[Index]] = Set.empty

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
    val channels = factors.flatten.map{case Exact(c) => c.toInt}.product

    val banking = indexPatternsToBanking(patterns, strides)

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
    (BankedMemory(Seq(StridedBanking(1, channels)), depth = 1, isAccum = false), 1)
  }

  // TODO: Concurrent writes to registers should be illegal
  def bankRegAccess(mem: Exp[_], access: Exp[_]): Channels = {
    val factors = unrollFactorsOf(access) diff unrollFactorsOf(mem)
    val duplicates = factors.flatten.map{case Exact(c) => c.toInt}.product

    (BankedMemory(Seq(NoBanking), depth = 1, isAccum = false), duplicates)
  }

  def bankLineBufferAccess(mem: Exp[_], access: Exp[_]): Channels = {
    val factors  = unrollFactorsOf(access) diff unrollFactorsOf(mem)
    val channels = factors.flatten.map{case Exact(c) => c.toInt}.product

    val dims: Seq[Int] = stagedDimsOf(mem.asInstanceOf[Exp[SRAM[_]]]).map{case Exact(c) => c.toInt}
    val strides = constDimsToStrides(dims)

    val banking = access match {
      case Def(LineBufferColSlice(_,row,col,Exact(len))) => Seq(NoBanking, StridedBanking(strides(1), len.toInt))
      case Def(LineBufferRowSlice(_,row,Exact(len),col)) => Seq(StridedBanking(strides(0),len.toInt), NoBanking)
      case Def(LineBufferEnq(_,_,_))                     => Seq(NoBanking, StridedBanking(strides(1), channels))
      case Def(LineBufferLoad(_,row,col,_)) =>
        val patterns = accessPatternOf(access)
        indexPatternsToBanking(patterns, strides)

      case _ =>
        val patterns = accessPatternOf(access)
        NoBanking +: indexPatternsToBanking(patterns, strides) // Everything else uses 1D view of line buffer
    }

    val banks = banking.map(_.banks).product
    val duplicates = channels / banks

    (BankedMemory(banking, depth=1, isAccum=false), duplicates)
  }

  def bankRegFileAccess(mem: Exp[_], access: Exp[_]): Channels = {
    val dims: Seq[Int] = stagedDimsOf(mem).map{case Exact(c) => c.toInt}
    val strides = constDimsToStrides(dims)

    val factors  = unrollFactorsOf(access) diff unrollFactorsOf(mem)
    val channels = factors.flatten.map{case Exact(c) => c.toInt}.product

    def bankFactor(i: Exp[Index]) = parFactorOf(i) match { case Exact(c) => c.toInt }

    val patterns = accessPatternOf(access)
    val banking = indexPatternsToBanking(patterns, strides)

    val banks = banking.map(_.banks).product
    val duplicates = channels / banks

    (BankedMemory(banking, 1, isAccum = false), duplicates)
  }

  def bankStream(mem: Exp[_]): Unit = {
    val reads = readersOf(mem)
    val writes = writersOf(mem)
    val accesses = reads ++ writes

    accesses.foreach{access =>
      dispatchOf.add(access, mem, 0)
      portsOf(access, mem, 0) = Set(0)
    }

    val par = (1 +: accesses.map{access =>
      val factors = unrollFactorsOf(access.node) // relative to stream, which always has par of 1
      factors.flatten.map{case Exact(c) => c.toInt}.product
    }).max

    /*val bus = mem match {
      case Op(StreamInNew(bus)) => bus
      case Op(StreamOutNew(bus)) => bus
    }*/

    duplicatesOf(mem) = List(BankedMemory(Seq(StridedBanking(1,par)),1,isAccum=false))
  }

  def bankBufferOut(buffer: Exp[_]): Unit = {
    val reads = readersOf(buffer)
    val writes = writersOf(buffer)
    val accesses = reads ++ writes

    assert(reads.isEmpty)

    // Hack: Find outermost streaming control

    accesses.foreach{access =>
      dispatchOf.add(access, buffer, 0)
      portsOf(access, buffer, 0) = Set(0)
    }

    val par = (1 +: accesses.map{access =>
      val factors = unrollFactorsOf(access.node) // relative to stream, which always has par of 1
      factors.flatten.map{case Exact(c) => c.toInt}.product
    }).max

  }

}
