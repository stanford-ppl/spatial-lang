package spatial.codegen.pirgen

import argon.core._

import scala.collection.mutable
import scala.util.control.NoStackTrace

trait PIRSplitting extends PIRTraversal {

  class SplitException(val msg: String) extends Exception("Unable to split!")

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
  def splitCU(cu: CU, archCU: CUCost, archMU: MUCost, others: Seq[CU]): List[CU] = cu.style match {
    case MemoryCU => splitPMU(cu, archMU, others)
    case _:FringeCU => List(cu)
    case _ if cu.computeStages.isEmpty => List(cu)
    case _ => splitPCU(cu, archCU, others)
  }


  // TODO: PMU splitting. For now just throws an exception if it doesn't fit the specified constraints
  def splitPMU(cu: CU, archMU: MUCost, others: Seq[CU]): List[CU] = {
    if (cu.lanes > spec.lanes) {
      var errReport = s"Failed splitting in PMU $cu"
      errReport += s"\nCU had ${cu.lanes} lanes, greater than allowed ${spec.lanes}"
      throw new SplitException(errReport) with NoStackTrace
    }

    val allStages = cu.allStages.toList
    val ctrl = cu.cchains.find{case _:UnitCChain | _:CChainInstance => true; case _ => false}

    val partitions = mutable.ArrayBuffer[Partition]()

    val current = new MUPartition(mutable.ArrayBuffer.empty, mutable.ArrayBuffer.empty, ctrl, false) //TODO

    val cost = getMUCost(current, partitions, allStages, others, cu)

    if (cost > archMU) {
      var errReport = s"Failed splitting in PMU $cu"
      errReport += s"\nRead Stages: "
      current.rstages.foreach{stage => errReport += s"\n  $stage" }
      errReport += s"\nWrite Stages: "
      current.wstages.foreach{stage => errReport += s"\n  $stage" }

      errReport += "\nCost for last split option: "
      errReport += s"\n$cost"
      throw new SplitException(errReport) with NoStackTrace
    }

    List(cu)
  }

  def splitPCU(cu: CU, arch: CUCost, others: Seq[CU]): List[CU] = dbgblk(s"splitCU($cu)"){
    dbgl(s"Compute: ") {
      cu.computeStages.foreach{stage => dbgs(s"$stage")}
    }
    val allStages = cu.allStages.toList
    val ctrl = cu.cchains.find{case _:UnitCChain | _:CChainInstance => true; case _ => false}

    val partitions = mutable.ArrayBuffer[CUPartition]()
    var current: CUPartition = Partition.emptyCU(ctrl, true)
    val remote: CUPartition = new CUPartition(cu.computeStages, ctrl, false)

    def getCost(p: CUPartition): CUCost = getCUCost(p, partitions, allStages, others, cu){p => p.cstages}

    while (remote.nonEmpty) {
      dbgs(s"Computing partition ${partitions.length}")

      // Bite off a chunk to maximize compute usage
      current addTail remote.popHead(arch.comp)
      var cost = getCost(current)

      while (!(cost > arch) && remote.nonEmpty) {
        //dbgs(s"Adding stages until exceeds cost")
        current addTail remote.popHead()
        cost = getCost(current)
      }

      while (cost > arch && current.nonEmpty) {
        //dbgs(s"Removing stage")
        //TODO: This splitting stratagy highly depends on the linear schedule of the stages.
        //It's possible that different valid linear schedule can give a much better splitting
        //result. 
        remote addHead current.popTail()
        cost = getCost(current)
      }

      if (current.cstages.isEmpty) {
        // Find first SRAM owner which can still be spliced
        var errReport = s"Failed splitting in CU $cu"
        errReport += s"\nCompute stages: "
        remote.cstages.foreach{stage => errReport += s"\n  $stage\n" }

        errReport += "Cost for last split option: "
        current addTail remote.popHead()
        current.cstages.foreach{stage => errReport += s"\n  $stage\n"}
        val cost = getCost(current)
        errReport += s"Arch: \n$arch\n"
        errReport += s"Cost: \n$cost\n"
        throw new SplitException(errReport) with NoStackTrace
      } else {
        dbgs(s"Partition ${partitions.length}")
        dbgs(getCost(current))
        dbgs(s"  Compute stages: ")
        current.cstages.foreach{stage => dbgs(s"  $stage") }

        partitions += current
        current = Partition.emptyCU(ctrl, false)
      }
    } // end while

    val parent = if (partitions.length > 1) {
      val exp = mappingOf(cu)
      val parent = ComputeUnit(cu.name, StreamCU)
      mappingOf(exp) = parent
      parent.parent = cu.parent
      parent.cchains ++= cu.cchains
      parent.memMap ++= cu.memMap.filter { case (e, m) => collectInput[CUMemory](cu.cchains).contains(m) } 
      Some(parent)
    }
    else None

    val cus = partitions.zipWithIndex.map{case (p,i) =>
      scheduleCUPartition(orig = cu, p, i, parent)
    }

    cus.zip(partitions).zipWithIndex.foreach{case ((cu,p), i) =>
      dbgs(s"Partition #$i: $cu")
      val cost = getCost(p)
      dbgs(s"Cost: ")
      dbgs(cost)
      dbgs(s"Util: ")
      reportUtil(getUtil(cu, cus.filterNot(_ == cu)++others))
      dbgl(s"Compute stages: ") {
        cu.computeStages.foreach{stage => dbgs(s"$stage") }
      }
    }

    parent.toList ++ cus.toList

  }


  def scheduleCUPartition(orig: CU, part: CUPartition, i: Int, parent: Option[CU]): CU = dbgblk(s"scheduleCUPartition(orig=$orig, part=$part, i=$i, parent=$parent)"){
    val isUnit = orig.lanes == 1

    val cu = ComputeUnit(orig.name+"_"+i, orig.style)
    dbgs(s"Creating $cu")
    mappingOf(mappingOf(orig)) = cu
    cu.parent = if (parent.isDefined) parent else orig.parent
    cu.innerPar = orig.innerPar
    cu.fringeGlobals ++= orig.fringeGlobals

    val local = part.cstages
    val remote = orig.allStages.toList diff part.allStages

    val localIns  = local.flatMap(_.ins).toSet
    val localOuts = local.flatMap(_.outs).toSet
    dbgs(s"localIns=${local.flatMap(_.ins).toSet}")

    val readMems = collectInput[CUMemory](localIns)
    orig.memMap.foreach{case (k,mem) => if (readMems contains mem) cu.memMap += k -> mem }

    val remoteIns = remote.flatMap(_.ins).toSet
    val remoteOuts = remote.flatMap(_.outs).toSet

    def globalBus(reg: LocalComponent, isScalar:Option[Boolean]): GlobalBus = {
      val bus = reg match {
        case ScalarIn(bus) => bus
        case VectorIn(bus) => bus
        case ScalarOut(bus) => bus
        case VectorOut(bus) => bus
        case ControlIn(bus) => bus
        case ControlOut(bus) => bus
        case MemLoad(mem) if List(SRAMType, VectorFIFOType).contains(mem.tpe) =>
          val bus = CUVector(mem.name+"_vdata", cu.innerPar)
          bus
        case MemLoad(mem) if List(ScalarFIFOType, ScalarBufferType).contains(mem.tpe) =>
          val bus = CUScalar(mem.name+"_sdata")
          bus
        case WriteAddrWire(mem) =>
          val bus = CUScalar(mem.name+"_waddr")
          bus
        case ReadAddrWire(mem) =>
          val bus = CUScalar(mem.name+"_raddr")
          bus
        case _                    =>
          val bus = if (isScalar.getOrElse(isUnit)) CUScalar("bus_" + reg.id)    
                    else CUVector("bus_" + reg.id, cu.innerPar)
          bus
      }
      dbgs(s"globalBus: reg=$reg ${scala.runtime.ScalaRunTime._toString(reg.asInstanceOf[Product])}, bus=$bus") 
      bus
    }

    def portOut(reg: LocalComponent, isScalar:Option[Boolean] = None) = globalBus(reg, isScalar) match {
      case bus:ControlBus => ControlOut(bus)
      case bus:ScalarBus => ScalarOut(bus)
      case bus:VectorBus => VectorOut(bus)
    }

    def portIn(reg: LocalComponent, isScalar:Option[Boolean] = None) = {
      val bus = globalBus(reg, isScalar)
      val fifo = allocateRetimingFIFO(reg, bus, cu)
      MemLoad(fifo)
    }

    def rerefIn(reg: LocalComponent): LocalComponent = {
      val in = reg match {
        case _:ConstReg[_] | _:CounterReg => reg
        case _:ValidReg | _:ControlReg => reg
        case _:ReduceMem[_]   => if (localOuts.contains(reg)) reg else portIn(reg)
        case MemLoad(mem) => 
          assert(localIns.contains(reg), s"localIns=$localIns doesn't contains $reg")
          assert(cu.mems.contains(mem), s"cu.mems=${cu.mems} cu=${cu.name} doesn't contains $mem")
          reg
        case _ if !remoteOuts.contains(reg) | cu.regs.contains(reg) => reg 
        case _ if remoteOuts.contains(reg) & !cu.regs.contains(reg) => portIn(reg)
      }
      cu.regs += in
      in
    }

    def rerefOut(reg: LocalComponent): List[LocalComponent] = {
      val outs = reg match {
        case _:ControlOut => List(reg)
        case _:ScalarOut => List(reg)
        case _:VectorOut => List(reg)
        case _ =>
          val local  = if (localIns.contains(reg)) List(reg) else Nil
          val global = if (remoteIns.contains(reg)) List(portOut(reg)) else Nil
          local ++ global
      }
      cu.regs ++= outs
      outs
    }

    // --- Reschedule compute stages
    part.cstages.foreach{
      case MapStage(op, ins, outs) =>
        val inputs = ins.map{in => rerefIn(in) }
        val outputs = outs.flatMap{out => rerefOut(out) }
        addComputeStage(cu, MapStage(op, inputs, outputs))

      case stage@ReduceStage(op, inputs, outputs) =>
        var accum = stage.accum
        var in = stage.in 
        in = rerefIn(in)
        if (!in.isInstanceOf[ReduceMem[_]]) {
          val redReg = ReduceReg()
          cu.regs += redReg
          bypass(cu, in, redReg)
          in = redReg
        }
        accum = rerefIn(accum).asInstanceOf[AccumReg]
        val newAccumParent = 
          if (accum.parent==orig) parent.getOrElse(cu) // Take StreamController of the splitted cu
                                                      // Or current CU if single partition
          else accum.parent // outer controller. Shouldn't be splitted 
        dbgs(s"accum.parent==orig ${accum.parent==orig}")
        dbgs(s"accum.parent=${accum.parent} orig=${orig} parent=${parent.map(_.name)} cu=${cu.name}")
        dbgs(s"newAccumParent=${newAccumParent.name}")
        accum.parent = newAccumParent
        addComputeStage(cu, ReduceStage(op, List(in, accum), outputs))

        if (remoteIns.contains(accum)) {
          val bus = portOut(accum, Some(true))
          addComputeStage(cu, MapStage(PIRBypass, List(accum), List(bus)))
        }
    }

    // --- Copy counters
    parent.fold { // No split
      cu.cchains ++= orig.cchains
    } { parent =>  // split
      val unitCtr = CUCounter(ConstReg(0), ConstReg(1), ConstReg(cu.innerPar), par=cu.innerPar)
      cu.cchains += CChainInstance(s"${cu.name}_unit", Seq(unitCtr))
      val f = copyIterators(cu, parent)

      def tx(cc: CUCChain): CUCChain = f.getOrElse(cc, cc)
      def swap(x: LocalComponent) = x match {
        case CounterReg(cc,cIdx,iter) => CounterReg(tx(cc), cIdx, iter)
        case ValidReg(cc,cIdx, valid) => ValidReg(tx(cc), cIdx, valid)
        case _ => x
      }

      cu.allStages.foreach{
        case stage@MapStage(_,ins,_) => stage.ins = ins.map{in => swap(in) }
        case _ =>
      }

      val readMems = collectInput[CUMemory](cu.cchains)
      dbgs(s"cu.cchains=${cu.cchains}")
      dbgs(s"readMems of cchains = ${readMems}")
      orig.memMap.foreach{case (k,mem) => if (readMems contains mem) cu.memMap += k -> mem }
      cu.mems.foreach{sram =>
        sram.readPort.transform{ case (data, addr, top) => (data, addr.map(swap), top) }
        sram.writePort.transform{ case (data, addr, top) => (data, addr.map(swap), top) }
      }
    }

    cu
  }

}
