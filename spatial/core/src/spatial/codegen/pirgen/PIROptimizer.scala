package spatial.codegen.pirgen

import argon.core._

import scala.collection.mutable

class PIROptimizer(implicit val codegen:PIRCodegen) extends PIRTraversal {
  override val name = "PIR Optimization"
  var IR = codegen.IR

  override def process[S:Type](b: Block[S]): Block[S] = {
    msg("Starting traversal PIR Optimizer")
    dbgs(s"globals:${quote(globals)}")
    for (cu <- cus) removeRouteThrus(cu) // Remove route through stages
    for (cu <- cus) removeUnusedCUComponents(cu)
    for (cu <- cus) removeDeadStages(cu)
    removeEmptyCUs(cus)
    removeUnusedGlobalBuses()
    for (cu <- cus) removeDeadStages(cu)
    removeEmptyCUs(cus)
    val block = super.process(b)
    dbgs(s"\n\n//----------- Finishing PIROptimizer ------------- //")
    cus.foreach(dbgcu)
    block
  }

  override def preprocess[S:Type](b: Block[S]): Block[S] = {
    super.preprocess(b)
  }

  override def postprocess[S:Type](b: Block[S]): Block[S] = {
    // Printed inside PIRCodegen
    dbgs(s"\n\n//----------- Finishing PIROptimizer ------------- //")
    dbgs(s"Mapping:")
    mappingOf.foreach { case (sym, cus) =>
      dbgs(s"${sym} -> [${cus.mkString(",")}]")
    }
    dbgs(s"globals:${quote(globals)}")
    super.postprocess(b)
  }

  def removeUnusedCUComponents(cu: CU) = {
    removeUnusedRegs(cu)
    removeUnusedCChainCopy(cu)
    removeUnusedMems(cu)
  }

  def removeUnusedRegs(cu: CU) = dbgblk(s"removeUnusedRegs(${cu.name})") {
    val stages = cu.allStages.toList
    val usedRegs = mutable.Set[LocalComponent]()
    val allRegs = mutable.Set[LocalComponent]()
    stages.reverseIterator.foreach { stage =>
      val usedOuts = stage.outs.filter {
        case out:ControlOut => true
        case out:ScalarOut => true
        case out:VectorOut => true
        case out => usedRegs.contains(out)
      }
      if (usedOuts.nonEmpty) {
        usedRegs ++= usedOuts
        usedRegs ++= stage.ins
      }
      dbgs(s"stage=$stage")
      dbgs(s"- usedOuts=$usedOuts")
      allRegs ++= stage.ins
      allRegs ++= stage.outs
    }

    val unusedRegs = allRegs -- usedRegs

    if (unusedRegs.nonEmpty) {
      dbgs(s"")
      dbgs(s"Removing unused registers from cu ${cu.name}")
      dbgs(s"unusedRegs=$unusedRegs")

      stages.foreach { stage =>
        stage.outs = stage.outs.filterNot{reg => unusedRegs contains reg} 
      }

      cu.regs --= unusedRegs

      dbgblk(s"Resulting Stages:") {
        stages.foreach { stage => dbgs(s"$stage") }
      }
    }
  }

  def removeUnusedCChainCopy(cu: CU) = dbgblk(s"removeUnusedCChainCopy(${cu.name})") {
    // Remove unused counterchain copies
    val usedCCs = collectInput[CUCChain](cu.allStages ++ cu.mems)
    dbgs(s"usedCCs=$usedCCs")
    val unusedCopies = cu.cchains.collect{case cc:CChainCopy if !usedCCs.contains(cc) => cc}

    if (unusedCopies.nonEmpty) {
      dbgs(s"Removing unused counterchain copies from $cu")
      unusedCopies.foreach{cc => dbgs(s"$cc")}
      cu.cchains --= unusedCopies
    }
  }

  def removeUnusedMems(cu: CU) = dbgblk(s"removeUnusedMems(${cu.name})") {
    val globalMems = cu.mems.filter { mem => collectOutput[GlobalBus](mem).nonEmpty }
    var refMems = collectInput[CUMemory](cu.allStages ++ cu.cchains ++ globalMems.map(visitIn)) ++ globalMems
    val unusedMems = if (cu.style.isInstanceOf[FringeCU]) Set[CUMemory]() else cu.mems.filterNot{ mem => refMems.contains(mem) }
    if (unusedMems.nonEmpty) {
      dbgs(s"Removing unused mems from $cu: ${unusedMems.mkString(",")}")
      cu.memMap.retain { case (e, m) => !unusedMems.contains(m) }
    }
  }


  def removeUnusedGlobalBuses() = dbgblk(s"removeUnusedGlobalBuses") {
    val buses = globals.collect{case bus:GlobalBus if isInterCU(bus) => bus}
    val inputs = cus.flatMap{cu => collectInput[GlobalBus](cu) }.toSet

    dbgl(s"Buses: ") {
      buses.foreach{bus => dbgs(s"$bus")}
    }

    dbgl(s"Used buses: ") {
      inputs.foreach{in => dbgs(s"$in")}
    }


    val unusedBuses = buses.filterNot(inputs contains _)

    def isUnusedReg(reg: LocalComponent) = reg match {
      case ControlOut(out) => unusedBuses contains out
      case ScalarOut(out) => unusedBuses contains out
      case VectorOut(out) => unusedBuses contains out
      case _ => false
    }

    dbgs(s"Removing unused global buses:\n  " + unusedBuses.mkString("\n  "))
    cus.foreach{cu =>
      val stages = cu.allStages.collect{case m:MapStage => m}
      stages.foreach{stage => stage.outs = stage.outs.filterNot(isUnusedReg) }
      cu.regs = cu.regs.filterNot(isUnusedReg)
    }
    globals --= unusedBuses
  }

  // Remove route-through stages from the IR after scheduling
  // Rationale (for post scheduling): The Spatial IR has a number of nodes which are
  // effectively no-ops in PIR, which makes detecting route through cases difficult.
  // Once scheduled, a typical route-through case just looks like a CU with a single stage
  // which takes a vecIn and bypasses to a vecOut, which is easier to pattern match on
  def removeRouteThrus(cu: CU) = if (cu.parent.isDefined) dbgblk(s"removeRouteThrus(${cu.name})"){
    cu.computeStages.foreach{stage => dbgs(s"$stage") }

    val bypassStages = cu.computeStages.flatMap{
      case bypass@MapStage (
        PIRBypass, 
        List(MemLoad(mem:CUMemory)), 
        List(VectorOut(out: VectorBus))
      ) if mem.writePort.size==1 & mem.tpe==VectorFIFOType =>
        val (in:GlobalBus, _, _) = mem.writePort.head
        if (isInterCU(out)) {
          dbgs(s"Found route-thru: $in -> $out")
          swapBus(cus, orig=out, swap=in)
          Some(bypass)
        }
        else None

      case bypass@MapStage(
        PIRBypass, 
        List(MemLoad(mem:CUMemory)), 
        List(ScalarOut(out: OutputArg))
      ) if mem.writePort.size==1 & (mem.tpe==ScalarFIFOType | mem.tpe==ScalarBufferType)=>
        val (in:GlobalBus, _, _) = mem.writePort.head
        dbgs(s"Found route-thru: $in -> $out")
        swapBus(cus, orig=in, swap=out)
        Some(bypass)

      case bypass@MapStage(
        PIRBypass, 
        List(MemLoad(mem:CUMemory)), 
        List(ScalarOut(out: ScalarBus))
      ) if mem.writePort.size==1 & (mem.tpe==ScalarFIFOType | mem.tpe==ScalarBufferType)=>
        val (in:GlobalBus, _, _) = mem.writePort.head
        if (isInterCU(out)) {
          dbgs(s"Found route-thru: $in -> $out")
          swapBus(cus, orig=out, swap=in)
          Some(bypass)
        } else None
      case _ => None
    }
    if (bypassStages.nonEmpty) {
      dbgblk(s"Removing route through stages: ") {
        bypassStages.foreach{stage => dbgs(s"$stage")}
      }
      bypassStages.foreach { stage => removeStage(cu, stage) }
    }
  }

  def removeDeadStages(cu: CU) = dbgblk(s"removeDeadStages(${cu.name})"){
    val deadStages = cu.computeStages.collect{case stage:MapStage if stage.outs.isEmpty => stage}
    if (deadStages.nonEmpty) {
      deadStages.foreach { stage => removeStage(cu, stage) }
    }
  }

  def removeStage(cu:CU, stage:Stage) = {
    dbgs(s"Remove Stage $stage from cu=${cu.name}")
    cu.computeStages -= stage
    cu.controlStages -= stage
  }

  def removeEmptyCUs(cus: List[CU]) = dbgblk(s"removeEmptyCUs") { cus.foreach { cu =>
    dbgblk(s"$cu") {
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

      val globalOutputs = collectOutput[GlobalBus](cu)
      dbgs(s"globalOutputs=${globalOutputs}")
      val noOutput = globalOutputs.isEmpty

      if (cu.computeStages.isEmpty && children.isEmpty && !isFringe 
          && !isCopied && noOutput && cu.switchTable.isEmpty) {
        dbgs(s"Removing empty CU $cu")
        mappingOf.transform{ case (pipe, cus) => cus.filterNot{ _ == cu} }.retain{ case (pipe, cus) => cus.nonEmpty }
      }
    }
  } }

}
