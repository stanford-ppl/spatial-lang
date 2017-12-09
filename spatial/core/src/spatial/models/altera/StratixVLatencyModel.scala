package spatial.models
package altera

import argon.core._
import argon.nodes._
import forge._
import spatial.aliases._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._

class StratixVLatencyModel extends LatencyModel {
  import spatial.targets.AlteraDevice._
  val FILE_NAME = "StratixVLatency.csv"
  lazy val memModel = new TileLoadModel

  @stateful override def init(): Unit = {
    super.init()
    memModel.init()
  }

  @stateful def memoryModel(c: Int, r: Int, b: Int, p: Int): Long = {
    val cols = if (b < 96) 96 else b
    val overhead12 = cols match {
      case 96  => 0.307/(1 + Math.exp(-0.096*r + 0.21))   // Logistic, bounded by 30.7%
      case 192 => 0.185/(1 + Math.exp(-0.24*r - 0.8))     // Logistic, bounded by 18.5%
      case _ => 0.165
    }
    val overhead = ((1/Math.log(12))*Math.log(c))*overhead12
    val base = Math.ceil( (1+overhead)*(110 + r*(53 + cols)) )

    val parSpeedup = memModel.evaluate(c, r, cols, p)

    //System.out.println(s"Base: $base, par: $parSpeedup")

    (parSpeedup*base).toLong
  }



  @stateful override protected def latencyOfNode(s: Exp[_], d: Def): Double = d match {
    case op: DenseTransfer[_,_] if op.isStore =>
      val c = contentionOf(s)
      val p = boundOf(op.p).toInt

      val dims = op.lens.map{x => boundOf(x).toInt }
      val size = dims.last
      val iters = dims.dropRight(1).product
      val baseCycles = size / p.toDouble

      val oFactor = 0.02*c - 0.019
      val smallOverhead = if (c < 8) 0.0 else 0.0175
      val overhead = if (p < 8) 1.0 + smallOverhead*p else oFactor*p + (1 - (8*oFactor)) + smallOverhead*8

      Math.ceil(baseCycles*overhead).toLong * iters

    case op: DenseTransfer[_,_] if op.isLoad =>
      val c = contentionOf(s)
      val dims = op.lens.map{x => boundOf(x).toInt }
      val size = dims.last
      val b = size  // TODO - max of this and max command size
      val r = 1.0   // TODO - number of commands needed (probably 1)
      val p = boundOf(op.p)

      val iters = dims.dropRight(1).product
      //System.out.println(s"Tile transfer $s: c = $c, r = $r, b = $b, p = $p")
      memoryModel(c,r.toInt,b.toInt,p.toInt) * iters

    case _ => super.latencyOfNode(s, d)
  }
}
