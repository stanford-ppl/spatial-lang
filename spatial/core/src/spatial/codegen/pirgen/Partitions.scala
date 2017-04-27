package spatial.codegen.pirgen

import spatial.analysis.SpatialTraversal

import scala.collection.mutable
import spatial.{SpatialConfig, SpatialExp}


trait Partitions extends SpatialTraversal { this: PIRTraversal =>
  val IR: SpatialExp with PIRCommonExp
  import IR._

  var STAGES: Int = SpatialConfig.stages                   // Number of compute stages per CU
  def LANES = SpatialConfig.lanes                          // Number of SIMD lanes per CU
  def REDUCE_STAGES = (Math.log(LANES)/Math.log(2)).toInt  // Number of stages required to reduce across all lanes
  var READ_WRITE = SpatialConfig.readWrite

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

  class MUPartition(write: Map[Seq[CUMemory],mutable.ArrayBuffer[Stage]], read: Map[Seq[CUMemory],mutable.ArrayBuffer[Stage]], cc: Option[CUCChain], edge: Boolean) extends Partition {
    var wstages = Map[Seq[CUMemory], mutable.ArrayBuffer[Stage]]()
    var rstages = Map[Seq[CUMemory], mutable.ArrayBuffer[Stage]]()
    val isEdge: Boolean = edge
    val ctrl: Option[CUCChain] = cc

    write.foreach{case (mems, stages) =>
      wstages += mems -> mutable.ArrayBuffer[Stage]()
      wstages(mems) ++= stages
    }
    read.foreach{case (mems, stages) =>
      rstages += mems -> mutable.ArrayBuffer[Stage]()
      rstages(mems) ++= stages
    }

    def nonEmpty = wstages.nonEmpty || rstages.nonEmpty

    def allStages: Iterable[Stage] = wstages.values.flatten ++ rstages.values.flatten
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
      this.sIn > that.sIn || this.sOut > that.sOut || this.vIn > that.vIn ||
      this.vOut > that.vOut || this.write > that.write || this.read > that.read
    }
    def +(that: MUCost) = MUCost(
      sIn   = this.sIn + that.sIn,
      sOut  = this.sOut + that.sOut,
      vIn   = this.vIn + that.vIn,
      vOut  = this.vOut + that.vOut,
      write = this.write + that.write,
      read  = this.read + that.read
    )
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
      this.sIn > that.sIn || this.sOut > that.sOut || this.vIn > that.vIn ||
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
    case pcu: CUPartition => getCUCost(pcu, prev, all, others, isUnit){p => p.cstages}
  }

  def getMUCost(p: MUPartition, prev: Seq[Partition], all: List[Stage], others: Iterable[CU]) = {
    val readCost = p.rstages.values.map{stages =>
      val cost = getCUCost(p, prev, all, others, isUnit=false){_ => stages}
      MUCost(sIn=cost.sIn,sOut=cost.sOut,vIn=cost.vIn,vOut=cost.vOut,read=cost.comp)
    }.fold(MUCost()){_+_}
    val writeCost = p.wstages.values.map{stages =>
      val cost = getCUCost(p, prev, all, others, isUnit=false){_ => stages}
      MUCost(sIn=cost.sIn,sOut=cost.sOut,vIn=cost.vIn,vOut=cost.vOut,write=cost.comp)
    }.fold(MUCost()){_+_}
    readCost + writeCost
  }

  def getCUCost[P<:Partition](p: P, prev: Seq[Partition], all: List[Stage], others: Iterable[CU], isUnit: Boolean)(getStages: P => Seq[Stage]) = {
    val local = getStages(p)

    dbg(s"\n\n")
    dbg(s"  Stages: ")
    local.foreach{stage => dbg(s"    $stage") }

    dbg(s"  CChains: ")
    p.cchains.foreach{cc => dbg(s"    $cc :: " + globalInputs(cc).mkString(", ")) }

    val remote = all diff local

    val localIns: Set[LocalComponent] = local.flatMap(_.inputMems).toSet.filterNot(isControl)
    val localOuts: Set[LocalComponent] = local.flatMap(_.outputMems).toSet
    val remoteIns: Set[LocalComponent] = remote.flatMap(_.inputMems).toSet
    val remoteOuts: Set[LocalComponent] = remote.flatMap(_.outputMems).toSet

    // --- CU inputs and outputs
    val cuInBuses = globalInputs(localIns) ++ globalInputs(p.cchains)
    val cuOutBuses = globalOutputs(localOuts)

    val cuGrpsIn = groupBuses(cuInBuses)
    val cuGrpsOut = groupBuses(cuOutBuses)

    var vIns: Int   = nVectorIns(cuGrpsIn, others)
    var vOuts: Int  = nVectorOuts(cuGrpsOut, countScalars = false)

    var sIns = Map[Int, Int]()
    def addIn(part: Int) {
      if (!sIns.contains(part)) sIns += part -> 1
      else sIns += part -> (sIns(part) + 1)
    }
    var sOuts: Int = localOuts.count{case ScalarOut(_) => true; case _ => false}

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

    // --- Bypass stages
    val bypasses = local.map{
      case ReduceStage(_,_,in,acc) =>
        val bypassInCost  = if (localIns.contains(in.reg)) 0 else 1
        val bypassOutCost = if (remoteIns.contains(acc))   1 else 0
        bypassInCost + bypassOutCost
      case MapStage(PIRBypass,_,_) => 1
      case _ => 0
    }.sum

    // --- Compute
    val rawCompute = local.map{
      case MapStage(op,_,_) => if (op == PIRBypass) 0 else 1
      case _:ReduceStage    => REDUCE_STAGES
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
    cu.allStages.map {
      case MapStage(op, _, _) if op != PIRBypass => 1
      case _: ReduceStage => REDUCE_STAGES
      case _ => 0
    }.sum
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
  def nVectorIns(groups: BusGroups, others: Iterable[CU]): Int = groups.vectors.size

  def nVectorOuts(cu: CU): Int = {
    val groups = groupBuses(globalOutputs(cu))
    nVectorOuts(groups)
  }
  def nVectorOuts(groups: BusGroups, countScalars: Boolean = true): Int = groups.vectors.size

  def reportUtil(stats: Utilization) {
    val Utilization(pcus, pmus, ucus, switch, stages, alus, mems, sclIn, sclOut, vecIn, vecOut) = stats
    dbg(s"  pcus: $pcus, pmus: $pmus, ucus: $ucus, switch: $switch, stages: $stages, alus: $alus,")
    dbg(s"  mems: $mems, sclIn: $sclIn, sclOut: $sclOut, vecIn: $vecIn, vecOut: $vecOut")
  }




}
