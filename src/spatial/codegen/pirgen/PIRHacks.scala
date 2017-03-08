package spatial.codegen.pirgen

import spatial.SpatialExp

import scala.collection.mutable

trait PIRHacks extends PIRTraversal {
  val IR: SpatialExp with PIRCommonExp
  import IR._

  override val name = "PIR Hacks"
  override val recurse = Always

  val mappingIn = mutable.HashMap[Expr, List[List[CU]]]()

  val mappingOut = mutable.HashMap[Expr, List[List[CU]]]()

  override def process[S:Staged](b: Block[S]) = {
    msg(s"Starting traversal PIR Hacks")
    for ((pipe, cus) <- mappingIn) {
      //mcHack(pipe, cus.flatten)
      mappingOut += pipe -> cus
    }
    //streamHack()
    //counterHack()
    b
  }

  override protected def postprocess[S:Staged](block: Block[S]): Block[S] = {
    dbgs(s"\n\n//----------- Finishing PIRHacks ------------- //")
    for (cu <- mappingOut.values.flatten.flatten) {
      dbgcu(cu)
    }
    block
  }

  def mcHack(pipe: Expr, cus: List[CU]) {
    def allCUs = mappingIn.values.flatten.flatten

    dbg(s"MC Hack")

    // Set all CUs which write to a memory controller to StreamCUs
    // Either set parent to a streamcontroller, or make one and redirect parent
    cus.foreach{cu =>
      dbg(s"${cu.name}: writtenMC${writtenMC(cu).mkString(",")}")

      // Set everything but first stages to streaming pipes
      //if (writesMC && cu.deps.nonEmpty) cu.style = StreamCU


      if (writtenMC(cu).nonEmpty) cu.parent match {
        case Some(parent: CU) =>
          val cusWithParent = allCUs.filter(_.parent == cu.parent).toSet
          if (parent.style != StreamCU) {
            dbg(s"  Set $parent.style from ${parent.style} to StreamCU")
            parent.style = StreamCU
          }
          cusWithParent.foreach { sib =>
            if (sib.style != StreamCU) {
              sib.style = StreamCU
              dbg(s"  Set $sib.style from ${sib.style} to StreamCU")
            }
          }
        case _ =>
      }
    }
  }

  def writesToMC(cu: CU, cus: List[CU]): Boolean = {
    val children = cus.filter(_.parent == Some(cu))

    (cu +: children).exists{child => globalOutputs(child) exists (_.isInstanceOf[PIRDRAMBus]) }
  }

  // Ensure that outer controllers have exactly one leaf
  def streamHack() {
    val cus = mappingOut.values.flatten.flatten.toList
    for (cu <- cus) {
      if (cu.allStages.isEmpty && !cu.isDummy) {
        val children = cus.filter(_.parent == Some(cu))
        val writesMC = writesToMC(cu, cus)

        val deps = children.flatMap(_.deps).toSet

        // Leaves - no CU is dependent on this child
        val leaves = children.filterNot(deps contains _)

        if (leaves.size > 1 && !writesMC) {
          val leaf = ComputeUnit(quote(cu.pipe)+"_leaf", cu.pipe, UnitCU)
          copyIterators(leaf, cu)
          leaf.parent = Some(cu)
          leaf.deps ++= leaves
          leaf.isDummy = true
          leaf.cchains += UnitCChain(quote(cu.pipe)+"_unitcc")
          mappingOut(cu.pipe) = mappingOut(cu.pipe) ++ List(List(leaf))
        }
        else {
          // If we have a child controller leaf which itself has leaves which write to DRAM data bus
          val leafWritesMC = leaves.exists{leaf =>
            val leafChildren = cus.filter(_.parent == Some(leaf))
            leafChildren.exists{child => globalOutputs(child) exists(_.isInstanceOf[PIRDRAMDataOut]) }
          }
          if (leafWritesMC) {
            // insert a dummy pipe after the writing leaf
            val newLeaf = ComputeUnit(quote(cu.pipe)+"_leafX", cu.pipe, UnitCU)
            copyIterators(newLeaf, cu)
            newLeaf.parent = Some(cu)
            newLeaf.deps ++= leaves
            newLeaf.cchains += UnitCChain(quote(cu.pipe)+"_unitcc")
            newLeaf.isDummy = true
            mappingOut(leaves.last.pipe) = mappingOut(leaves.last.pipe) ++ List(List(newLeaf))
          }
        }
      }
    }
  }

  // Change strides of last counter in inner, parallelized loops to LANES
  def counterHack() {
    val cus = mappingOut.values.flatten.flatten.toList
    for (cu <- cus) {
      if (!cu.isUnit && (cu.allStages.nonEmpty || cu.isDummy)) {
        cu.cchains.foreach{
          case CChainInstance(name, ctrs) =>
            val innerCtr = ctrs.last
            if (innerCtr.end != ConstReg("1i")) {
              assert(innerCtr.stride == ConstReg("1i"), s"${innerCtr.stride} should be ConstReg(1i)")
              innerCtr.stride = ConstReg(s"${LANES}i")
            }

          case _ => // Do nothing
        }
      }
      else if (cu.allStages.isEmpty && !cu.isDummy) {
        // Eliminate cchain copies in outer loops
        cu.cchains = cu.cchains.filter{
          case _:CChainInstance | _:UnitCChain => true
          case _ => false
        }
      }
    }
  }

  def writtenMC(cu: CU): Set[MemoryController] = globalOutputs(cu).collect{
    case PIRDRAMDataOut(mc) => mc
    case PIRDRAMAddress(mc) => mc
    case PIRDRAMLength(mc) => mc
    case PIRDRAMOffset(mc) => mc
  }


  def makeStreamController(pipe: Expr, parent: Option[ACU]): CU = {
    val cu = ComputeUnit(quote(pipe)+"_sc", pipe, StreamCU)
    cu.parent = parent
    cu.cchains += UnitCChain(quote(pipe)+"_unitcc")
    cu
  }
}
