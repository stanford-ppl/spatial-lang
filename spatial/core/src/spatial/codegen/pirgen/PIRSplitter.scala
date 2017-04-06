package spatial.codegen.pirgen

import spatial.SpatialExp
import spatial.SpatialConfig

import scala.collection.mutable


trait PIRSplitter extends PIRSplitting with PIRRetiming {
  val IR: SpatialExp with PIRCommonExp
  import IR._

  override val name = "PIR Splitting"
  override val recurse = Always

  val mappingIn  = mutable.HashMap[Symbol, CU]()
  val mappingOut = mutable.HashMap[Symbol, List[CU]]()

  //TODO read this from some config file?
  lazy val ComputeMax = SplitCost(
    sIn=SpatialConfig.sIn,
    vIn=SpatialConfig.vIn,
    vOut=SpatialConfig.vOut,
    vLoc=1,
    comp=SpatialConfig.comp,
    write=SpatialConfig.readWrite,
    read=SpatialConfig.readWrite,
    mems=SpatialConfig.mems
  )
  STAGES = 10
  SCALARS_PER_BUS = SpatialConfig.sbus

  override def process[S:Type](b: Block[S]) = {
    super.run(b)
    try {
      val cuMapping = mappingIn.keys.map{k => mappingIn(k).asInstanceOf[ACU] -> mappingOut(k).head.asInstanceOf[ACU] }.toMap
      swapCUs(mappingOut.values.flatten, cuMapping)
    }
    catch {case e: SplitException =>
      sys.exit(-1)
    }
    b
  }

  override protected def visit(lhs: Sym[_], rhs: Op[_]) {
    if (isControlNode(lhs) && mappingIn.contains(lhs))
      mappingOut(lhs) = split(mappingIn(lhs))
  }

  def split(cu: CU): List[CU] = {
    if (cu.allStages.nonEmpty) {
      val others = mutable.ArrayBuffer[CU]()
      others ++= mappingOut.values.flatten

      val cus = splitCU(cu, ComputeMax, others)
      retime(cus, others)

      cus.foreach{cu =>
        val cost = getStats(cu, others)
        if (cost.mems > ComputeMax.mems)
          throw new Exception(s"${cu.srams} > ${ComputeMax.mems}, exceeded maximum SRAMs after retiming")

        others += cu
      }

      cus
    }
    else List(cu)
  }

}
