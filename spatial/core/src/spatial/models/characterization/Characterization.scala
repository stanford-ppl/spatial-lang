package spatial.models.characterization

import spatial._
import argon.core.Config

import java.io.{File, PrintWriter}
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import sys.process._

trait AllBenchmarks
    extends Benchmarks with SpatialCompiler
    with FIFOs
    with Primitives
    with RegFiles
    with Regs
    with SRAMs 

object Characterization extends AllBenchmarks {
  def area(dir: JString): Map[JString, scala.Double] = {
    val output = Seq("~/spatial-lang/bin/scrape.py", dir).!!
    val pairs = output.split("\n").map(_.split(","))
    val map = pairs.map { case Array(k, v) => k -> v.toDouble }.toMap
    map
  }

  val pw = new PrintWriter(new File("characterization.csv"))

  def storeArea(name: JString, area: Map[JString, scala.Double]) = {
    pw.synchronized {
      area.foreach { case (comp, v) => pw.println(name + ',' + comp +',' + v) }
    }
  }

  val NUM_PAR_SYNTH: scala.Int = 2
  val stagingArgs = scala.Array("--synth")

  def main(args: scala.Array[JString]) {
    val programs: Seq[NamedSpatialProg] = gens.flatMap(_.expand)

    println("Number of programs: " + programs.length)

    val chiseled = programs.take(10).map(x => {
      val name = x._1
      Config.name = name
      Config.genDir = s"${Config.cwd}/gen/$name"
      initConfig(stagingArgs)
      resetState()
      compileProgram(x._2)
      Console.println(name + " chisel generated ")
      x._1
    })

    val exec = java.util.concurrent.Executors.newFixedThreadPool(NUM_PAR_SYNTH)
    implicit val ec = ExecutionContext.fromExecutor(exec)

    val workers = chiseled.map(x => Future {
       storeArea(x, area(x))
     })

    try {
      workers.foreach(Await.ready(_, Duration.Inf))
    } catch {
      case e: Throwable =>
        e.printStackTrace
    } finally {
      Console.println("COMPLETED")
      exec.shutdown()
      pw.close()
    }
  }

}
