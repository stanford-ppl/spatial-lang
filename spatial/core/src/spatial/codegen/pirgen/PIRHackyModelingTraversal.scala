package spatial.codegen.pirgen

import argon.core._
import argon.nodes._
import spatial.analysis.ModelingTraversal
import spatial.aliases._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._
import org.virtualized.SourceContext

import scala.collection.mutable

trait PIRHackyModelingTraversal extends ModelingTraversal { trv =>
  def spec = spatialConfig.plasticineSpec
  override val latencyModel = new PlasticineLatencyModel{}

  private case class Partition(compute: Seq[Exp[_]]) {
    var stages: List[Exp[_]] = compute.toList
    var cchains: List[Exp[_]] = Nil

    def cycles: Double = stages.map(stage => latencyOf(stage)).sum

    def inputs = {
      stages.flatMap{case Def(d) => d.expInputs; case _ => Nil}.distinct diff stages
    }
    def defines(x: Exp[_]) = stages.contains(x)

    def cost(others: Seq[Partition], isUnit: Boolean, scope: Seq[Exp[_]]): (Seq[Exp[_]],Seq[Exp[_]],Double) = {
      val external = scope diff stages
      val externalInputs  = external.flatMap{case Def(d) => d.expInputs; case _ => Nil }
      val externalOutputs = external

      val inputs = stages.flatMap{case Def(d) => d.expInputs; case _ => Nil }
      val outputs = stages

      val outsideInputs  = inputs intersect externalOutputs // Things other partitions created that we need
      val outsideOutputs = externalInputs intersect outputs // Things we created that other partitions need

      val nCycles = stages.map(stage => latencyOf(stage)).sum

      (outsideInputs, outsideOutputs, nCycles)
    }

    def willProbablyFitMaybe(others: Seq[Partition], isUnit: Boolean, scope: Seq[Exp[_]]): Boolean = {
      val (outsideInputs, outsideOutputs, nCycles) = cost(others, isUnit, scope)

      val inputLimit  = if (isUnit) spec.pcuSin  else spec.pcuVin
      val outputLimit = if (isUnit) spec.pcuSout else spec.pcuVout

      outsideInputs.size <= inputLimit && outsideOutputs.size <= outputLimit && nCycles < spec.pcuStages
    }
    def nonEmpty = stages.nonEmpty

    def addTail(tail: List[Exp[_]]): Unit = stages ++= tail
    def addTail(tail: Exp[_]): Unit = stages = stages :+ tail
    def addHead(head: List[Exp[_]]): Unit = stages = head ++ stages
    def addHead(head: Exp[_]): Unit = stages = head +: stages

    def popHead(size: Int = 1): List[Exp[_]] = {
      val drop = stages.take(size)
      stages = stages.drop(size)
      drop
    }
    def popTail(size: Int = 1): List[Exp[_]] = {
      val drop = stages.takeRight(size)
      stages = stages.dropRight(size)
      drop
    }
  }

  def pipeDelaysHack(b: Block[_], cchain: Option[Exp[CounterChain]]) = {
    val scope = getStages(b).filterNot(s => isGlobal(s)).filter{e => e.tp == UnitType || Bits.unapply(e.tp).isDefined }
    val paths  = mutable.HashMap[Exp[_],Long]()
    val delays = mutable.HashMap[Exp[_],Long]()
    val par = cchain.map{cc => parsOf(cc).last}.getOrElse(1)

    var partitions = mutable.ArrayBuffer[Partition]()
    var current = Partition(Nil)
    var remote = Partition(scope)

    def willFit(p: Partition): Boolean = p.willProbablyFitMaybe(partitions, par == 1, scope)
    def cost(p: Partition) = p.cost(partitions, par == 1, scope)

    while (remote.nonEmpty) {
      current addTail remote.popHead(spec.pcuStages)

      while (willFit(current) && remote.nonEmpty) {
        current addTail remote.popHead()
      }
      while (!willFit(current) && current.nonEmpty) {
        remote addHead current.popTail()
      }
      if (current.stages.isEmpty) {
        error("Failed hacky splitting")
        remote.stages.foreach{stage => error(s"  ${str(stage)}") }
        current addTail remote.popHead()
        error("Last cost: ")
        error("  Inputs: ")
        val (ins, outs, cycles) = cost(current)
        ins.foreach{in => error(s"    ${str(in)}")}
        error("  Outputs: ")
        outs.foreach{out => error(s"    ${str(out)}")}
        error("  Cycles: " + cycles)
        sys.exit(-1)
      }
      else {
        partitions += current
        current = Partition(Nil)
      }
    }

    partitions.zipWithIndex.foreach{case (p,i) =>
      dbgs(s"Parition #$i")
      dbgs("  Stages: ")
      p.stages.foreach{stage =>
        dbgs(s"    ${str(stage)}")
      }
      dbgs("  Inputs: ")
      p.inputs.foreach{in =>
        dbgs(s"    ${str(in)}")
      }
    }

    val layer = mutable.HashMap[Partition, Int]()

    // Order CUs using BFS
    def bfs(x: Partition): Int = layer.getOrElseAdd(x, {
      dbgs(c"Getting layer of partition #${partitions.indexOf(x)}")
      val ins = x.inputs.flatMap{in => partitions.find(_.defines(in)) }.distinct
      dbgs(c"  inputs: " + ins.map(x => partitions.indexOf(x)).mkString(", "))
      (-1 +: ins.map(bfs)).max + 1
    })
    val layers = partitions.map(bfs)
    val maxLayer = (0 +: layers).max

    partitions.zip(layers).foreach{case (p,l) =>
      val offset = (10 * l).toLong
      val nStages = p.stages.size
      var i = 0
      val lastIndex = p.stages.lastIndexWhere{stage => Bits.unapply(stage.tp).isDefined }
      val lastOption = if (lastIndex >= 0) Some(p.stages(lastIndex)) else None
      p.stages.foreach{stage =>
        // Last is always 10
        paths(stage) = offset + i
        if (lastOption.contains(stage)) {
          delays(stage) = spec.pcuStages - 1 - i
          i = spec.pcuStages - 1
        }
        else if (latencyOf(stage) > 0) delays(stage) = 1
        else delays(stage) = 0
      }
    }

    scope.foreach{stage =>
      dbgs(s"${str(stage)} [${paths.getOrElse(stage,0L)}]")
    }

    (paths.toMap, delays.toMap)
  }

}
