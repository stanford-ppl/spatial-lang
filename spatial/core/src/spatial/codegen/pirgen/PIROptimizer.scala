package spatial.codegen.pirgen

import argon.core._

import scala.collection.mutable

trait PIROptimizer extends PIRTraversal {
  override val name = "PIR Optimization"

  val mapping = mutable.HashMap[Expr, List[CU]]()

  def cus = mapping.values.flatMap{cus => cus}.toList

  override def process[S:Type](b: Block[S]): Block[S] = {
    msg("Starting traversal PIR Optimizer")
    for (cu <- cus) removeRouteThrus(cu) // Remove route through stages
    for (cu <- cus) removeUnusedCUComponents(cu)
    for (cu <- cus) removeDeadStages(cu)
    removeEmptyCUs(cus)
    removeUnusedGlobalBuses()
    for (cu <- cus) removeDeadStages(cu)
    removeEmptyCUs(cus)
    super.process(b)
  }

  override def postprocess[S:Type](b: Block[S]): Block[S] = {
    dbgs(s"\n\n//----------- Finishing PIROptimizer ------------- //")
    dbgs(s"Mapping:")
    mapping.foreach { case (sym, cus) =>
      dbgs(s"${sym} -> [${cus.mkString(",")}]")
    }
    for (cu <- mapping.values.flatten) {
      dbgcu(cu)
    }
    super.postprocess(b)
  }

  def removeUnusedCUComponents(cu: CU) = {
    removeUnusedStages(cu)
    removeUnusedCChainCopy(cu)
    removeUnusedFIFO(cu)
  }

  def removeUnusedStages(cu: CU) = dbgl(s"Checking CU $cu for unused stages...") {
    val stages = cu.allStages.toList
    // Remove all unused temporary registers
    val ins  = stages.flatMap{stage => stage.inputMems.filter{t => isReadable(t) && isWritable(t) }}.toSet
    val outs = stages.flatMap{stage => stage.outputMems.filter{t => isReadable(t) && isWritable(t) }}.toSet
    val unusedRegs = outs diff ins
    stages.foreach { stage =>
      dbgblk(s"$stage") {
        stage.inputMems.foreach { in => dbgs(s"in=$in isReadable=${isReadable(in)} isWritable=${isWritable(in)}") }
        stage.outputMems.foreach { out => dbgs(s"out=$out isReadable=${isReadable(out)} isWritable=${isWritable(out)}") }
      }
    }

    if (unusedRegs.nonEmpty) {
      dbgs(s"Removing unused registers from $cu: [${unusedRegs.mkString(",")}]")

      stages.foreach{ 
        case stage:MapStage => stage.outs = stage.outs.filterNot{ref => unusedRegs contains ref.reg} 
        case stage:ReduceStage => // Cannot remove register in reduce stage
      }
      cu.regs --= unusedRegs
    }
    stages.foreach { stage =>
      if (stage.outputMems.isEmpty) {
        dbgs(s"Removing stage with no output from $cu: $stage")
        cu.writeStages.foreach { case (mems, stages) => stages -= stage }
        cu.readStages.foreach { case (mems, stages) => stages -= stage }
        cu.computeStages -= stage
        cu.controlStages -= stage
      }
    }
  }

  def removeUnusedCChainCopy(cu: CU) = dbgl(s"Checking CU $cu for unused CChainCopy...") {
    // Remove unused counterchain copies
    val usedCCs = usedCChains(cu)
    val unusedCopies = cu.cchains.collect{case cc:CChainCopy if !usedCCs.contains(cc) => cc}

    if (unusedCopies.nonEmpty) {
      dbgs(s"Removing unused counterchain copies from $cu")
      unusedCopies.foreach{cc => dbgs(s"$cc")}
      cu.cchains --= unusedCopies
    }
  }

  def removeUnusedFIFO(cu: CU) = dbgl(s"Checking CU $cu for unused FIFO...") {
    var refMems = usedMem(cu) 
    val unusedFifos = cu.fifos.filterNot{ fifo => refMems.contains(fifo) }
    if (unusedFifos.nonEmpty) {
      dbgs(s"Removing unused fifos from $cu")
      unusedFifos.foreach{ fifo => dbgs(s"$fifo")}
      cu.memMap.retain { case (e, m) => !unusedFifos.contains(m) }
    }
  }


  def removeUnusedGlobalBuses() = dbgl(s"Checking Unused GlobalBuses ...") {
    val buses = globals.collect{case bus:GlobalBus if isInterCU(bus) => bus}
    val inputs = cus.flatMap{cu => globalInputs(cu) }.toSet

    dbgs(s"Buses: ")
    buses.foreach{bus => dbgs(s"  $bus")}

    dbgs(s"Used buses: ")
    inputs.foreach{in => dbgs(s"  $in")}


    val unusedBuses = buses filterNot(inputs contains _)

    def isUnusedReg(reg: LocalComponent) = reg match {
      case ScalarOut(out) => unusedBuses contains out
      case VectorOut(out) => unusedBuses contains out
      case _ => false
    }
    def isUnusedRef(ref: LocalRef) = isUnusedReg(ref.reg)

    dbgs(s"Removing unused global buses:\n  " + unusedBuses.mkString("\n  "))
    cus.foreach{cu =>
      val stages = cu.allStages.collect{case m:MapStage => m}
      stages.foreach{stage => stage.outs = stage.outs.filterNot(isUnusedRef) }
      cu.regs = cu.regs.filterNot(isUnusedReg)
    }
    globals --= unusedBuses
  }

  // Remove route-through stages from the IR after scheduling
  // Rationale (for post scheduling): The Spatial IR has a number of nodes which are
  // effectively no-ops in PIR, which makes detecting route through cases difficult.
  // Once scheduled, a typical route-through case just looks like a CU with a single stage
  // which takes a vecIn and bypasses to a vecOut, which is easier to pattern match on
  def removeRouteThrus(cu: CU) = if (cu.parent.isDefined) dbgblk(s"Checking $cu for route through stages: "){
    cu.computeStages.foreach{stage => dbgs(s"  $stage") }

    val bypassStages = cu.computeStages.flatMap{
      case bypass@MapStage(PIRBypass, List(LocalRef(_,MemLoadReg(mem:CUMemory))), List(LocalRef(_,VectorOut(out: VectorBus)))) if mem.writePort.size==1 & mem.mode==VectorFIFOMode =>
        val in = mem.writePort.head
        if (isInterCU(out)) {
          dbgs(s"Found route-thru: $in -> $out")
          swapBus(cus, out, in)
          Some(bypass)
        }
        else None

      case bypass@MapStage(PIRBypass, List(LocalRef(_,MemLoadReg(mem:CUMemory))), List(LocalRef(_,ScalarOut(out: ScalarBus)))) if mem.writePort.size==1 & (mem.mode==ScalarFIFOMode | mem.mode==ScalarBufferMode)=>
        val in = mem.writePort.head
        if (isInterCU(out)) {
          dbgs(s"Found route-thru: $in -> $out")
          swapBus(cus, out, in)
          Some(bypass)
        }
        else None

      case bypass@MapStage(PIRBypass, List(LocalRef(_,MemLoadReg(mem:CUMemory))), List(LocalRef(_,ScalarOut(out: OutputArg)))) if mem.writePort.size==1 & (mem.mode==ScalarFIFOMode | mem.mode==ScalarBufferMode)=>
        val in = mem.writePort.head
        if (isInterCU(in)) {
          dbgs(s"Found route-thru: $in -> $out")
          swapBus(cus, in, out)
          Some(bypass)
        }
        else None

      case _ => None
    }
    if (bypassStages.nonEmpty) {
      dbgl(s"Removing route through stages: ") {
        bypassStages.foreach{stage => dbgs(s"$stage")}
      }
      removeComputeStages(cu, bypassStages.toSet)
    }
  }

  // TODO: This could be iterative with removing unused outputs
  // Right now only does one layer
  def removeDeadStages(cu: CU) {
    val deadStages = cu.computeStages.collect{case stage:MapStage if stage.outs.isEmpty => stage}
    if (deadStages.nonEmpty) {
      dbgl(s"Removing dead stages from $cu:") {
        deadStages.foreach{stage => dbgs(s"$stage") }
      }
      removeComputeStages(cu, deadStages.toSet)
    }
  }


  def removeEmptyCUs(cus: List[CU]) = cus.foreach {cu =>
    // 1. This CU has no children, no write stages, and no compute stages
    // 2. This CU has a sibling (same parent) CU or no counterchain instances
    // 3. This is not a FingeCU
    // 4. No other CU is making copy of current CU's cchain
    val children = cus.filter{c => c.parent.contains(cu) }
    val isFringe = cu.style.isInstanceOf[FringeCU]

    val isCopied = cus.exists { other => 
      other.cchains.exists { 
        case copy@CChainCopy(_, inst, owner) if owner == cu => true
        case _ => false
      } 
    }

    if (cu.writeStages.isEmpty && cu.readStages.isEmpty && cu.computeStages.isEmpty && children.isEmpty && !isFringe && !isCopied) {
      cus.foreach{ c =>
        if (c.deps.contains(cu)) {
          c.deps -= cu
          c.deps ++= cu.deps
        }
      }
      dbgs(s"Removing empty CU $cu")
      mapping.transform{ case (pipe, cus) => cus.filterNot{ _ == cu} }.retain{ case (pipe, cus) => cus.nonEmpty }
    }
  }

}
