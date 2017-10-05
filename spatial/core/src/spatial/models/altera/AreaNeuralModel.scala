package spatial.models
package altera

import java.io.File

import argon.util.Report._
import org.encog.engine.network.activation.ActivationSigmoid
import org.encog.ml.data.basic.{BasicMLData, BasicMLDataSet}
import org.encog.neural.networks.BasicNetwork
import org.encog.neural.networks.layers.BasicLayer
import org.encog.neural.networks.training.propagation.resilient.ResilientPropagation
import org.encog.persist.EncogDirectoryPersistence

import scala.collection.JavaConverters._
import scala.io.Source

object AreaNeuralModel {
  val RBRAM  = 11
  val RLUTS  = 12
  val FREGS  = 13
  val UNVAIL = 14
  val IMPL   = 15
  val NEEDED = 16
}

abstract class AreaNeuralModel(
  val name: String,
  val filename: String,
  val OUT: Int,
  val LAYER2: Int
) {
  import AreaNeuralModel._

  private var network: BasicNetwork = _
  def needsInit: Boolean = network eq null
  val verbose = false
  val MAX_EPOCH = 600

  private val pwd = System.getenv().getOrDefault("SPATIAL_HOME", new java.io.File(".").getAbsolutePath)

  private lazy val dataFile: Array[Array[Double]] = {
    try {
      Source.fromFile(s"$pwd/data/$filename").getLines().toArray.drop(1).map(_.split(",").map(_.trim.toDouble))
    }
    catch {case _:Throwable =>
      error(s"Unable to find file $pwd/data/$filename - please set the SPATIAL_HOME environment variable")
      sys.exit()
      Array.empty[Array[Double]]
    }
  }

  private lazy val maxValues = dataFile(0)

  def init(): Unit = if (needsInit) {
    val encogFile = s"$pwd/data/$name.eg"
    val exists = new File(encogFile).exists

    if (exists) {
      report("Loaded " + name + " model from file")
      network = EncogDirectoryPersistence.loadObject(new File(encogFile)).asInstanceOf[BasicNetwork]
    }
    else {
      report(s"Training $name model...")
      val MODELS = 1000
      val data = dataFile.drop(1)

      // Normalize by max and offset
      val dataNorm = Array.tabulate(data.length){i =>
        val dat = data(i)
        Array.tabulate(dat.length){j => dat(j) / maxValues(j) }
      }
      val input = dataNorm.map(_.take(11))
      val output = dataNorm.map(_.slice(OUT,OUT+1).map(a => a))
      if (verbose) report(output.map(_.mkString(", ")).mkString(", "))
      val trainingSet = new BasicMLDataSet(input, output)
      var iter = 0
      var minError = Double.PositiveInfinity
      var maxError = Double.PositiveInfinity
      while (iter < MODELS) {
        val (curNetwork, curError, curMaxError) = trainOne(trainingSet)
        if (curMaxError < maxError) {
          minError = curError
          maxError = curMaxError
          network = curNetwork
        }
        iter += 1
      }
      report(name + "\n-----------------")
      report("Neural network results:")
      report(s"Average error: %.2f".format(100*minError/trainingSet.size) + "%")
      report(s"Maximum observed error: %.2f".format(100*maxError) + "%")

      EncogDirectoryPersistence.saveObject(new File(encogFile), network)
    }
  }

  private def trainOne(trainingSet: BasicMLDataSet) = {
    val network = new BasicNetwork()
    network.addLayer(new BasicLayer(null,true,11))
    network.addLayer(new BasicLayer(new ActivationSigmoid(),true,LAYER2))
    network.addLayer(new BasicLayer(new ActivationSigmoid(),false,1))
    network.getStructure.finalizeStructure()
    network.reset()

    var epoch = 1
    val train = new ResilientPropagation(network, trainingSet)
    train.iteration()
    while (epoch < MAX_EPOCH) {
      //report(s"Epoch #$epoch Error: ${100*train.getError()}")
      epoch += 1
      train.iteration()
    }
    train.finishTraining()
    //
    //report(s"Completed training at epoch $epoch with error of ${100*train.getError()}")

    var errors = 0.0
    var maxError = 0.0
    for (pair <- trainingSet.asScala) {
      val output = network.compute(pair.getInput)
      val data = Array.tabulate(11){i => pair.getInput.getData(i) * maxValues(i) }.mkString(", ")
      val diff = output.getData(0) - pair.getIdeal.getData(0)
      val error = diff / pair.getIdeal.getData(0)
      //report(s"output = ${output.getData(0) * maxValues(RLUTS)}, ideal = ${pair.getIdeal().getData(0) * maxValues(RLUTS)} (error = ${100*error}%, true = ${100*trueError}%)")
      if (Math.abs(error) > maxError) maxError = Math.abs(error)
      errors += Math.abs(error)
    }

    (network, errors, maxError)
  }

  def evaluate(x: Seq[Double]): Int = {
    if (needsInit) init()
    val input = x.toArray.zip(maxValues.slice(0,RBRAM)).map{case (n,m) => n/m}
    val output = network.compute(new BasicMLData(input))
    (output.getData(0) * maxValues(OUT)).toInt
  }
}

