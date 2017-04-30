package spatial.codegen.pirgen

import spatial.{SpatialConfig, SpatialExp}

import scala.collection.mutable._

trait PIRRetiming extends PIRTraversal {
  val IR: SpatialExp with PIRCommonExp
  import IR._

  /**
   * For all vector inputs for each CU, if inputs have mismatched delays or come
   * from other stages (and LCA is not a stream controller), put them through a retiming FIFO
   **/
  def retime(cus: List[CU], others: Iterable[CU]): Unit = {
    var producer = Map[GlobalBus, CU]()
    var deps     = Map[CU, List[GlobalBus]]()

    val compute = cus.filter(_.allStages.nonEmpty)

    compute.foreach{cu =>
      globalOutputs(cu).foreach{bus => producer += bus -> cu}

      // Ignore inputs for cchains, srams here - SRAM vectors are already retimed, others are scalars which are retimed differently
      val ins = globalInputs(cu.allStages)
      deps += cu -> ins.toList
    }

    //       |
    //       A
    //       |
    //       B <-- F
    //      / \    |
    //     C   D - E

    /*def getDelay(input: GlobalBus, cur: Int, visit: Set[CU]): Int = producer.get(input) match {
      case Some(cu) if visit.contains(cu) =>
        dbg(s"    [CYCLE]")
        -1  // Don't retime cycles
      case Some(cu) if deps(cu).isEmpty =>
        dbg(s"    ${cu.name}")
        cur+1
      case Some(cu) =>
        dbg(s"    ${cu.name} -> " + deps(cu).mkString(", "))
        val delays = deps(cu).map{dep => getDelay(dep,cur+1,visit+cu)}
        if (delays.contains(-1)) -1 else delays.max
      case None => cur
    }*/

    compute.foreach{cu => if (deps(cu).nonEmpty) {
      dbg(s"Retiming inputs to CU ${cu.name}: ")

      val vecIns = deps(cu).iterator.collect{case bus: VectorBus => bus}

      vecIns.foreach{dep =>
        if (producer.contains(dep) || isInterCU(dep)) { // Inputs from DRAM should already have FIFOs, input args don't need them
          // Size of FIFO can't be statically predicted here (routing costs) and doesn't matter to config anyway
          insertFIFO(cu, dep, 4096)
        }
        /*else if (isInterCU(dep)) {
          insertFIFO(cu, dep, 4096)
        }
          val produce = others.find{cu => globalOutputs(cu) contains dep }
          if (produce.isDefined) {
            lca(cu, produce.get) match {
              case Some(parent) if parent.style == StreamCU => // No retiming within stream controllers?
              case None => // What?
              case _ => insertFIFO(cu, dep, 4096)
            }
          }
        }*/
      }

      cu.deps ++= deps(cu).flatMap{dep => producer.get(dep) } // Only use dependencies within this virtual CU
    }}






    /*    val produce = producer.getOrElse(dep, )
        if (produce.isDefined) {
          lca(cu, produce.get) match {
            case Some(parent) if parent.style == StreamCU => // No action
            case None => // ???
            case _ => insertFIFO(cu, dep, 4096)
          }
          insertFIFO(cu, dep, 4096 ) // size isn't used anyway

      }

      val delays = deps(cu).map{dep =>
        dbg(s"  $dep")
        getDelay(dep, 0, Set(cu))
      }
      val criticalPath = delays.max



      deps(cu).zip(delays).foreach{case (dep,dly) =>
        if (dly <= criticalPath && dly > 0) {
          insertFIFO(cu, dep, (criticalPath - dly + 1)*STAGES)
        }
        else if (dly == 0) {
          val produce = others.find{cu => globalOutputs(cu) contains dep}
          if (produce.isDefined) {

          }
        }
      }*/


    //}}
  }

  def lca(a: CU, b: CU):CU = {
    val (lca, path1, path2) = leastCommonAncestorWithPaths(a,b,(cu:CU) => cu.parentCU)
    lca.get
    //var ap:Option[CU] = Some(a)
    //var bp:Option[CU] = Some(b)
    //val aps = ListBuffer[CU]()
    //val bps = ListBuffer[CU]()
    //while (!ap.isEmpty && !bp.isEmpty) {
      //val ac = ap.get
      //val bc = bp.get
      //aps += ac
      //bps += bc
      //if (aps.contains(bc)) return bc
      //if (bps.contains(ac)) return ac
      //ap = ac.parentCU
      //bp = bc.parentCU
    //}
    //throw new Exception(s"Could not find common ancesstor between $a and $b")
  }

  def insertFIFO(cu: CU, bus: GlobalBus, depth: Int) {
    dbg(s"Inserting FIFO in $cu for input $bus")
    val sram = allocateFIFO(bus, depth, cu)
    cu.memMap += sram.mem -> sram
    cu.allStages.foreach{
      case stage@MapStage(op, ins, outs) =>
        stage.ins = ins.map{
          case LocalRef(_,ScalarIn(`bus`)) => LocalRef(-1, MemLoadReg(sram))
          case LocalRef(_,VectorIn(`bus`)) => LocalRef(-1, MemLoadReg(sram))
          case ref => ref
        }
      case _ =>
    }
  }

  def allocateFIFO(bus: GlobalBus, depth: Int, cu:CU) = {
    val name = bus match {
      case bus:ScalarBus => bus.name+"_fifo"
      case bus:VectorBus => bus.name+"_fifo"
    }
    val memSym = if (SpatialConfig.DSEMode) null else fresh[Int32]
    val memAccess = if (SpatialConfig.DSEMode) null else fresh[Int32]
    val sram = CUMemory(name, memSym, memAccess, cu)
    sram.mode = bus match {
      case bus:ScalarBus => ScalarFIFOMode
      case bus:VectorBus => VectorFIFOMode
    }
    sram.size = depth
    sram.writePort = Some(bus) //TODO: readport?
    sram.banking = Some(Strided(1))
    sram
  }

}
