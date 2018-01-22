package spatial.banking

import argon.analysis._
import argon.core._
import spatial.aliases._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._
import org.virtualized.SourceContext

import scala.collection.mutable.{ArrayBuffer,HashMap,HashSet}

/**
  * Helper class for configuring banking and buffering of an addressable memory
  */
class MemoryConfigurer(val mem: Exp[_], val strategy: BankingStrategy)(implicit val IR: State) extends MemoryChecks {
  protected val dims: Seq[Int] = constDimsOf(mem)
  protected val AllowMultiDimStreaming = false
  protected val inVector: HashSet[Exp[Index]] = new HashSet[Exp[Index]] // TODO: Unused?
  protected val unrolledRand= new HashMap[Exp[Index],Map[Seq[Int],Exp[Index]]]

  protected val isGlobalMem: Boolean = isArgIn(mem) || isArgOut(mem)

  protected def dimensionGroupings: Seq[Seq[Seq[Int]]] = {
    val dimIndices = dims.indices
    val hierarchical = dimIndices.map{d => Seq(d) }   // Fully hierarchical (each dimension has separate bank addr.)
    val fullDiagonal = Seq(dimIndices)                // Fully diagonal (all dimensions contribute to single bank addr.)
    // TODO: try other/all possible dimension orderings?
    if (dims.length > 1) Seq(hierarchical, fullDiagonal) else Seq(hierarchical)
  }

  /**
    * Unrolls the given random access symbol to multiple symbols based on the parallelization factors
    * of all counters between the memory definition and the calculation of this address.
    */
  def unrollRandomAddress(access: Access, x: Option[Exp[Index]], pattern: IndexPattern): Map[Seq[Int],Exp[Index]] = {
    val lastVariantIndex = pattern.lastVariantIndex
    val is = accessIterators(access.node, mem)
    // Take only the indices which this access varies with (essentially code motion)
    val idxOfLastInvariant = lastVariantIndex.map{i => is.indexOf(i) }.getOrElse(-1)
    val variant = is.slice(0, idxOfLastInvariant+1)
    val invariant = is.slice(idxOfLastInvariant+1, is.length)

    if (x.isDefined && unrolledRand.contains(x.get)) {
      unrolledRand(x.get)
    }
    else {
      // Iterate over all iterators being unrolled
      val vps = variant.map{i => parFactorOf(i).toInt }
      val ips = invariant.map{i => parFactorOf(i).toInt }
      // TODO: Annoying to update symbol table when calling fresh here..
      val xs = multiLoop(vps).flatMap{vid =>
        // Only create a new index for variant iterators
        val x = fresh[Index]
        multiLoop(ips).map{iid => (vid++iid) -> x }
      }.toMap

      if (config.verbosity > 0) {
        dbg(s"  Random access $access" + (if (x.isDefined) s", addr: ${str(x.get)}" else "") )
        val ps = is.map{i => parFactorOf(i).toInt }
        dbg(s"  Iterators between access and memory: " + is.zip(ps).map{case (i,p) => c"$i ($p)"}.mkString(", "))
        dbg(s"  Variant:   " + variant.zip(vps).map{case (i,p) => c"$i ($p)"}.mkString(", "))
        dbg(s"  Invariant: " + invariant.zip(ips).map{case (i,p) => c"$i ($p)"}.mkString(", "))
        dbg(s"  Unrolled to: ")
        xs.foreach{case (id,x) => dbg("    [" + id.mkString(",") + s"] -> $x") }
      }

      x.foreach{x => unrolledRand += x -> xs }
      xs
    }
  }

  def randomAddrs(access: Access, is: Seq[Exp[Index]], pattern: IndexPattern): Map[(Exp[Index],Seq[Int]),Exp[Index]] = {
    val xs = is.filter{case _:Bound[_] => false; case _ => true}
    xs.flatMap{x => unrollRandomAddress(access,Some(x),pattern).toSeq.map{case (id,x2) => (x2,id) -> x } }.toMap
  }

  /**
    * Convert this compact access matrix to multiple unrolled access matrices
    * by simulating loop parallelization/unrolling
    */
  def unroll(matrix: CompactMatrix, indices: Seq[Exp[Index]]): Seq[AccessMatrix] = {
    val blks = blksBetween(blkOf.get(matrix.access.node), blkOf.get(mem))
    val is = accessIterators(matrix.access.node, mem)
    val ps = is.map{i => parFactorOf(i).toInt }
    dbg("")
    dbg(s"  Simulating unrolling of access ${matrix.access}")
    dbg(s"  Iterators between access and memory: " + is.zip(ps).map{case (i,p) => c"$i ($p)"}.mkString(", "))
    dbg(s"  Block of access: ${blkOf.get(matrix.access.node).map(_.toString).getOrElse("<none>")}")
    dbg(s"  Block of memory: ${blkOf.get(mem).map(_.toString).getOrElse("<none>")}")
    dbg(s"  Blocks between access and memory: ")
    blks.foreach{blk =>
      dbg(s"    ${str(blk.node)} [${blk.block}]")
    }

    def expand(vector: AccessVector, id: Seq[Int]): AccessVector = vector match {
      // EXPERIMENTAL: Treat a random vector offset address as an affine index
      case RandomVector(_,uroll,Some(vecId)) =>
        val xp = uroll.apply(id)
        val as = indices.map{i => if (i == xp) 1 else 0 }.toArray
        AffineVector(as,indices,vecId,Map.empty)

      case RandomVector(_,uroll,None) =>
        val xp = uroll.apply(id) //.take(len))
        RandomVector(Some(xp), uroll, None)

      // Note that there's three sets of iterators here:
      //  is      - iterators defined between the memory and this access
      //  inds    - iterators used by this affine access
      //  indices - iterators used by ALL accesses to this memory
      case AffineVector(as,inds,b,uroll) =>
        val unrolled = indices.map{i =>

          if (uroll.contains((i,id))) {
            val x_orig = uroll((i,id))
            val idxAccess = inds.indexOf(x_orig)
            val a = if (idxAccess >= 0) as(idxAccess) else 0
            (a, 0)
          }
          else {
            val idxAccess = inds.indexOf(i)
            val idxHierarchy = is.indexOf(i)
            val a_orig = if (idxAccess >= 0) as(idxAccess) else 0
            val p = if (idxHierarchy >= 0) ps(idxHierarchy) else 0
            val n = if (idxHierarchy >= 0) id(idxHierarchy) else 0

            val a = a_orig * p
            val b_i = a_orig * n
            (a, b_i)
          }
        }
        val as2 = unrolled.map(_._1).toArray
        val b2 = unrolled.map(_._2).sum + b
        val uvec = AffineVector(as2,indices,b2,Map.empty)
        uvec
    }

    // Fake unrolling
    // e.g. change 2i + 3 with par(i) = 2 into
    // 4i + 0*2 + 3 = 4i + 3
    // 4i + 1*2 + 3 = 4i + 5
    multiLoop(ps).map{id =>
      dbg(s"  Unroll: " + is.zip(id).map{case (i,xid) => c"$i [$xid]" }.mkString(", "))
      val uvectors = matrix.vectors.map{vector => expand(vector, id) }
      val unrollId = id ++ matrix.vecId
      AccessMatrix(uvectors, matrix.access, indices, unrollId)
    }.toSeq
  }


  def configure(): Unit = {
    dbg("")
    dbg("")
    dbg("-----------------------------------")
    dbg(u"Inferring instances for on-chip memory $mem (${mem.ctx})")
    dbg(c"${str(mem)}")

    val writers = writersOf(mem)
    val readers = readersOf(mem)
    checkAccesses(readers, writers)

    val instances = bank(readers, writers)

    finalize(instances)
  }

  /**
    * Calculate the physical instances required to efficiently support the given accesses.
    */
  def bank(readers: Seq[Access], writers: Seq[Access]): Seq[MemoryInstance] = {
    val (readMatrices, writeMatrices, domain) = createAccessMatrices(readers, writers)
    val readGroups = createGroups(readMatrices, domain)
    val writeGroups = createGroups(writeMatrices, domain)

    if (config.verbosity > 0) {
      dbg("")
      dbg(s"  Grouping accesses resulted in the following: ")
      if (readGroups.isEmpty) dbg("  <No Read Groups>") else dbg(s"  ${readGroups.length} Read Group(s):")
      readGroups.zipWithIndex.foreach { case (grp, i) =>
        dbg(s"    Read Group $i: ")
        grp.foreach {read => dbg(s"""      ${str(read.access.node)} [${read.id.mkString(", ")}]""") }
      }
      if (writeGroups.isEmpty) dbg("  <No Write Groups>") else dbg(s"  ${writeGroups.length} Write Group(s):")
      writeGroups.zipWithIndex.foreach { case (grp, i) =>
        dbg(s"    Write Group $i: ")
        grp.foreach {write => dbg(s"""      ${str(write.access.node)} [${write.id.mkString(", ")}]""") }
      }
    }

    val instances = if (readers.nonEmpty) {
      dbg(s"  Merging read groups...")
      mergeReadGroups(readGroups.map(_.toSet), writeGroups, domain)
    }
    else if (writers.nonEmpty) {
      dbg(s"  Merging write groups...")
      mergeWriteGroups(writeGroups, domain)
    }
    else {
      dbg(s"  No accesses?")
      // There's no writers or readers... so whatever?
      val banking = ModBanking(1,1,dims.map{_ => 1}, List.tabulate(dims.length){i => i})
      Seq(InstanceGroup(Nil,Nil,Set.empty,None,Seq(banking),1,1,Map.empty))
    }
    instances.map(instanceGroupToMemoryInstance)
  }

  /**
    * Complete memory analysis by adding banking and buffering metadata to the memory and
    * all associated accesses.
    */
  def finalize(instances: Seq[MemoryInstance]): Unit = {
    val duplicates = instances.zipWithIndex.map{
      case (MemoryInstance(reads,writes,mp,banking,depth,ports,isAccum), x) =>
        val uaccesses = (reads ++ writes).flatten
        val accesses  = uaccesses.map(_.access)
        var ctrlCount = Map.empty[Ctrl,Int]

        writes.foreach{grp =>
          grp.groupBy(_.ctrl).foreach{case (ctrl,uaccs) =>
              val index = ctrlCount.getOrElse(ctrl, 0)
              uaccs.foreach{access => muxIndexOf(access, mem) = index }
              ctrlCount += ctrl -> (index+1)
          }
        }

        uaccesses.foreach{a => dispatchOf.add(a.uaccess, mem, x) }
        accesses.foreach{access => portsOf(access, mem, x) = ports(access) }

        Memory(banking,depth,isAccum)
    }

    val allAccesses: Set[Access] = (readersOf(mem) ++ writersOf(mem)).toSet
    val usedAccesses: Set[Access] = instances.flatMap{inst => inst.reads.flatMap(_.map(_.access)) ++ inst.writes.flatMap(_.map(_.access)) }.toSet
    val unusedAccesses: Set[Access] = allAccesses diff usedAccesses
    unusedAccesses.foreach{access =>
      warn(access.node.ctx, u"Access to memory ${access.node} appears to be unused")
      warn(access.node.ctx)
      isUnusedAccess(access.node) = true
    }

    printInstances(instances)
    duplicatesOf(mem) = duplicates
  }

  protected def printInstances(instances: Seq[MemoryInstance]): Unit = if (config.verbosity > 0) {
    dbg("")
    dbg("")
    dbg(u"  SUMMARY for memory $mem:")
    dbg(u"${str(mem)}")
    dbg(mem.ctx.lineContent.getOrElse(""))
    dbg("")
    instances.zipWithIndex.foreach{case (inst,i) =>
      dbg(s"  Instance #$i: ")
      printInstance(inst)
    }
  }

  protected def printInstance(instance: MemoryInstance): Unit = {
    val MemoryInstance(reads,writes,mp,banking,depth,ports,isAccum) = instance
    dbg(s"    isAccum:    $isAccum")
    dbg(s"    Depth:      $depth")
    dbg(s"    Banking:    $banking")
    dbg(s"    Controller: ${mp.map{c=>u"$c"}.getOrElse("---")}")
    dbg(s"    Buffer Ports: ")
    (0 until depth).foreach{port =>
      writes.foreach { grp =>
        grp.filter{w => ports(w.access).contains(port) }
           .foreach{w =>
             val muxIdx = muxIndexOf(w,mem)
             dbg(c"""      $port: (mux:$muxIdx) [WR] ${w._1} [${w._2}] {${w._3.mkString(",")}}""")
           }
      }
      reads.foreach { grp =>
        grp.filter{r => ports(r.access).contains(port) }
           .foreach{r =>
             val muxIdx = muxIndexOf(r,mem)
             dbg(c"""      $port: (mux:$muxIdx) [RD] ${r._1} [${r._2}] {${r._3.mkString(",")}}""")
           }
      }
    }
  }

  /**
    * Register all of the indices for all associated accesses.
    * Attempt to find the minimum and maximum value for each counter index, allowing
    * for the min/max to be affine functions of other counter values.
    * If the function is not analyzable, assume Int.min or Int.max for min and max, respectively.
    *
    * TODO: The bounds logic should eventually be moved elsewhere (to ScalarAnalyzer)?
    */
  def getIterDomain(indices: Seq[Exp[Index]]): IndexDomain = IndexDomain(indices.zipWithIndex.flatMap{case (i,iIdx) =>
    def sparseBound(i: Option[IndexPattern]): Option[AffineVector] = i match {
      case Some(Affine(as,is,b)) => Some(AffineVector(as,is,b,Map.empty).remap(indices))
      case _ => None
    }
    ctrOf(i) match {
      case Some(ctr) =>
        val min = sparseBound(accessPatternOf(counterStart(ctr)).headOption).map{m =>
          // a0*i0 + ... + aN*iN + b <= iX  |->  -a0*i0 + ... + iX + ... + -aN*iN - b >= 0
          val minA = m.as.zipWithIndex.map{case (x,d) => if (d == iIdx) 1 else -x}
          val minC = -m.b
          Seq(minA :+ minC)
        }.getOrElse(Nil)
        val max = sparseBound(accessPatternOf(counterEnd(ctr)).headOption).map{m =>
          // a0*i0 + ... + aN*iN + b >= iX  |->  a0*i0 + ... + -iX + ... + aN*iN + b >= 0
          val maxA = m.as; maxA(iIdx) = -1
          val maxC = m.b
          Seq(maxA :+ maxC)
        }.getOrElse(Nil)
        min ++ max

      case _ => Nil
    }
  }.toArray)


  /**
    * Returns an approximation of the cost for the given banking strategy.
    */
  def cost(banking: Seq[ModBanking], depth: Int): Int = {
    val totalBanks = banking.map(_.N).product
    depth * totalBanks
  }

  /**
    * A write MAY be seen by a reader if it may precede the reader and the addresses may intersect
    *
    * TODO: Should factor in branches of switches being mutually exclusive here
    */
  def precedingWrites(reader: AccessMatrix, writers: Set[AccessMatrix], domain: IndexDomain): (Seq[AccessMatrix], Seq[AccessMatrix]) = {
    dbg(s"    Preceding writers for ")
    reader.printWithTab("  ")

    val preceding = writers.filter{writer =>
      val intersects = reader.intersectsSpace(writer,domain)
      val mayPrecede = writer.access.mayPrecede(reader.access)
      val notAfter   = !writer.access.mayFollow(reader.access)

      val (ctrl,dist) = lcaWithDistance(reader.access.ctrl, writer.access.ctrl)
      val inLoop = isInLoop(ctrl.node)

      writer.printWithTab("      ")
      dbg(s"      [LCA=$ctrl, dist=$dist, inLoop=$inLoop]")
      dbg(s"      [notAfter = $notAfter, intersects=$intersects, mayPrecede=$mayPrecede]")

      intersects && mayPrecede
    }
    if (preceding.isEmpty) dbg(s"    <None>")

    val (before, after) = preceding.partition{writer => !writer.access.mayFollow(reader.access) }

    (before.toSeq, after.toSeq)
  }

  /**
    * Tests if a given write is entirely overwritten by a subsequent write prior to being read by the reader
    *
    * This occurs when another write w MUST follow that write and w contains ALL of the addresses in the original write
    */
  def isKilled(write: AccessMatrix, others: Seq[AccessMatrix], reader: AccessMatrix, domain: IndexDomain): Boolean = {
    others.exists{w => w.access.mustFollow(write.access, reader.access) && w.containsSpace(write,domain) }
  }

  /**
    * Returns the subset of writers which may be visible to this set of readers
    */
  def reachingWrites(readGroups: Seq[Set[AccessMatrix]], writeGroups: Seq[Set[AccessMatrix]], domain: IndexDomain): Seq[Set[AccessMatrix]] = {
    /*dbg("")
    dbg("  Reaching writes for reads ")
    readGroups.flatten.foreach{rd =>
      dbg(s"    ${str(rd.access.node)} [${rd.id}] [${rd.access.ctrl}]")
    }*/

    // Hack: return all writers for argOuts for now
    if (isGlobalMem) return writeGroups

    var remainingWrites: Set[AccessMatrix] = Set.empty
    var reachingWrites:  Set[AccessMatrix] = Set.empty
    writeGroups.foreach{grp => remainingWrites ++= grp }

    readGroups.foreach{grp => grp.foreach{reader =>
      val (before, after) = precedingWrites(reader, remainingWrites, domain)

      val reachingBefore = before.zipWithIndex.filterNot{case (wr,i) => isKilled(wr, before.drop(i+1), reader, domain) }.map(_._1)
      val reachingAfter  = after.zipWithIndex.filterNot{case (wr,i) => isKilled(wr, after.drop(i+1) ++ before, reader, domain) }.map(_._1)
      val reaching = reachingBefore ++ reachingAfter
      remainingWrites --= reaching
      reachingWrites ++= reaching
    }}
    val reaching = writeGroups.map{grp =>
      grp intersect reachingWrites
    }.filterNot(_.isEmpty)

    /*reaching.zipWithIndex.foreach{case (grp, i) =>
      dbg(s"  Group #$i: ")
      grp.foreach{wr => dbg(s"    ${str(wr.access.node)} [${wr.id}] [${wr.access.node}]") }
    }*/
    reaching
  }

  /**
    * Converts a 1-dimensional partial access with optional address `addr` to an AccessVector
    * Simulates unrolling for random accesses to model each distinct random access as an "iterator"
    */
  def indexPatternToAccessVector(access: Access, addr: Option[Exp[Index]], pattern: IndexPattern, vecId: Option[Int]): AccessVector = pattern match {
    case Affine(as, is, b)  => AffineVector(as, is, b + vecId.getOrElse(0), randomAddrs(access,is,pattern))
    case _ =>
      //if (isVecOfs) addr.foreach{a => inVector += a } // TODO: Not yet used
      val uroll = unrollRandomAddress(access, addr, pattern)
      RandomVector(addr,uroll,vecId)
  }

  def accessPatternToCompactMatrix(access: Access, addr: Option[Seq[Exp[Index]]]): Seq[CompactMatrix] = {
    val vectors = accessPatternOf(access.node).zipWithIndex.map { case (pattern, i) =>
      indexPatternToAccessVector(access, addr.map(_.apply(i)), pattern, None)
    }
    dbg(s"  ${str(access.node)}: Found ${vectors.length} vectors: ")
    vectors.foreach{v => dbg("    " + v.toString) }
    Seq(CompactMatrix(vectors.toArray, access, None))
  }

  /**
    * Return the access pattern of this access as one or more CompactMatrices
    */
  def getAccessVector(access: Access): Seq[CompactMatrix] = access.node match {
    // TODO: Don't treat vector enqueues like vector operations for now
    case Def(d:VectorEnqueueLikeOp[_]) => accessPatternToCompactMatrix(access, d.address)

    case Def(d: VectorAccess[_]) if d.accessWidth > 1 =>
      Seq.tabulate(d.accessWidth){ vecId =>
        val addr = d.address
        val vectors = accessPatternOf(access.node).zipWithIndex.map { case (pattern, i) =>
          val vId = if (i == d.axis) Some(vecId) else None
          indexPatternToAccessVector(access, addr.map(_.apply(i)), pattern, vId)
        }
        CompactMatrix(vectors.toArray, access, Some(vecId))
      }

    case Def(d: EnabledAccess[_]) => accessPatternToCompactMatrix(access, d.address)
    case _                        => accessPatternToCompactMatrix(access, None)
  }

  /**
    * Fake the access pattern of this streaming access as being an affine function of the iterators
    * This is done such that all unrolled vectors will be distinct, to avoid tripping up the analyzer
    *
    * Foreach(N par 4){i =>
    *   Foreach(M par 6){j =>
    *     x(6i + j) --> 24i + 6j + [0,24)
    */
  protected def createStreamingVector(access: Access): CompactMatrix = {
    val is = accessIterators(access.node, mem)
    val ps = is.map{i => parFactorOf(i).toInt }
    val as = Array.tabulate(is.length){i => ps.drop(i + 1).product }
    CompactMatrix(Array(AffineVector(as, is, 0, Map.empty)), access, None)
  }

  /**
    * Converts streaming accesses to 1D accesses
    *
    * NOTE: This only works if accesses to this memory are (viewed as) 1D
    */
  protected def getStreamingAccessVectors(readers: Seq[Access], writers: Seq[Access]): (Seq[CompactMatrix], Seq[CompactMatrix]) = {
    if (readers.nonEmpty || writers.nonEmpty) {
      if (dims.length != 1 && !AllowMultiDimStreaming) {
        bug(mem.ctx, "Cannot create streaming accesses on memory with more than one dimension")
        bug(s"${str(mem)}")
        readers.foreach { rd => bug(s"${str(rd.node)}") }
        writers.foreach { wr => bug(s"${str(wr.node)}") }
      }
      val readVectors = readers.map(createStreamingVector)
      val writeVectors = writers.map(createStreamingVector)
      (readVectors, writeVectors)
    }
    else (Nil,Nil)
  }

  /**
    * Convert read and write accesses to AccessMatrices by simulating unrolling of parallel loops
    */
  def createAccessMatrices(readers: Seq[Access], writers: Seq[Access]): (Seq[AccessMatrix],Seq[AccessMatrix],IndexDomain) = {
    val (addrReaders,streamReaders) = readers.partition(rd => !isAccessWithoutAddress(rd.node))
    val (addrWriters,streamWriters) = writers.partition(wr => !isAccessWithoutAddress(wr.node))
    val (streamReadVectors, streamWriteVectors) = getStreamingAccessVectors(streamReaders,streamWriters)

    val readDenseMatrices = addrReaders.flatMap(getAccessVector) ++ streamReadVectors
    val writeDenseMatrices = addrWriters.flatMap(getAccessVector) ++ streamWriteVectors
    val indices = (readDenseMatrices ++ writeDenseMatrices).flatMap(_.unrollIndices).distinct.sortBy{case s:Dyn[_] => s.id; case _ => 0}

    dbg(s"  Found the following compact access matrices: ")
    dbg(s"  Reads: ")
    readDenseMatrices.foreach{m =>
      m.printWithTab("    ")
      accessPatternOf.get(m.access.node).foreach{a => a.zipWithIndex.foreach{case (p,i) => dbg(s"     $i: $p") }}
    }
    dbg("")
    dbg(s"  Writes: ")
    writeDenseMatrices.foreach{m =>
      m.printWithTab("    ")
      accessPatternOf.get(m.access.node).foreach{a => a.zipWithIndex.foreach{case (p,i) => dbg(s"     $i: $p") }}
    }
    dbg("")

    val domain = getIterDomain(indices)
    val readMatrices = readDenseMatrices.flatMap{mat => unroll(mat, indices) }
    val writeMatrices = writeDenseMatrices.flatMap{mat => unroll(mat, indices) }

    dbg("Indices: " + indices.mkString(", "))
    dbg("Domain: ")
    dbg(domain.str)
    dbg("")

    dbg(s"  Pseudo-unrolling resulted in the following access matrices: ")
    dbg(s"  Reads: ")
    readMatrices.foreach{m => m.printWithTab("    ") }
    dbg("")
    dbg(s"  Writes:")
    writeMatrices.foreach{m => m.printWithTab("     ") }

    (readMatrices, writeMatrices, domain)
  }

  protected def createGroups(accessors: Seq[AccessMatrix], domain: IndexDomain): Seq[Set[AccessMatrix]] = {
    accessors.groupBy(_.access.ctrl).toSeq.flatMap{case (_,accesses) =>
      val groups = ArrayBuffer[ArrayBuffer[AccessMatrix]]()
      accesses.foreach{access =>
        val grpId = groups.indexWhere{grp => grp.forall{r => !access.intersects(r, domain) } }
        if (grpId != -1) groups(grpId) += access  else groups += ArrayBuffer(access)
      }
      groups
    }.map(_.toSet)
  }

  protected def instanceGroupToMemoryInstance(instance: InstanceGroup): MemoryInstance = {
    val InstanceGroup(rds, wrs, _, mp, banking, depth, _, ports) = instance

    val reads   = rds.flatten.map{_.access}
    val writes  = wrs.flatten.map{_.access}
    val accesses = reads ++ writes
    val ureads  = rds.map{grp => grp.map(_.unrolledAccess)}
    val uwrites = wrs.map{grp => grp.map(_.unrolledAccess)}

    // A memory is an accumulator if a writer depends on a reader in the same pipe
    // or if this memory is used as an accumulator by a Reduce or MemReduce
    // and at least one of the writers is in the same control node as the reader
    val isImperativeAccum = reads.exists{read => writes.exists{w => readDepsOf(w.node).contains(read.node) }}
    val isReduceAccum = mem match {
      case s: Dyn[_] => s.dependents.exists{
        case Def(e: OpReduce[_])      => e.accum == s && reads.exists{read => writes.exists(_.ctrl == read.ctrl)}
        case Def(e: OpMemReduce[_,_]) => e.accum == s && reads.exists{read => writes.exists(_.ctrl == read.ctrl)}
        case _ => false
      }
      case _ => false
    }
    val isAccum = isImperativeAccum || isReduceAccum

    val muxedPorts = mp match {
      // Metapipelined case: partition accesses based on whether they're n-buffered or time multiplexed w/ buffer
      case Some(parent) =>
        val (nbuf, tmux) = accesses.partition{access => lca(access.ctrl, parent).get == parent }
        val allPorts = List.tabulate(depth){i=>i}.toSet
        (nbuf.map{a => a -> Set(ports(a)) } ++ tmux.map{a => a -> allPorts}).toMap

      // Time-multiplexed case:
      case None =>
        ports.map{case (key, port) => key -> Set(port)}
    }

    MemoryInstance(
      reads = ureads,
      writes = uwrites,
      metapipe = mp,
      banking = banking,
      depth = depth,
      ports = muxedPorts,
      isAccum = isAccum
    )
  }


  protected def bankGroups(readGroups: Seq[Set[AccessMatrix]], writeGroups: Seq[Set[AccessMatrix]], domain: IndexDomain): Option[InstanceGroup] = {
    val reads  = readGroups.flatten.map(_.access)
    val writes = writeGroups.flatten.map(_.access)
    val ctrls  = reads.map(_.ctrl).toSet
    val reachWrites = if (readGroups.nonEmpty) reachingWrites(readGroups, writeGroups, domain) else writeGroups
    val bankings = strategy.bankAccesses(mem, dims, readGroups, reachWrites, domain, dimensionGroupings)
    if (bankings.nonEmpty) {
      // TODO: Multiple metapipe parents should cause backtrack eventually
      dbg(s"  Group LCAs:")
      val (metapipe, ports) = findMetaPipe(mem, reads, writes) //, verbose = true)
      val depth = ports.values.max + 1
      val bankingCosts = bankings.map { b => (b, cost(b, depth)) }
      val (banking, bankCost) = bankingCosts.minBy(_._2)
      Some(InstanceGroup(readGroups, reachWrites, ctrls, metapipe, banking, depth, bankCost, ports))
    }
    else None
  }

  // 1. Cannot combine across the same common LCA controller
  // 2. Cannot combine across parallel controllers
  // 3. Cannot combine such that we create hierarchical buffers (for now)
  // 4. Cannot combine local accumulators with other buffers UNLESS the accumulation is a Reduce
  protected def mergeCompatible(a: InstanceGroup, b: InstanceGroup): Boolean = {
    (a.ctrls intersect b.ctrls).isEmpty &&
    a.ctrls.forall{cA => !b.ctrls.exists{cB => lca(cA,cB).exists(isParallel) } } &&
    findAllCtrlMetaPipes(mem, a.ctrls ++ b.ctrls).size <= 1 &&
    !a.isAcc && !b.isAcc
  }

  /** Special case primarily for registers - allow concurrent accesses with constant addresses **/
  /*protected def areConstantAccesses(groups: Seq[Set[AccessMatrix]]): Boolean = {
    groups.forall{grp => grp.forall(_.vectors.forall{
      case v: AffineVector => v.as.forall(_ == 0)
      case _ => false
    })}
  }*/

  /**
    * Greedily banks and merges groups of readers into banked memory instances.
    * NOTE: Total worst case runtime is O(n**2) for n groups of readers.
    * TODO: Add exhaustive implementation after pruning?
    */
  protected def mergeReadGroups(readGroups: Seq[Set[AccessMatrix]], writeGroups: Seq[Set[AccessMatrix]], domain: IndexDomain): Seq[InstanceGroup] = {
    val instances = ArrayBuffer[InstanceGroup]()

    readGroups.zipWithIndex.foreach{case (group, groupId) =>
      val orig = bankGroups(Seq(group),writeGroups,domain)
      // TODO: What to do on failure?
      if (orig.isEmpty) throw new Exception(s"Could not bank all $mem reads with writes")
      val i1 = orig.get

      var instIdx = 0
      var mergedIntoInstance = false
      while (instIdx < instances.length && !mergedIntoInstance) {
        val i2 = instances(instIdx)
        // We already separated out the reads which can't be banked in the same controller, so skip groups which
        // contain any of the same read controllers
        // Exception: reads on global memories (i.e. ArgIn) can be banked together since value is constant
        dbg(s"  Attempting to merge group #$groupId into instance #$instIdx")
        if (mergeCompatible(i1,i2) || isGlobalMem) {
          val i_p = bankGroups(group +: i2.reads, writeGroups, domain) // i'

          // Merging buffers is only allowed if explicitly enabled (note: this is for PIR)
          if (i_p.isDefined && (i2.metapipe.isEmpty || i1.metapipe.isEmpty || spatialConfig.enableBufferCoalescing)) {
            if (i_p.get.cost <= i1.cost + i2.cost) {
              instances(instIdx) = i_p.get
              mergedIntoInstance = true
              dbg(s"  Merged group #$groupId into instance #$instIdx")
            }
            else dbg(s"  Did not merge #$groupId into instance #$instIdx: Too expensive")
          }
          else if (i_p.isEmpty) dbg(s"  Did not merge #$groupId into instance #$instIdx: Conflicting banks")
          else dbg(s"  Did not merge #$groupId into instance #$instIdx: Buffer conflict")
        }
        else dbg(s"  Did not merge #$groupId into instance #$instIdx: Control conflict")
        instIdx += 1
      }
      if (!mergedIntoInstance) instances += i1
    }
    instances
  }

  /**
    * Greedily bank and merge writers into banked memory instances.
    * NOTE: This version is only used if there are no readers.
    * TODO: Assumes all writes are reaching for now (to some unknown reader outside of Accel)
    */
  protected def mergeWriteGroups(writeGroups: Seq[Set[AccessMatrix]], domain: IndexDomain): Seq[InstanceGroup] = {
    val instance = bankGroups(Nil, writeGroups, domain)
    if (instance.isEmpty) throw new Exception("Could not bank all writers together!")
    Seq(instance.get)
  }


}
