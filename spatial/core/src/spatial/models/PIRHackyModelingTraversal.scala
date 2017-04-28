package spatial.models

import spatial.SpatialConfig
import spatial.analysis.ModelingTraversal

import scala.collection.mutable

trait PIRHackyModelingTraversal extends ModelingTraversal {
  import IR._

  private case class Partition(compute: Seq[Exp[_]]) {
    var stages: List[Exp[_]] = compute.toList
    var cchains: List[Exp[_]] = Nil

    def willProbablyFitMaybe(others: Seq[Partition], isUnit: Boolean, scope: Seq[Exp[_]]): Boolean = {
      val external = scope diff stages
      val externalInputs  = external.flatMap{case Def(d) => d.expInputs; case _ => Nil }
      val externalOutputs = external

      val inputs = stages.flatMap{case Def(d) => d.expInputs; case _ => Nil }
      val outputs = stages

      val outsideInputs  = inputs intersect externalOutputs // Things other partitions created that we need
      val outsideOutputs = externalInputs intersect outputs // Things we created that other partitions need

      val inputLimit  = if (isUnit) SpatialConfig.sIn_PCU  else SpatialConfig.vIn_PCU
      val outputLimit = if (isUnit) SpatialConfig.sOut_PCU else SpatialConfig.vOut_PCU

      outsideInputs.size <= inputLimit && outsideOutputs.size <= outputLimit
    }
    def nonEmpty = compute.nonEmpty
  }

  def pipeDelaysHack(b: Block[_], cchain: Exp[CounterChain]): Map[Exp[_],Long] = {
    val scope = getStages(b).filterNot(s => isGlobal(s)).filter{e => e.tp == VoidType || Bits.unapply(e.tp).isDefined }
    val paths  = mutable.HashMap[Exp[_],Long]()
    val par = parsOf(cchain).last

    var partitions = mutable.ArrayBuffer[Partition]()
    var current = Partition(Nil)
    var remote = Partition(scope)

    def willFit(p: Partition): Boolean = p.willProbablyFitMaybe(partitions, par == 1, scope)

    /*while(remote.nonEmpty) {

    }*/

    paths.toMap
  }

}
