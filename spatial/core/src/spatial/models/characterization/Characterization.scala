package spatial.models.characterization

import spatial._
import argon.core.Config
import argon.util.Report._
import java.io.{File, PrintWriter}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import sys.process._
import java.util.concurrent.{BlockingQueue, Executors, LinkedBlockingQueue, TimeUnit}

trait AllBenchmarks
    extends Benchmarks with SpatialCompiler
    with FIFOs
    with Primitives
    with RegFiles
    with Regs
    with SRAMs 

object Characterization extends AllBenchmarks {
  def area(dir: JString): Map[JString, scala.Double] = {
    val output = Seq("~/spatial/bin/scrape.py", dir).!!
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

    var i = 1458
    val prev = programs.take(i).map{x => x._1 }

    val chiseled = programs.take(2).flatMap{x => //prev ++ programs.drop(i).flatMap{x =>
      val name = x._1
      initConfig(stagingArgs)
      Config.name = name
      Config.genDir = s"${Config.cwd}/gen/$name"
      Config.logDir = s"${Config.cwd}/logs/$name"
      Config.verbosity = -2
      Config.showWarn = false
      Console.print(s"Compiling #$i: " + name + "...")
      resetState()
      _IR.useBasicBlocks = true // experimental for faster scheduling
      val result = try {
        compileProgram(x._2)
        Console.println("done")
        Some(x._1)
      }
      catch {case e:Throwable =>
        Console.println("fail")
        Config.verbosity = 4
        withLog(Config.logDir,"exception.log") {
          log(e.getMessage)
          log(e.getCause)
          e.getStackTrace.foreach{line => log("  " + line) }
        }
        None
      }
      i += 1
      result
    }

    val pool = Executors.newFixedThreadPool(NUM_PAR_SYNTH)
    val workQueue = new LinkedBlockingQueue[String](chiseled.length)

    class Synthesis(queue: BlockingQueue[String]) extends Runnable {
      var isAlive = true

      def run(): Unit = {
        while(isAlive) {
          val name = queue.take()
          try {
            if (!name.isEmpty) storeArea(name, area(name))
            else isAlive = false
          }
          catch { case e: Throwable => e.printStackTrace() }
        }
      }
    }

    val workers = List.fill(NUM_PAR_SYNTH){ new Synthesis(workQueue) }
    workers.foreach{worker => pool.submit(worker) }

    chiseled.foreach{name => workQueue.put(name) }
    (0 until NUM_PAR_SYNTH).foreach{_ => workQueue.put("") }


    /*val workers = chiseled.map(x => Future {
       println("done")
     })*/

    /*try {
      workers.foreach(Await.ready(_, Duration.Inf))
    } catch {
      case e: Throwable =>
        e.printStackTrace
    } finally {
      Console.println("COMPLETED")
      exec.shutdown()
      pw.close()
    }*/

    pool.awaitTermination(14L, TimeUnit.DAYS)
    Console.println("COMPLETED")
    pw.close()
  }

}
