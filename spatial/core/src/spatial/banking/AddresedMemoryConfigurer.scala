package spatial.banking

import argon.analysis._
import argon.core._
import argon.util._
import spatial.aliases._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._

import scala.collection.mutable.ArrayBuffer

case class DenseBoundVector(as: Array[Int], is: Seq[Exp[Index]], b: Int) {
  def sparsify(indices: Seq[Exp[Index]]): SparseBoundVector = {
    val as2 = indices.map{ind =>
      val pos = is.indexOf(ind)
      if (pos >= 0) as(pos) else 0
    }
    SparseBoundVector(as2.toArray, b)
  }
}
case class SparseBoundVector(as: Array[Int], b: Int)

case class InstanceGroup(
  var reads:    Set[AccessMatrix],      // All reads within this group
  var writes:   Set[AccessMatrix],      // All writes within this group
  var ctrls:    Set[Ctrl],              // Set of controllers these accesses are in
  var metapipe: Option[Ctrl],           // Controller if at least some accesses require n-buffering
  var banking:  Seq[ModBanking],        // Banking information
  var depth:    Int,                    // Depth of n-buffer
  var cost:     Int,                    // Cost estimate of this configuration
  var ports:    Map[Access,Int]         // Ports
)

/**
  * Helper class for configuring banking and buffering of an addressable memory
  */
class AddressedMemoryConfigurer(override val mem: Exp[_])(implicit state: State) extends MemoryConfigurer(mem) with ExhaustiveBanking {

  /**
    * A write MAY be seen by a reader if it may precede the reader and the addresses may intersect
    *
    * TODO: Should factor in branches of switches being mutually exclusive here
    */
  def precedingWrites(reader: AccessMatrix, writers: Set[AccessMatrix]): (Seq[AccessMatrix], Seq[AccessMatrix]) = {
    val (before, after) =
      writers.filter{writer => reader.intersectsSpace(writer) && writer.access.mayPrecede(reader.access) }
             .partition{writer => !writer.access.mayFollow(reader.access) }

    (before.toSeq, after.toSeq)
  }

  /**
    * Tests if a given write is entirely overwritten by a subsequent write prior to being read by the reader
    *
    * This occurs when another write w MUST follow that write and w contains ALL of the addresses in the original write
    */
  def isKilled(write: AccessMatrix, others: Seq[AccessMatrix], reader: AccessMatrix): Boolean = {
    others.exists{w => w.access.mustFollow(write.access, reader.access) && w.containsSpace(write) }
  }

  /**
    * Returns the subset of writers which may be visible to this set of readers
    */
  def reachingWrites(readers: Set[AccessMatrix], writers: Seq[AccessMatrix]): Set[AccessMatrix] = {
    var remainingWrites: Set[AccessMatrix] = writers.toSet
    var reachingWrites: Set[AccessMatrix] = Set.empty
    readers.foreach{reader =>
      val (before, after) = precedingWrites(reader, remainingWrites)

      val reachingBefore = before.zipWithIndex.filterNot{case (wr,i) => isKilled(wr, before.drop(i+1), reader) }.map(_._1)
      val reachingAfter  = after.zipWithIndex.filterNot{case (wr,i) => isKilled(wr, after.drop(i+1) ++ before, reader) }.map(_._1)
      val reaching = reachingBefore ++ reachingAfter
      remainingWrites --= reaching
      reachingWrites ++= reaching
    }
    reachingWrites
  }

  /**
    * Return the access pattern of this access as a (Dense)AccessVector
    */
  def getAccessVector(access: Access): Seq[Seq[CompactVector]] = Seq(accessPatternOf(access.node).map {
    case BankableAffine(as, is, b) => CompactAffineVector(as, is, b, access)
    case _ => CompactRandomVector(access)
  })

  /**
    * Convert the given ND compact vectors to a sequence of ND unrolled vectors (sequence of AccessMatrix)
    */
  def unrollAccess(access: Seq[CompactVector], indices: Seq[Exp[Index]]): Seq[AccessMatrix] = {
    val a = access.head.access
    val is = iteratorsBetween(a, (parentOf(mem).get,-1))
    val ps = is.map{i => parFactorOf(i).toInt }

    val ndims = is.length
    val prods = List.tabulate(ndims){i => ps.slice(i+1,ndims).product }
    val total = ps.product

    def expand(vector: CompactVector, id: Seq[Int]): UnrolledVector = vector match {
      case _: CompactRandomVector => UnrolledRandomVector(indices,a,id)

      // Note that there's three sets of iterators here:
      //  is      - iterators defined between the memory and this access
      //  inds    - iterators used by this affine access
      //  indices - iterators used by ALL accesses to this memory
      case CompactAffineVector(as,inds,b,_) =>
        val unrolled = indices.map{i =>
          val idxAccess = inds.indexOf(i)
          val idxHierarchy = is.indexOf(i)
          val a_orig = if (idxAccess >= 0) as(idxAccess) else 0
          val p = if (idxHierarchy >= 0) ps(idxHierarchy) else 0
          val n = if (idxHierarchy >= 0) id(idxHierarchy) else 0
          val a = a_orig*p
          val b_i = a_orig*n
          (a, b_i)
        }
        val as2 = unrolled.map(_._1).toArray
        val b2 = unrolled.map(_._2).sum + b
        UnrolledAffineVector(as2,indices,b2,a,id)
    }

    // Fake unrolling
    // e.g. change 2i + 3 with par(i) = 2 into
    // 4i + 0*2 + 3 = 4i + 3
    // 4i + 1*2 + 3 = 4i + 5
    Seq.tabulate(total){x =>
      val id = Seq.tabulate(ndims){d => (x / prods(d)) % ps(d) }
      access.map{vector => expand(vector, id) }.toArray
    }
  }

  // HACK: Convert streaming accesses to look like indexed accesses for the solver
  // TODO: Better way to handle these cases?
  // NOTE: This only works if the solver views the memory as 1D (like LineBuffer)
  protected def getStreamingAccessVectors(readers: Seq[Access], writers: Seq[Access]): (Seq[Seq[CompactVector]], Seq[Seq[CompactVector]]) = {
    if (readers.nonEmpty || writers.nonEmpty) {
      if (dims.length != 1) {
        bug(mem.ctx, "Cannot create streaming accesses on memory with more than one dimension")
        bug(s"${str(mem)}")
        readers.foreach { rd => bug(s"${str(rd.node)}") }
        writers.foreach { wr => bug(s"${str(wr.node)}") }
      }
      def createVector(access: Access) = {
        val i = iteratorsBetween(access, (parentOf(mem).get,-1)).last
        Seq(CompactAffineVector(Array(1), Seq(i), 0, access))
      }
      getUnaddressedPars(readers ++ writers)

      val readVectors = readers.map(createVector)
      val writeVectors = writers.map(createVector)
      (readVectors, writeVectors)
    }
    else (Nil,Nil)
  }

  protected def createAccessMatrices(readers: Seq[Access], writers: Seq[Access]): (Seq[AccessMatrix],Seq[AccessMatrix]) = {
    val (addrReaders,streamReaders) = readers.partition(rd => isAccessWithoutAddress(rd.node))
    val (addrWriters,streamWriters) = writers.partition(wr => isAccessWithoutAddress(wr.node))
    val (streamReadVectors, streamWriteVectors) = getStreamingAccessVectors(streamReaders,streamWriters)

    val readDenseVectors = addrReaders.flatMap(getAccessVector) ++ streamReadVectors
    val writeDenseVectors = addrWriters.flatMap(getAccessVector) ++ streamWriteVectors
    val indices = (readDenseVectors ++ writeDenseVectors).flatMap(_.flatMap(_.is)).distinct.sortBy{case s:Dyn[_] => s.id}

    registerIndices(indices)
    val readMatrices = readDenseVectors.flatMap{vec => unrollAccess(vec, indices) }
    val writeMatrices = writeDenseVectors.flatMap{vec => unrollAccess(vec, indices) }

    (readMatrices, writeMatrices)
  }

  def bank(readers: Seq[Access], writers: Seq[Access]): Seq[MemoryInstance] = {
    val (readMatrices, writeMatrices) = createAccessMatrices(readers, writers)

    val readGroups = readMatrices.groupBy(_.access.ctrl).toSeq.flatMap{case (ctrl,reads) =>
      val groups = ArrayBuffer[ArrayBuffer[AccessMatrix]]()
      reads.foreach{read =>
        val grpId = groups.indexWhere{grp => grp.forall{r => !read.intersects(r) } }
        if (grpId != -1) groups(grpId) += read  else groups += ArrayBuffer(read)
      }
      groups
    }

    mergeReadGroups(readGroups.map(_.toSet), writeMatrices)

    // TODO: Finalize into MemoryInstances
  }

  def mergeReadGroups(readGroups: Seq[Set[AccessMatrix]], writeMatrices: Seq[AccessMatrix]): Seq[InstanceGroup] = {
    val instances = ArrayBuffer[InstanceGroup]()

    // Greedy implementation
    // TODO: Add exhaustive implementation after pruning?
    readGroups.foreach{group =>
      val groupCtrls = group.map(_.access.ctrl)
      val groupWrites = reachingWrites(group, writeMatrices)
      val groupBanking = bankAccesses(group, groupWrites)
      // TODO: Multiple metapipe parents should cause backtrack eventually
      val (groupMetapipe, groupPorts) = findMetaPipe(mem,group.map(_.access).toSeq, groupWrites.map(_.access).toSeq)
      val groupDepth = groupPorts.values.max + 1
      val groupCost = cost(groupBanking, groupDepth)

      val addedIdx = instances.indexWhere{instance =>
        // We already separated out the reads which can't be banked in the same controller, so skip groups which
        // contain any of the same read controllers
        val commonCtrls = instance.ctrls.exists{x => groupCtrls.contains(x) }
        if (!commonCtrls) {
          val reads  = group ++ instance.reads
          val writes = reachingWrites(reads, writeMatrices)
          val ctrls  = instance.ctrls ++ groupCtrls
          val metapipeLCAs = findAllCtrlMetaPipes(mem, ctrls)
          if (metapipeLCAs.size <= 1) {
            // Only allow buffer merging if it is enabled (e.g. disabled for PIR generation)
            if (instance.metapipe.isDefined && groupMetapipe.isDefined && spatialConfig.enableBufferCoalescing) {
              val (metapipe, ports) = findMetaPipe(mem,reads.map(_.access).toSeq, writes.map(_.access).toSeq)
              val banking = bankAccesses(reads, writes)
              val depth = ports.values.max + 1
              val combinedCost = cost(banking, depth)
              if (combinedCost < groupCost + instance.cost) {
                // Merge in to this instance
                instance.reads = reads
                instance.writes = writes
                instance.ctrls = ctrls
                instance.metapipe = metapipe
                instance.banking = banking
                instance.depth = depth
                instance.cost = combinedCost
                instance.ports = ports
                true
              }
              else false
            }
            else false
          }
          else false
        }
        else false
      }
      if (addedIdx == -1) {
        instances += InstanceGroup(group,groupWrites,groupCtrls,groupMetapipe,groupBanking,groupDepth,groupCost,groupPorts)
      }
    }

    instances
  }


  /**
    // A memory is an accumulator if a writer depends on a reader in the same pipe
    // or if this memory is used as an accumulator by a Reduce or MemReduce
    // and at least one of the writers is in the same control node as the reader
    val isImperativeAccum = reader.exists{read => writers.exists(_.node.dependsOn(read.node)) }
    val isReduceAccum = mem match {
      case s: Dyn[_] => s.dependents.exists{
        case Def(e: OpReduce[_])      => e.accum == s && reader.exists{read => writers.exists(_.ctrl == read.ctrl)}
        case Def(e: OpMemReduce[_,_]) => e.accum == s && reader.exists{read => writers.exists(_.ctrl == read.ctrl)}
        case _ => false
      }
      case _ => false
    }
    val isAccum = isImperativeAccum || isReduceAccum
    metapipe match {
      // Metapipelined case: partition accesses based on whether they're n-buffered or time multiplexed w/ buffer
      case Some(parent) =>
        val (nbuf, tmux) = accesses.partition{access => lca(access.ctrl, parent).get == parent }

        def allPorts = List.tabulate(depth){i=>i}.toSet
        val bufPorts = Map(nbuf.map{a => a -> Set(ports(a)) } ++ tmux.map{a => a -> allPorts} : _*)
        val bufSwaps = Map(nbuf.map{a => a -> childContaining(parent, a) } : _*)
        InstanceGroup(metapipe, accesses, bufferedChannels, bufPorts, bufSwaps)

      // Time-multiplexed case:
      case None =>
        val muxPorts = ports.map{case (key, port) => key -> Set(port)}
        InstanceGroup(None, accesses, bufferedChannels, muxPorts, Map.empty)
    }
  */
}
