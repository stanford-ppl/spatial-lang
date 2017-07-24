package spatial.models

import argon.core._
import argon.nodes._
import forge._
import spatial.aliases._
import spatial.metadata._

abstract class AreaModel[Area:AreaMetric, Sum<:AreaSummary[Sum]] extends AreaMetricOps {
  protected val metric: AreaMetric[Area] = implicitly[AreaMetric[Area]]

  lazy val NoArea: Area = metric.zero
  def SRAMArea(n: Int): Area = metric.sramArea(n)
  def MuxArea(n: Int, bits: Int): Area = metric.muxArea(n, bits)

  val MAX_PORT_WIDTH: Int
  def bramWordDepth(width: Int): Int

  def init(): Unit

  @stateful protected def areaOfMemory(nbits: Int, dims: Seq[Int], instance: Memory): Area = {
    dbg(s"$instance")
    // Physical depth for given word size for horizontally concatenated RAMs
    val nElements = dims.product
    val wordDepth = bramWordDepth(nbits)

    // Number of horizontally concatenated RAMs required to implement given word
    val portWidth = if (nbits > MAX_PORT_WIDTH) Math.ceil( nbits / MAX_PORT_WIDTH ).toInt else 1

    val bufferDepth = instance.depth

    val controlResourcesPerBank = if (bufferDepth == 1) NoArea else MuxArea(bufferDepth, nbits)

    // TODO: This seems suspicious - check later
    instance match {
      case DiagonalMemory(strides, banks, depth, isAccum) =>
        val elementsPerBank = Math.ceil(nElements.toDouble/banks)
        val nRAMsPerBank = Math.ceil(elementsPerBank.toDouble/wordDepth).toInt * portWidth
        val memResourcesPerBank = SRAMArea(nRAMsPerBank)
        val resourcesPerBuffer = (memResourcesPerBank + controlResourcesPerBank).replicate(banks, isInner = false)

        dbg(s"Word width:       $nbits")
        dbg(s"# of banks:       $banks")
        dbg(s"# of buffers:     $bufferDepth")
        dbg(s"# of columns:     $portWidth")
        dbg(s"Words / Memory:   $wordDepth")
        dbg(s"Elements / Bank:  $elementsPerBank")
        dbg(s"Memories / Bank:  $nRAMsPerBank")
        dbg(s"Resources / Bank: $memResourcesPerBank")
        dbg(s"Buffer resources: $resourcesPerBuffer")

        resourcesPerBuffer.replicate(bufferDepth, isInner=false)

      case BankedMemory(banking,depth,isAccum) =>
        val banks = banking.map(_.banks)
        val nBanks = banks.product
        val elementsPerBank = dims.zip(banks).map{case (dim,bank) => Math.ceil(dim.toDouble/bank).toInt }.product
        val nRAMsPerBank = Math.ceil(elementsPerBank.toDouble/wordDepth).toInt * portWidth
        val memResourcesPerBank = SRAMArea(nRAMsPerBank)
        val resourcesPerBuffer = (memResourcesPerBank + controlResourcesPerBank).replicate(nBanks, isInner = false)

        dbg(s"Word width:       $nbits")
        dbg(s"# of banks:       $nBanks")
        dbg(s"# of buffers:     $bufferDepth")
        dbg(s"# of columns:     $portWidth")
        dbg(s"Words / Memory:   $wordDepth")
        dbg(s"Elements / Bank:  $elementsPerBank")
        dbg(s"Memories / Bank:  $nRAMsPerBank")
        dbg(s"Resources / Bank: $memResourcesPerBank")
        dbg(s"Buffer resources: $resourcesPerBuffer")

        resourcesPerBuffer.replicate(bufferDepth, isInner=false)
    }
  }

  @stateful protected def areaOfSRAM(nbits: Int, dims: Seq[Int], instances: Seq[Memory]): Area = {
    instances.map{instance => areaOfMemory(nbits, dims, instance) }.fold(NoArea){_+_}
  }

  @stateful def nDups(e: Exp[_]): Int = duplicatesOf(e).length
  @stateful def nStages(e: Exp[_]): Int = childrenOf((e,-1)).length

  @stateful def apply(e: Exp[_], inHwScope: Boolean, inReduce: Boolean): Area = getDef(e) match {
    case Some(d) => areaOf(e, d, inHwScope, inReduce)
    case None => NoArea
  }
  @stateful final def areaOf(e: Exp[_], d: Def, inHwScope: Boolean, inReduce: Boolean): Area = {
    if (!inHwScope) NoArea else if (inReduce) areaInReduce(e, d) else areaOfNode(e, d)
  }

  @stateful def areaInReduce(e: Exp[_], d: Def): Area = areaOfNode(e, d)
  @stateful def areaOfNode(e: Exp[_], d: Def): Area

  @stateful def areaOfDelayLine(length: Int, width: Int, par: Int): Area

  @stateful def summarize(area: Area): Sum
}
