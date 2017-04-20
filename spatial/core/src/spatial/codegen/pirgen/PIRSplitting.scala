package spatial.codegen.pirgen

import spatial.SpatialExp

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

import scala.util.control.NoStackTrace

trait PIRSplitting extends PIRTraversal {
  val IR: SpatialExp with PIRCommonExp
  import IR._

  class SplitException(val msg: String) extends Exception("Unable to split!")


  var SCALARS_PER_BUS = 2

  case class SplitStats(
    cus:    Int = 0,
    ccus:   Int = 0,
    ucus:   Int = 0,
    alus:   Int = 0,
    mems:   Int = 0,
    sclIn:  Int = 0,
    sclOut: Int = 0,
    vecIn:  Int = 0,
    vecOut: Int = 0
  ) {
    def +(that: SplitStats) = SplitStats(
      cus    = this.cus + that.cus,
      ccus   = this.ccus + that.ccus,
      ucus   = this.ucus + that.ucus,
      alus   = this.alus + that.alus,
      mems   = this.mems + that.mems,
      sclIn  = this.sclIn + that.sclIn,
      sclOut = this.sclOut + that.sclOut,
      vecIn  = this.vecIn + that.vecIn,
      vecOut = this.vecOut + that.vecOut
    )

    override def toString = s"$cus, $ccus, $ucus, " +
                            s"$alus, $mems, $sclIn, $sclOut, $vecIn, $vecOut"

    def heading = "CUs, CCUs, UCUs, ALUs, SRAMs, SclIn, SclOut, VecIn, VecOut"
  }

  def getStats(cu: CU, others: Iterable[CU]) = SplitStats(
    cus = 1,
    ccus = if (cu.allStages.isEmpty) 1 else 0,
    ucus = if (cu.isUnit) 1 else 0,
    alus = nUsedALUs(cu),
    mems = nMems(cu, others),
    sclIn = nScalarIn(cu),
    sclOut = nScalarOut(cu),
    vecIn = nVectorIns(cu, others),
    vecOut = nVectorOuts(cu)
  )

  def nUsedALUs(cu: CU): Int = {
    val lanes = if (cu.isUnit) 1 else LANES

    cu.allStages.map{
      case MapStage(op,_,_) if op != PIRBypass => lanes
      case _:ReduceStage => lanes // ALUs used by reduction tree
      case _ => 0
    }.fold(0){_+_}
  }

  def nMems(cu: CU, others: Iterable[CU]): Int = {
    val sramWritePorts = cu.mems.flatMap(_.writePort)
    //TODO: readports?
    val nonRetimedGroups = groupBuses(globalInputs(cu) diff sramWritePorts)
    cu.mems.size + nMems(nonRetimedGroups, others)
  }
  def nMems(groups: BusGroups, others: Iterable[CU]): Int = {
    val scalarGrps = if (groups.scalars.nonEmpty) {
      val producers = groups.scalars.groupBy{bus => others.find{cu => scalarOutputs(cu) contains bus}}
      producers.values.map{ss => Math.ceil(ss.size.toDouble / SCALARS_PER_BUS).toInt }.sum
    }
    else 0

    scalarGrps
  }


  def nScalarIn(cu: CU) = {
    val groups = groupBuses(globalInputs(cu))
    groups.args.size + groups.scalars.size
  }
  def nScalarOut(cu: CU) = {
    val groups = groupBuses(globalOutputs(cu))
    groups.scalars.size
  }

  def nVectorIns(cu: CU, others: Iterable[CU]): Int = {
    val groups = groupBuses(globalInputs(cu))
    nVectorIns(groups, others)
  }
  def nVectorIns(groups: BusGroups, others: Iterable[CU]): Int = {
    val argGrps  = Math.ceil(groups.args.size.toDouble / SCALARS_PER_BUS).toInt

    val scalarGrps = if (groups.scalars.nonEmpty) {
      val producers = groups.scalars.groupBy{bus => others.find{cu => scalarOutputs(cu) contains bus}}
      producers.values.map{ss => Math.ceil(ss.size.toDouble / SCALARS_PER_BUS).toInt }.sum
    }
    else 0

    groups.vectors.size + argGrps + scalarGrps
  }


  def nVectorOuts(cu: CU): Int = {
    val groups = groupBuses(globalOutputs(cu))
    nVectorOuts(groups)
  }
  def nVectorOuts(groups: BusGroups, countScalars: Boolean = true): Int = {
    val scalarGrps = if (countScalars) {
      Math.ceil(groups.scalars.size.toDouble / SCALARS_PER_BUS).toInt
    }
    else 0

    groups.vectors.size + scalarGrps
  }

  def reportStats(stats: SplitStats) {
    val SplitStats(cus, ccus, ucus, alus, mems, sclIn, sclOut, vecIn, vecOut) = stats
    dbg(s"  cus: $cus, ccus: $ccus, ucus: $ucus, alus: $alus, mems: $mems, ")
    dbg(s"  sclIn: $sclIn, sclOut: $sclOut, vecIn: $vecIn, vecOut: $vecOut")
  }

  case class SplitCost(
    sIn:   Int = 0, // Scalar inputs
    sOut:  Int = 0, // Scalar outputs
    vIn:   Int = 0, // Vector inputs
    vOut:  Int = 0, // Vector outputs
    vLoc:  Int = 0, // Vector feedback registers
    comp:  Int = 0, // Compute stages
    write: Int = 0, // Write stages (assumed to be before compute)
    read:  Int = 0, // Read stages (assumed to at least partially overlap with compute)
    mems:  Int = 0  // SRAMs
  ) {
    def >(that: SplitCost) = (this.sIn > that.sIn || this.vIn > that.vIn || this.vOut > that.vOut || this.vLoc > that.vLoc ||
                              this.comp > (that.comp + that.write) || this.write > that.write ||
                              this.read > that.read || this.mems > that.mems)

    def toStat = SplitStats(alus = comp+write, mems = this.mems, sclIn = sIn, sclOut = sOut, vecIn = vIn, vecOut = vOut)

    override def toString = s"  sIn: $sIn, sOut: $sOut, vIn: $vIn, vOut: $vOut, vLoc: $vLoc, " +
                            s"comp: $comp, wrt: $write, read: $read, mems: $mems"
  }

  def report(cost: SplitCost) = dbg(cost.toString)

  /**
    CU splitting is graph partitioning, where each partition has a limited number of inputs, outputs, and nodes

    Given a set of stages, we want to find a set of minimum cuts where each partition satisfies these limits

    This is more restricted/complex than the standard graph partitioning problem as nodes and edges cannot be treated uniformly:
    - Reduction stages represent multiple real ALU stages in the architecture (logV + 1 ?)
    - Reduction stages cannot directly consume CU inputs (requires a bypass stage)
    - Reduction stages cannot directly produce CU outputs (requires a bypass stage)
    - Write address computation stages MUST be preserved/copied within consumer CUs
    - Local vector writes: address and data must be available in the same CU

    Nodes must be duplicated/created depending on how the graph is partitioned
    Could model this as conditional costs for nodes in context of their partition?
   **/
  case class WriteGroup(mems: Seq[CUMemory], stages: Seq[Stage])

  class Partition(write: Map[Int, WriteGroup], compute: ArrayBuffer[Stage], mems: Set[CUMemory], owners: Set[LocalRef], cc: Option[CUCChain], edge: Boolean) {
    var wstages = Map[Int, WriteGroup]() ++ write
    var cstages: List[Stage] = compute.toList
    var cchains: Set[CUCChain] = Set[CUCChain]()
    var srams: Set[CUMemory] = Set[CUMemory]() ++ mems

    private val sramOwners: Set[LocalRef] = owners
    private val isEdge: Boolean = edge
    private val ctrl: Option[CUCChain] = cc

    recomputeSRAMs()
    recomputeCChains(false)

    def nonEmpty = wstages.nonEmpty || cstages.nonEmpty

    def allStages: Iterator[Stage] = cstages.iterator ++ wstages.values.flatMap(_.stages.iterator)

    def spliceSRAMOwner(): Boolean = {
      val ownerIdx = cstages.indexWhere{
        case stage@MapStage(PIRBypass,ins,outs) =>
          !outs.exists{case LocalRef(_,MemLoadReg(_)) => true; case _ => false} &&
          (ins exists (sramOwners contains _))

        case stage@MapStage(op,ins,_) => (ins exists (sramOwners contains _))
        case _ => false
      }
      if (ownerIdx > -1) {
        val stage = cstages(ownerIdx)

        val owner = stage.inputRefs.find(sramOwners contains _).get
        val output = LocalRef(owner.stage+1, owner.reg) // Stage doesn't matter here
        val bypass = MapStage(PIRBypass, List(owner), List(output))

        stage match {
          case stage@MapStage(op,ins,outs) => stage.ins = stage.ins.map{case `owner` => output; case ref => ref}
          case _ => throw new Exception("Cannot have ReduceStage as SRAM owner")
        }
        assert(!stage.inputRefs.contains(owner))
        assert(stage.inputRefs.contains(output))
        cstages = cstages.take(ownerIdx) ++ (bypass +: cstages.drop(ownerIdx))

        true
      }
      else false
    }

    def popHead(n: Int = 1) = {
      val stages = cstages.take(n)
      cstages = cstages.drop(n)
      val (keep, drop) = recomputeOwnedSRAMs(this, sramOwners)
      wstages = keep
      recomputeCChains(false)
      (stages, drop, cstages.isEmpty)
    }

    def popTail(n: Int = 1) = {
      val stages = cstages.takeRight(n)
      cstages = cstages.dropRight(n)
      val (keep, drop) = recomputeOwnedSRAMs(this, sramOwners)
      wstages = keep
      recomputeCChains(false)
      (stages, drop, cstages.isEmpty)
    }

    private def recomputeCChains(nowEdge: Boolean) {
      recomputeOwnedCChains(this, ctrl, isEdge || nowEdge)
    }

    private def recomputeSRAMs() {
      val (keep, drop) = recomputeOwnedSRAMs(this, sramOwners)
      wstages = keep
    }

    private def add(writes: Map[Int, WriteGroup]) {
      val keys = wstages.keySet ++ writes.keySet
      val joined = keys.map{k => (wstages.get(k), writes.get(k)) match {
        case (Some(a),Some(b)) => k -> WriteGroup((a.mems ++ b.mems).distinct, a.stages)
        case (Some(a), None)   => k -> a
        case (None, Some(b))   => k -> b
        case (None, None)      => throw new Exception(s"Not a possible combination")
      }}.toMap
      wstages = joined
    }

    def addTail(drop: (List[Stage], Map[Int, WriteGroup], Boolean)) {
      cstages = cstages ++ drop._1
      add(drop._2)
      recomputeSRAMs()
      recomputeCChains(drop._3)
    }

    def addHead(drop: (List[Stage], Map[Int, WriteGroup], Boolean)) {
      cstages = drop._1 ++ cstages
      add(drop._2)
      recomputeSRAMs()
      recomputeCChains(drop._3)
    }

  }
  object Partition {
    def empty(owners: Set[LocalRef], cc: Option[CUCChain], isEdge: Boolean) = {
      new Partition(Map[Int,WriteGroup](), ArrayBuffer[Stage](), Set[CUMemory](), owners, cc, isEdge)
    }
  }

  // Recompute required memories + write stages for given compute stages
  // Includes handling of nested memory write + reads (although not clear this is well supported)
  // TODO: readStages
  def recomputeOwnedSRAMs(p: Partition, sramOwners: Set[LocalRef]) = {

    def writeStages(inputs: Set[LocalRef]) = {
      val owners = inputs intersect sramOwners
      val srams = owners.collect{case LocalRef(_,MemLoadReg(sram)) => sram}

      p.wstages.filter{case (i,grp) => grp.mems.exists(srams contains _)}
    }

    var inputs: Set[LocalRef] = p.cstages.flatMap(_.inputRefs).toSet
    var writes: Map[Int,WriteGroup] = writeStages(inputs)
    var prevSize: Int = -1
    while (inputs.size != prevSize) {
      prevSize = inputs.size
      inputs ++= writes.values.flatMap(_.stages).flatMap(_.inputRefs)
      writes = writeStages(inputs)
    }

    val ownedSRAMs = (inputs intersect sramOwners).collect{case LocalRef(_,MemLoadReg(sram)) => sram}
    p.srams = ownedSRAMs

    val keep = p.wstages.flatMap{case (i,grp) =>
      val keys = grp.mems filter (ownedSRAMs contains _)
      if (keys.nonEmpty) Some(i -> WriteGroup(keys, grp.stages)) else None
    }
    val drop = p.wstages.flatMap{case (i,grp) =>
      val keys = grp.mems filterNot (ownedSRAMs contains _)
      if (keys.nonEmpty) Some(i -> WriteGroup(keys,grp.stages)) else None
    }
    drop.foreach{case (k,v) =>
      v.mems.foreach{m => dbg(s"  [drop] $k -> $m")}
    }
    (keep, drop)
  }

  def recomputeOwnedCChains(p: Partition, /*control: Iterable[Stage],*/ ctrl: Option[CUCChain], isEdge: Boolean) = {
    p.cchains = usedCChains(p.allStages) ++ usedCChains(p.srams) /*++ usedCChains(control)*/

    if (isEdge && ctrl.isDefined) p.cchains += ctrl.get
  }


  // Calculate total cost for given partition
  def partitionCost(p: Partition, prev: ArrayBuffer[Partition], all: List[Stage], others: Iterable[CU], isUnit: Boolean) = {
    dbg(s"\n\n")
    dbg(s"  Write stages:")
    for ((i,grp) <- p.wstages) {
      dbg(s"    Memories: " + grp.mems.map(_.name).mkString(", "))
      grp.stages.foreach{stage => dbg(s"      $stage")}
    }
    dbg(s"  Compute stages: ")
    p.cstages.foreach{stage => dbg(s"    $stage") }

    dbg(s"  CChains: ")
    p.cchains.foreach{cc => dbg(s"    $cc :: " + globalInputs(cc).mkString(", ")) }

    val local  = p.allStages.toList
    val remote = all diff local

    val localIns: Set[LocalComponent] = local.flatMap(_.inputMems).toSet filterNot(isControl(_))
    val localOuts: Set[LocalComponent] = local.flatMap(_.outputMems).toSet
    val remoteIns: Set[LocalComponent] = remote.flatMap(_.inputMems).toSet
    val remoteOuts: Set[LocalComponent] = remote.flatMap(_.outputMems).toSet

    val localReads: Set[CUMemory] = localIns.collect{case MemLoadReg(sram) => sram}
    val remoteReads: Set[CUMemory] = remoteIns.collect{case MemLoadReg(sram) => sram}
    // Inserted bypass reads
    val localBypasses: Set[CUMemory] = localOuts.collect{case MemLoadReg(sram) => sram}
    val remoteBypasses: Set[CUMemory] = remoteOuts.collect{case MemLoadReg(sram) => sram}

    val localWrites: Set[CUMemory] = localOuts.collect{case FeedbackDataReg(sram) => sram}
    val localAddrs: Set[CUMemory] = localOuts.collect{case FeedbackAddrReg(sram) => sram; case ReadAddrWire(sram) => sram}

    val remoteAddrs = remoteOuts.collect{case reg:FeedbackAddrReg => reg; case reg:ReadAddrWire => reg}

    // --- CU inputs and outputs
    val cuInBuses = globalInputs(localIns) ++ globalInputs(p.srams) ++ globalInputs(p.cchains)
    val cuOutBuses = globalOutputs(localOuts)

    val cuGrpsIn = groupBuses(cuInBuses)
    val cuGrpsOut = groupBuses(cuOutBuses)

    var vIns: Int   = nVectorIns(cuGrpsIn, others)
    var vOuts: Int  = nVectorOuts(cuGrpsOut, countScalars = false)
    var vLocal: Int = 0

    var sIns = Map[Int, Int]()
    def addIn(part: Int) {
      if (!sIns.contains(part)) sIns += part -> 1
      else sIns += part -> (sIns(part) + 1)
    }
    var sOuts: Int = localOuts.count{case ScalarOut(_) => true; case _ => false}

    var nSRAMs: Int = 0

    // --- SRAMs
    nSRAMs += p.srams.size

    val sramWritePorts = p.srams.flatMap(_.writePort)
    //TODO: readports?
    val nonRetimedGroups = groupBuses(cuInBuses diff sramWritePorts)

    nSRAMs += nMems(nonRetimedGroups, others)


    // TODO: Have to be more careful not to double count here!
    // Can double count for bypasses and multiple outputs for single stage

    dbg(s"  SRAMs: ")
    dbg(s"    Locally hosted: ")
    p.srams.foreach{sram => dbg(s"      $sram :: " + globalInputs(sram).mkString(", ")) }

    // 1. Inputs to account for remotely hosted, locally read SRAMs
    // EXCEPT ones which we've already inserted bypass vectors for (appear as live ins)
    // Needs bypass: NO
    val remoteHostLocalRead = (localReads diff p.srams) diff remoteBypasses
    if (!isUnit) {
      vIns += remoteHostLocalRead.size
      nSRAMs += remoteHostLocalRead.size // Retiming
    }
    else {
      remoteHostLocalRead.foreach{sram =>
        addIn(prev.indexWhere(_.srams contains sram))
      }
    }
    dbg(s"    Remotely hosted, locally read: " + remoteHostLocalRead.mkString(", "))

    // 2. Outputs to account for locally hosted, remotely read SRAMs
    // EXCEPT ones which we've already inserted bypass vectors for (appear as live outs)
    // Needs bypass: YES
    val localHostRemoteRead = (remoteReads intersect p.srams) diff localBypasses
    if (!isUnit) vOuts += localHostRemoteRead.size
    else         sOuts += localHostRemoteRead.size

    dbg(s"    Locally hosted, remotely read: " + localHostRemoteRead.mkString(", "))


    // 3. Outputs to account for feedback data to locally hosted SRAMs
    // Needs bypass: NO
    val localHostLocalWrite = localWrites intersect p.srams
    vLocal += localHostLocalWrite.size

    dbg(s"    Locally hosted, locally written: " + localHostLocalWrite.mkString(", "))

    // 4. Outputs for feedback data to remotely hosted SRAMs
    // Needs bypass: NO
    val remoteHostLocalWrite = localWrites diff p.srams
    if (!isUnit) vOuts += remoteHostLocalWrite.size
    else         sOuts += remoteHostLocalWrite.size

    dbg(s"    Remotely hosted, locally written: " + remoteHostLocalWrite.mkString(", "))


    // 5. Outputs for read or feedback addresses to remotely hosted SRAMs
    // Needs bypass: NO
    val remoteHostLocalAddr = localAddrs diff p.srams
    if (!isUnit) vOuts += remoteHostLocalAddr.size
    else         sOuts += remoteHostLocalAddr.size

    dbg(s"    Remotely hosted, locally addressed: " + remoteHostLocalAddr.mkString(", "))


    // 6. Inputs for read or feedback addresses to locally hosted CUs
    // Needs bypass: YES
    val localHostRemoteAddr = remoteAddrs filter {
      case FeedbackAddrReg(sram) => p.srams contains sram
      case ReadAddrWire(sram)    => p.srams contains sram
    }
    if (!isUnit) {
      vIns += localHostRemoteAddr.size
      nSRAMs += localHostRemoteAddr.size // Retiming vector
    }
    else {
      val prevOuts = prev.map(_.allStages.flatMap(_.outputMems).toSet)

      localHostRemoteAddr.foreach{wire =>
        addIn(prevOuts.indexWhere(_ contains wire))
      }
    }

    val remotelyAddressed = localHostRemoteAddr.collect{
      case FeedbackAddrReg(sram) => sram
      case ReadAddrWire(sram) => sram
    }
    dbg(s"    Locally hosted, remotely addressed: " + remotelyAddressed.mkString(", "))


    // --- Registers
    dbg(s"  Arg ins: " + cuGrpsIn.args.mkString(", "))

    val scalars = cuGrpsIn.scalars.map{bus => s"$bus [" + others.find{cu => scalarOutputs(cu) contains bus}.map(_.name).getOrElse("X") + "]" }

    dbg(s"  Scalar ins: " + scalars.mkString(", "))
    dbg(s"  Vector ins: " + cuGrpsIn.vectors.mkString(", "))
    dbg(s"  Scalar outs: " + cuGrpsOut.scalars.mkString(", "))
    dbg(s"  Vector outs: " + cuGrpsOut.vectors.mkString(", "))
    // Live inputs from other partitions
    val liveIns  = localIns intersect remoteOuts
    if (!isUnit) {
      vIns += liveIns.size
      nSRAMs += liveIns.size // Retiming vectors
    }
    else {
      liveIns.foreach{in =>
        addIn(prev.indexWhere{part => localOutputs(part.allStages) contains in})
      }
    }

    dbg(s"  Live ins: " + liveIns.mkString(", "))

    // Live outputs to other partitions
    val liveOuts = (remoteIns intersect localOuts)
    if (!isUnit) vOuts += liveOuts.size
    else         sOuts += liveOuts.size

    dbg(s"  Live outs: " + liveOuts.mkString(", "))

    // Pack scalar inputs and outputs into vectors
    sIns.values.foreach{ins =>
      val grpSize = Math.ceil(ins.toDouble / SCALARS_PER_BUS).toInt
      vIns += grpSize
      nSRAMs += grpSize // Retiming scalar bus
    }


    // Count the scalar/vector outputs for each stage independently to avoid double counting
    /*
    var sOuts = 0
    var vOuts = 0

    p.cstages.foreach{
      case MapStage(op,ins,outs) =>
        if (op != Bypass) {
          ins.foreach{
            case ref@LocalRef(_,MemLoadReg(sram)) =>
              if (sramOwners.contains(ref) && localHostRemoteRead.contains(sram)) {
                if (isUnit) sOuts += 1
                else        vOuts += 1
              }
            case _ => // No output
          }
        }
        val scalOrVec = outs.collect{
          case _:ScalarOut => false
          case _:VectorOut => true
          case reg if (liveOuts contains reg) => !isUnit
          case ReadAddrWire(sram) if remoteHostLocalAddr contains sram => !isUnit
          case FeedbackAddrReg(sram) if remoteHostLocalAddr contains sram => !isUnit
          case FeedbackDataReg(sram) if remoteHostLocalWrite contains sram => !isUnit
        }
        if (scalOrVec.nonEmpty) {
          val isVec = scalOrVec.exists(_ == true)
          if (isVec) vOuts += 1
          else       sOuts += 1
        }

      case ReduceStage(op,init,in,acc) =>
        if (liveOuts contains acc)
          sOuts += 1
    }*/

    vOuts += Math.ceil(sOuts.toDouble / SCALARS_PER_BUS).toInt


    // --- Bypass stages
    val sramBypasses  = localHostRemoteRead.size + localHostRemoteAddr.size
    val stageBypasses = p.cstages.map{
      case ReduceStage(op,init,in,acc) =>
        val bypassInCost  = if (localIns.contains(in.reg)) 0 else 1
        val bypassOutCost = if (remoteIns.contains(acc))   1 else 0
        bypassInCost + bypassOutCost
      case MapStage(PIRBypass,ins,outs) => 1
      case _ => 0
    }.sum

    val bypasses = sramBypasses + stageBypasses

    // --- Compute
    val rawCompute = p.cstages.map{
      case MapStage(op,ins,outs) => if (op == PIRBypass) 0 else 1
      case ReduceStage(op,init,in,acc) => REDUCE_STAGES
    }.sum

    val writes = p.wstages.values.flatMap(_.stages).size
    // TODO: Could reschedule stages such that reads occur earlier
    val reads = p.cstages.lastIndexWhere{stage => stage.outputMems.exists(_.isInstanceOf[ReadAddrWire])} + 1

    // Locally hosted, remotely read SRAMs require bypass registers
    val compute = rawCompute + bypasses

    // TODO: One virtual memory may cost more than one physical memory...

    // Scalars
    val sclIns = sIns.values.sum + cuGrpsIn.args.size + cuGrpsIn.scalars.size

    val cost = SplitCost(sclIns, sOuts, vIns, vOuts, vLocal, compute, writes, reads, nSRAMs)

    dbg(s"  $cost")

    cost
  }

  def getSRAMOwners(stageGroups: List[List[Stage]]): Set[LocalRef] = {

    def sramFromRef(x: LocalRef) = x match {case LocalRef(_,MemLoadReg(sram)) => Some(sram); case _ => None}
    def sramRefs(x: Seq[LocalRef]) = x.collect{case ref@LocalRef(_,MemLoadReg(_)) => ref}

    // Group set of stages into references for each memory
    def groupByMem(stages: List[Stage]): List[List[LocalRef]] = stages.flatMap{stage => sramRefs(stage.inputRefs) }.groupBy(sramFromRef).values.toList
    // Get reference with lowest stage number
    def getMemOwner(refs: List[LocalRef]): LocalRef = refs.reduce{(refA,refB) => if (refA.stage < refB.stage) refA else refB}

    stageGroups.map{stages: List[Stage] => groupByMem(stages) }.flatMap{mems => mems.map{refs => getMemOwner(refs)}}.toSet
  }


  //TODO: readStages
  def splitCU(cu: CU, arch: SplitCost, others: Iterable[CU]): List[CU] = {
    val writeGroups = Map[Int,WriteGroup]() ++ cu.writeStages.zipWithIndex.map{case ((mems,stages),i) => i -> WriteGroup(mems,stages.toList) }
    val sramOwners = getSRAMOwners(cu.computeStages.toList +: cu.writeStages.values.map(_.toList).toList)

    dbg("\n\n\n")
    dbg(s"Splitting CU: $cu")
    dbg(s"Compute: ")
    cu.computeStages.foreach{stage => dbg(s"  $stage")}
    dbg(s"Write Groups: ")
    writeGroups.foreach{case (i,grp) =>
      dbg(s"  Group #$i: ${grp.mems}")
      grp.stages.foreach{stage => dbg(s"    $stage") }
    }
    dbg("SRAM Owners:")
    sramOwners.foreach{s => dbg(s"  $s")}
    // Sanity check
    // HACK: Eliminate SRAMs without readers implicitly here
    /*cu.srams.foreach{sram =>
      val owner = sramOwners.find{case LocalRef(_,MemLoadReg(`sram`)) => true; case _ => false}
      if (owner.isEmpty) {
        throw new Exception(s"CU $cu does not have an owner reference for memory $sram")
      }
    }*/

    val isUnit = cu.isUnit
    val allStages = cu.allStages.toList

    val ctrl = cu.cchains.find{case _:UnitCChain | _:CChainInstance => true; case _ => false}


    val partitions = ArrayBuffer[Partition]()
    var current: Partition = Partition.empty(sramOwners, ctrl, true)
    val remote: Partition = new Partition(writeGroups, cu.computeStages, cu.mems, sramOwners, ctrl, false)

    def getCost(p: Partition) = partitionCost(p, partitions, allStages, others, isUnit)

    while (remote.nonEmpty) {
      dbg(s"Computing partition ${partitions.length}")

      // Bite off a chunk to maximize compute usage
      current addTail remote.popHead(arch.comp + arch.write)
      var cost = getCost(current)

      while (!(cost > arch) && remote.nonEmpty) {
        //dbg(s"Adding stages until exceeds cost")
        current addTail remote.popHead()
        cost = getCost(current)
      }

      while (cost > arch && current.nonEmpty) {
        //dbg(s"Removing stage")
        remote addHead current.popTail()
        cost = getCost(current)
      }

      if (current.cstages.isEmpty) {
        // Find first SRAM owner which can still be spliced
        dbg(s"Failed splitting. Checking for splittable SRAM owner stages...")
        if ( !remote.spliceSRAMOwner() ) {
          dbg(s"No remaining SRAM owners to split.")

          var errReport = s"Failed splitting in $cu"
          errReport += "\nWrite stages: "
          for ((i,grp) <- remote.wstages) {
            errReport += s"\n  Group #$i: " + grp.mems.mkString(", ")
            grp.stages.foreach{stage => errReport += s"\n    $stage"}
          }
          errReport += s"\nCompute stages: "
          remote.cstages.foreach{stage => errReport += s"\n  $stage" }

          errReport += "\nCost for last split option: "
          current addTail remote.popHead()
          val cost = getCost(current)
          errReport += s"\n$cost"
          throw new SplitException(errReport) with NoStackTrace
        }
      } // end if empty
      else {
        dbg(s"Partition ${partitions.length}")
        report(getCost(current))
        dbg(s"Write stages:")
        for ((i,grp) <- current.wstages) {
          dbg(s"    Memories: " + grp.mems.map(_.name).mkString(", "))
          grp.stages.foreach{stage => dbg(s"      $stage")}
        }
        dbg(s"  Compute stages: ")
        current.cstages.foreach{stage => dbg(s"    $stage") }

        partitions += current
        current = Partition.empty(sramOwners, ctrl, false)
      }
    } // end while

    val parent = if (partitions.length > 1) {
      val parent = ComputeUnit(cu.name, cu.pipe, StreamCU)
      parent.parent = cu.parent
      parent.deps = cu.deps
      parent.cchains ++= cu.cchains
      Some(parent)
    }
    else None

    val cus = partitions.zipWithIndex.map{case (p,i) =>
      schedulePartition(orig = cu, p, i, parent)
    }

    cus.zip(partitions).zipWithIndex.foreach{case ((cu,p), i) =>
      dbg(s"Partition #$i: $cu")
      val cost = getCost(p)
      dbg(s"Cost: ")
      report(cost)
      dbg(s"Stats: ")
      reportStats(getStats(cu, cus.filterNot(_ == cu)++others))

      dbg(s"  Write stages: ")
      for ((mems,stages) <- cu.writeStages) {
        dbg(s"    Memories: " + mems.map(_.name).mkString(", "))
        stages.foreach{stage => dbg(s"      $stage")}
      }
      dbg(s"  Compute stages: ")
      cu.computeStages.foreach{stage => dbg(s"    $stage") }
    }

    parent.toList ++ cus.toList
  }



  def schedulePartition(orig: CU, part: Partition, i: Int, parent: Option[CU]): CU = {
    val style = if (parent.isDefined) StreamCU else orig.style
    val cu = ComputeUnit(orig.name+"_"+i, orig.pipe, style)
    cu.parent = if (parent.isDefined) parent else orig.parent
    if (parent.isEmpty) cu.deps ++= orig.deps
    if (parent.isEmpty) cu.cchains ++= orig.cchains

    part.srams.map { sram => cu.memMap += sram.mem -> sram }

    //TODO: readStages
    cu.writeStages ++= part.wstages.map{case (i,WriteGroup(mems,stages)) => mems -> ArrayBuffer(stages:_*) }
    cu.regs ++= cu.writeStages.values.flatMap(stages => stages.flatMap(_.inputMems) ++ stages.flatMap(_.outputMems))

    val local = part.cstages
    val remote = orig.allStages.toList diff part.allStages.toList

    val localIns  = local.flatMap(_.inputMems).toSet ++ localInputs(cu.mems)
    val localOuts = local.flatMap(_.outputMems).toSet

    val remoteIns = remote.flatMap(_.inputMems).toSet
    val remoteOuts = remote.flatMap(_.outputMems).toSet

    val ctx = ComputeContext(cu)

    def globalBus(reg: LocalComponent, isScalar: Boolean): GlobalBus = reg match {
      case ScalarIn(bus) => bus
      case VectorIn(bus) => bus
      case ScalarOut(bus) => bus
      case VectorOut(bus) => bus
      case MemLoadReg(mem)     =>
        val bus = if (isScalar) CUScalar(mem.name+"_data") else CUVector(mem.name+"_data")
        globals += bus
        bus
      case FeedbackAddrReg(mem) =>
        val bus = if (isScalar) CUScalar(mem.name+"_addr") else CUVector(mem.name+"_addr")
        globals += bus
        bus

      case ReadAddrWire(mem) =>
        val bus = if (isScalar) CUScalar(mem.name+"_addr") else CUVector(mem.name+"_addr")
        globals += bus
        bus

      case FeedbackDataReg(mem) =>
        val bus = if (isScalar) CUScalar(mem.name+"_data") else CUVector(mem.name+"_data")
        globals += bus
        bus
      case _                    =>
        val bus = if (isScalar) CUScalar("bus_" + reg.id)    else CUVector("bus_" + reg.id)
        globals += bus
        bus
    }

    def portOut(reg: LocalComponent, isScalar: Boolean) = globalBus(reg,isScalar) match {
      case bus:ScalarBus => ScalarOut(bus)
      case bus:VectorBus => VectorOut(bus)
    }
    def portIn(reg: LocalComponent, isScalar: Boolean) = globalBus(reg,isScalar) match {
      case bus:ScalarBus => ScalarIn(bus)
      case bus:VectorBus => VectorIn(bus)
    }

    def rerefIn(reg: LocalComponent, isScalar: Boolean = orig.isUnit): LocalRef = {
      val in = reg match {
        case _:ConstReg[_] | _:CounterReg => reg
        case _:ValidReg | _:ControlReg => reg
        case _:ReduceMem[_]    => if (localOuts.contains(reg)) reg else portIn(reg, isScalar)
        case MemLoadReg(sram) => if (cu.mems.contains(sram)) reg else portIn(reg, isScalar)
        case _                 => if (cu.regs.contains(reg)) reg else portIn(reg, isScalar)
      }
      cu.regs += in
      ctx.refIn(in)
    }
    def rerefOut(reg: LocalComponent, isScalar: Boolean = orig.isUnit): List[LocalRef] = {
      val outs = reg match {
        case _:ScalarOut => List(reg)
        case _:VectorOut => List(reg)
        case FeedbackAddrReg(sram) => if (cu.mems.contains(sram)) List(reg) else List(portOut(reg,isScalar))
        case FeedbackDataReg(sram) => if (cu.mems.contains(sram)) List(reg) else List(portOut(reg,isScalar))
        case ReadAddrWire(sram) => if (cu.mems.contains(sram)) List(reg) else List(portOut(reg,isScalar))
        case MemLoadReg(sram) => List(portOut(reg,isScalar))
        case _ =>
          val local  = if (localIns.contains(reg)) List(reg) else Nil
          val global = if (remoteIns.contains(reg)) List(portOut(reg, isScalar)) else Nil
          local ++ global
      }
      cu.regs ++= outs
      outs.map{out => ctx.refOut(out)}
    }

    // --- Reconnect remotely computed read addresses (after write stages, before compute)
    cu.mems.foreach{ sram =>
      val remoteAddr = remoteOuts.find{case ReadAddrWire(`sram`) => true; case _ => false}
      val localAddr  = localOuts.find{case ReadAddrWire(`sram`) => true; case _ => false}

      if (remoteAddr.isDefined && localAddr.isEmpty) {
        val reg = ReadAddrWire(sram)
        val addrIn = portIn(reg, orig.isUnit)
        ctx.addStage(MapStage(PIRBypass, List(ctx.refIn(addrIn)), List(ctx.refOut(reg))))
        cu.regs += reg
        cu.regs += addrIn
      }
    }

    // --- Reschedule compute stages
    part.cstages.foreach{
      case MapStage(op, ins, outs) =>
        val inputs = ins.map{in => rerefIn(in.reg) }
        val outputs = outs.flatMap{out => rerefOut(out.reg) }
        ctx.addStage(MapStage(op, inputs, outputs))

      case ReduceStage(op, init, in, acc) =>
        var input = rerefIn(in.reg)
        if (!input.reg.isInstanceOf[ReduceMem[_]]) {
          val redReg = ReduceReg()
          val reduce = ctx.refOut(redReg)
          cu.regs += redReg
          ctx.addStage(MapStage(PIRBypass, List(input), List(reduce)))
          input = ctx.refIn(redReg)
        }
        cu.regs += acc
        ctx.addStage(ReduceStage(op, init, input, acc))

        if (remoteIns.contains(acc)) {
          val bus = portOut(acc, true)
          ctx.addStage(MapStage(PIRBypass, List(ctx.refIn(acc)), List(ctx.refOut(bus))))
        }
    }

    // --- Add bypass stages for locally hosted, remotely read SRAMs
    val remoteSRAMReads = remoteIns.collect{case MemLoadReg(sram) => sram}
    val localBypasses = remoteSRAMReads intersect cu.mems
    localBypasses.foreach{sram =>
      val reg = MemLoadReg(sram)
      val out = portOut(reg, cu.isUnit)

      if (!cu.computeStages.flatMap(_.outputMems).contains(out)) {
        ctx.addStage(MapStage(PIRBypass, List(ctx.refIn(reg)), List(ctx.refOut(out))))
        cu.regs += reg
        cu.regs += out
      }
    }


    // --- Reconnect split feedback paths
    val rescheduledOutputs = cu.computeStages.flatMap(_.outputMems).toSet

    //TODO: readPort?
    cu.mems.foreach{sram => sram.writePort match {
      case Some(LocalVectorBus) =>
        val dataReg = FeedbackDataReg(sram)
        val addrReg = FeedbackAddrReg(sram)

        // TODO: What is the correct thing to do here?
        // TODO: Will the timing still be correct?
        if (!rescheduledOutputs.contains(dataReg)) {
          //sram.vector = Some(globalBus(dataReg, cu.isUnit))
          val in = portIn(dataReg, cu.isUnit)
          ctx.addStage(MapStage(PIRBypass, List(ctx.refIn(in)), List(ctx.refOut(dataReg))))
          cu.regs += in
          cu.regs += dataReg
        }
        if (!rescheduledOutputs.contains(addrReg)) {
          val in = portIn(addrReg, cu.isUnit)
          ctx.addStage(MapStage(PIRBypass, List(ctx.refIn(in)), List(ctx.refOut(addrReg))))
          cu.regs += in
          cu.regs += dataReg
        }

      case _ =>
    }}

    // --- TODO: Control logic


    // --- Copy counters
    if (parent.isDefined) {
      val ctrl = parent.get
      val f = copyIterators(cu, ctrl)

      // Copy all, but only retain those in the partition
      cu.cchains = cu.cchains.filter{cc => part.cchains.exists{_.name == cc.name}}

      // TODO
      def tx(cc: CUCChain): CUCChain = {
        if (f.contains(cc)) f(cc)
        else if (f.values.toList.contains(cc)) cc  // HACK: DSE
        else {
          val mapping = f.map{case (k,v) => s"$k -> $v"}.mkString("\n")
          throw new Exception(s"Attempted to copy counter $cc in CU $ctrl, but no such counter exists.\nMapping:\n$mapping")
        }
      }
      def swap_cchain_Reg(x: LocalComponent) = x match {
        case CounterReg(cc,idx) => CounterReg(tx(cc), idx)
        case ValidReg(cc,idx) => ValidReg(tx(cc), idx)
        case _ => x
      }
      def swap_cchains_Ref(x: LocalRef) = x match {
        case LocalRef(i, reg) => LocalRef(i, swap_cchain_Reg(reg))
      }

      cu.allStages.foreach{
        case stage@MapStage(_,ins,_) => stage.ins = ins.map{swap_cchains_Ref(_)}
        case _ =>
      }
      cu.mems.foreach{sram =>
        sram.readAddr = sram.readAddr.map{swap_cchain_Reg(_).asInstanceOf[Addr]} //TODO refactor this
        sram.writeAddr = sram.writeAddr.map{swap_cchain_Reg(_).asInstanceOf[Addr]}
      }
    }

    cu
  }

}
