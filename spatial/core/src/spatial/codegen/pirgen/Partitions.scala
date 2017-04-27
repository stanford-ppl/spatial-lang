package spatial.codegen.pirgen

import spatial.analysis.SpatialTraversal

import scala.collection.mutable
import spatial.{SpatialConfig, SpatialExp}


trait Partitions extends SpatialTraversal { this: PIRTraversal =>
  val IR: SpatialExp with PIRCommonExp
  import IR._

  var SCALARS_PER_BUS = 2
  var STAGES: Int = 10
  def LANES = SpatialConfig.lanes                          // Number of SIMD lanes per CU
  def REDUCE_STAGES = (Math.log(LANES)/Math.log(2)).toInt  // Number of stages required to reduce across all lanes
  var READ_WRITE = 10

  case class AddrGroup(mems: Seq[CUMemory], stages: Seq[Stage])

  abstract class Partition {
    var cchains: Set[CUCChain] = Set[CUCChain]()
    recomputeCChains(false)

    def nonEmpty: Boolean
    def allStages: Iterable[Stage]
    def isEdge: Boolean
    def ctrl: Option[CUCChain]

    protected def recomputeCChains(nowEdge: Boolean): Unit = recomputeOwnedCChains(this, ctrl, isEdge || nowEdge)

  }
  object Partition {
    def emptyCU(cc: Option[CUCChain], isEdge: Boolean) = new CUPartition(mutable.ArrayBuffer[Stage](), cc, isEdge)
    def emptyMU(cc: Option[CUCChain], isEdge: Boolean) = new MUPartition(Map.empty, Map.empty, cc, isEdge)
  }

  class CUPartition(compute: mutable.ArrayBuffer[Stage], cc: Option[CUCChain], edge: Boolean) extends Partition {
    var cstages: List[Stage] = compute.toList
    val isEdge: Boolean = edge
    val ctrl: Option[CUCChain] = cc

    def nonEmpty = cstages.nonEmpty
    def allStages = cstages

    def popHead(n: Int = 1) = {
      val stages = cstages.take(n)
      cstages = cstages.drop(n)
      recomputeCChains(false)
      (stages, cstages.isEmpty)
    }

    def popTail(n: Int = 1) = {
      val stages = cstages.takeRight(n)
      cstages = cstages.dropRight(n)
      recomputeCChains(false)
      (stages, cstages.isEmpty)
    }


    def addTail(drop: (List[Stage], Boolean)) {
      cstages = cstages ++ drop._1
      recomputeCChains(drop._2)
    }

    def addHead(drop: (List[Stage], Boolean)) {
      cstages = drop._1 ++ cstages
      recomputeCChains(drop._2)
    }
  }

  class MUPartition(write: Map[Int, AddrGroup], read: Map[Int, AddrGroup], cc: Option[CUCChain], edge: Boolean) extends Partition {
    var wstages = Map[Int, AddrGroup]() ++ write
    val rstages = Map[Int, AddrGroup]() ++ read
    val isEdge: Boolean = edge
    val ctrl: Option[CUCChain] = cc

    def nonEmpty = wstages.nonEmpty || rstages.nonEmpty

    def allStages: Iterable[Stage] = wstages.values.flatMap(_.stages.iterator) ++ rstages.values.flatMap(_.stages.iterator)
  }

  def recomputeOwnedCChains(p: Partition, ctrl: Option[CUCChain], isEdge: Boolean) = {
    p.cchains = usedCChains(p.allStages)

    if (isEdge && ctrl.isDefined) p.cchains += ctrl.get
  }

  /**
    * CUCost - resource utilization metrics for PCUs
    */
  abstract class PartitionCost

  case class MUCost(
    sIn:   Int = 0, // Scalar inputs
    sOut:  Int = 0, // Scalar outputs
    vIn:   Int = 0, // Vector inputs
    vOut:  Int = 0, // Vector outputs
    write: Int = 0, // Write stages
    read:  Int = 0  // Read stages
  ) extends PartitionCost {
    def >(that: MUCost) = {
      this.sIn > that.sIn || this.vIn > that.vIn ||
      this.vOut > that.vOut || this.write > that.write || this.read > that.read
    }
    //def toUtil = Utilization(alus = comp, sclIn = sIn, sclOut = sOut, vecIn = vIn, vecOut = vOut)
    override def toString = s"  sIn: $sIn, sOut: $sOut, vIn: $vIn, vOut: $vOut, write: $write, read: $read"
  }

  case class CUCost(
    sIn:   Int = 0, // Scalar inputs
    sOut:  Int = 0, // Scalar outputs
    vIn:   Int = 0, // Vector inputs
    vOut:  Int = 0, // Vector outputs
    comp:  Int = 0  // Compute stages
  ) extends PartitionCost {
    def >(that: CUCost) = {
      this.sIn > that.sIn || this.vIn > that.vIn ||
      this.vOut > that.vOut || this.comp > that.comp
    }

    //def toUtil = Utilization(alus = comp, sclIn = sIn, sclOut = sOut, vecIn = vIn, vecOut = vOut)
    override def toString = s"  sIn: $sIn, sOut: $sOut, vIn: $vIn, vOut: $vOut, comp: $comp"
  }

  /**
    * Calculate the total cost for a given partition
    */
  def getCost(p: Partition, prev: Seq[Partition], all: List[Stage], others: Iterable[CU], isUnit: Boolean) = p match {
    case pmu: MUPartition => getMUCost(pmu, prev, all, others)
    case pcu: CUPartition => getCUCost(pcu, prev, all, others, isUnit)
  }

  def getMUCost(p: MUPartition, prev: Seq[Partition], all: List[Stage], others: Iterable[CU]) = {
    // TODO
    MUCost()
  }

  def getCUCost(p: CUPartition, prev: Seq[Partition], all: List[Stage], others: Iterable[CU], isUnit: Boolean) = {
    dbg(s"\n\n")
    dbg(s"  Compute stages: ")
    p.cstages.foreach{stage => dbg(s"    $stage") }

    dbg(s"  CChains: ")
    p.cchains.foreach{cc => dbg(s"    $cc :: " + globalInputs(cc).mkString(", ")) }

    val local  = p.allStages
    val remote = all diff local

    val localIns: Set[LocalComponent] = local.flatMap(_.inputMems).toSet.filterNot(isControl)
    val localOuts: Set[LocalComponent] = local.flatMap(_.outputMems).toSet
    val remoteIns: Set[LocalComponent] = remote.flatMap(_.inputMems).toSet
    val remoteOuts: Set[LocalComponent] = remote.flatMap(_.outputMems).toSet

    // val localReads: Set[CUMemory] = localIns.collect{case MemLoadReg(sram) => sram}
    // val remoteReads: Set[CUMemory] = remoteIns.collect{case MemLoadReg(sram) => sram}
    // Inserted bypass reads
    // val localBypasses: Set[CUMemory] = localOuts.collect{case MemLoadReg(sram) => sram}
    // val remoteBypasses: Set[CUMemory] = remoteOuts.collect{case MemLoadReg(sram) => sram}

    // val localWrites: Set[CUMemory] = localOuts.collect{case FeedbackDataReg(sram) => sram}
    // val localAddrs: Set[CUMemory] = localOuts.collect{case FeedbackAddrReg(sram) => sram; case ReadAddrWire(sram) => sram}
    // val remoteAddrs = remoteOuts.collect{case reg:FeedbackAddrReg => reg; case reg:ReadAddrWire => reg}

    // --- CU inputs and outputs
    val cuInBuses = globalInputs(localIns) ++ globalInputs(p.cchains)
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

    /*// TODO: Have to be more careful not to double count here!
    // Can double count for bypasses and multiple outputs for single stage

    // 1. Inputs to account for remotely hosted, locally read SRAMs
    // EXCEPT ones which we've already inserted bypass vectors for (appear as live ins)
    // Needs bypass: NO
    val remoteHostLocalRead = localReads diff remoteBypasses
    if (!isUnit) {
      vIns += remoteHostLocalRead.size
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
    dbg(s"    Locally hosted, remotely addressed: " + remotelyAddressed.mkString(", "))*/


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
      //nSRAMs += liveIns.size // Retiming vectors
    }
    else {
      liveIns.foreach{in =>
        addIn(prev.indexWhere{part => localOutputs(part.allStages) contains in})
      }
    }

    dbg(s"  Live ins: " + liveIns.mkString(", "))

    // Live outputs to other partitions
    val liveOuts = remoteIns intersect localOuts
    if (!isUnit) vOuts += liveOuts.size
    else         sOuts += liveOuts.size

    dbg(s"  Live outs: " + liveOuts.mkString(", "))

    // Pack scalar inputs and outputs into vectors
    sIns.values.foreach{ins =>
      val grpSize = Math.ceil(ins.toDouble / SCALARS_PER_BUS).toInt
      vIns += grpSize
      //nSRAMs += grpSize // Retiming scalar bus
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
    val bypasses = p.cstages.map{
      case ReduceStage(op,init,in,acc) =>
        val bypassInCost  = if (localIns.contains(in.reg)) 0 else 1
        val bypassOutCost = if (remoteIns.contains(acc))   1 else 0
        bypassInCost + bypassOutCost
      case MapStage(PIRBypass,ins,outs) => 1
      case _ => 0
    }.sum

    // --- Compute
    val rawCompute = p.cstages.map{
      case MapStage(op,ins,outs) => if (op == PIRBypass) 0 else 1
      case ReduceStage(op,init,in,acc) => REDUCE_STAGES
    }.sum

    // Scalars
    val sclIns = sIns.values.sum + cuGrpsIn.args.size + cuGrpsIn.scalars.size

    val cost = CUCost(
      sIn  = sclIns,
      sOut = sOuts,
      vIn  = vIns,
      vOut = vOuts,
      comp = rawCompute + bypasses
    )

    dbg(s"  $cost")

    cost
  }



  /**
    * Utilization - gives statistics about various Plasticine resource utilization
    */
  case class Utilization(
    pcus:   Int = 0,    // Compute units
    pmus:   Int = 0,    // Memory units
    ucus:   Int = 0,    // Unit compute
    switch: Int = 0,    // Parent controllers in switches
    stages: Int = 0,    // Used stages (ignores parallelization)
    alus:   Int = 0,    // ALUs
    mems:   Int = 0,    // SRAMs
    sclIn:  Int = 0,    // Scalar Inputs
    sclOut: Int = 0,    // Scalar Outputs
    vecIn:  Int = 0,    // Vector Inputs
    vecOut: Int = 0     // Vector Outputs
  ) {
    def +(that: Utilization) = Utilization(
      pcus   = this.pcus + that.pcus,
      pmus   = this.pmus + that.pmus,
      ucus   = this.ucus + that.ucus,
      switch = this.switch + that.switch,
      alus   = this.alus + that.alus,
      mems   = this.mems + that.mems,
      sclIn  = this.sclIn + that.sclIn,
      sclOut = this.sclOut + that.sclOut,
      vecIn  = this.vecIn + that.vecIn,
      vecOut = this.vecOut + that.vecOut
    )

    override def toString = s"$pcus, $pmus, $ucus, $switch, $alus, $mems, $sclIn, $sclOut, $vecIn, $vecOut"

    def heading = "PCUs, PMUs, UCUs, Switch, ALUs, SRAMs, SclIn, SclOut, VecIn, VecOut"
  }

  def getUtil(cu: CU, others: Iterable[CU]): Utilization = cu.style match {
    case _:MemoryCU =>
      Utilization(
        pmus   = 1,
        mems   = 1,
        stages = nUsedStages(cu),
        alus   = nUsedALUs(cu),
        sclIn  = nScalarIn(cu),
        sclOut = nScalarOut(cu),
        vecIn  = nVectorIns(cu, others),
        vecOut = nVectorOuts(cu)
      )

    case _:FringeCU => Utilization()  // TODO

    case _ =>
      val nChildren = others.count{_.parent.contains(cu)}
      val isParent = nChildren > 0
      val isEmpty  = cu.allStages.isEmpty
      val parentIsStream = cu.allParents.exists(_.style == StreamCU)

      if (nChildren == 1 && !parentIsStream) Utilization() // Merged with child
      else if (isParent) Utilization(switch = 1)
      else if (isEmpty) Utilization()
      else Utilization(
        pcus   = 1,
        stages = nUsedStages(cu),
        alus   = nUsedALUs(cu),
        sclIn  = nScalarIn(cu),
        sclOut = nScalarOut(cu),
        vecIn  = nVectorIns(cu, others),
        vecOut = nVectorOuts(cu)
      )
  }

  def nUsedALUs(cu: CU): Int = {
    cu.allStages.map {
      case MapStage(op, _, _) if op != PIRBypass => cu.lanes
      case _:ReduceStage => cu.lanes // ALUs used by reduction tree
      case _ => 0
    }.sum
  }
  def nUsedStages(cu: CU): Int = {
    cu.allStages.map{
      case MapStage(op, _, _) if op != PIRBypass => 1
      case _: ReduceStage => REDUCE_STAGES
      case _ => 0
    }.sum
  }

  /*def nMems(cu: CU, others: Iterable[CU]): Int = {
    val sramWritePorts = cu.mems.flatMap(_.writePort)
    //TODO: readports?
    val nonRetimedGroups = groupBuses(globalInputs(cu) diff sramWritePorts)
    cu.mems.size + nMems(nonRetimedGroups, others)
  }*/
  /*def nMems(groups: BusGroups, others: Iterable[CU]): Int = {
    val scalarGrps = if (groups.scalars.nonEmpty) {
      val producers = groups.scalars.groupBy{bus => others.find{cu => scalarOutputs(cu) contains bus}}
      producers.values.map{ss => Math.ceil(ss.size.toDouble / SCALARS_PER_BUS).toInt }.sum
    }
    else 0

    scalarGrps
  }*/


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

  def reportUtil(stats: Utilization) {
    val Utilization(pcus, pmus, ucus, switch, stages, alus, mems, sclIn, sclOut, vecIn, vecOut) = stats
    dbg(s"  pcus: $pcus, pmus: $pmus, ucus: $ucus, switch: $switch, stages: $stages, alus: $alus,")
    dbg(s"  mems: $mems, sclIn: $sclIn, sclOut: $sclOut, vecIn: $vecIn, vecOut: $vecOut")
  }




}
