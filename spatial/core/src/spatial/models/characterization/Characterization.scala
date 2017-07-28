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
  lazy val SPATIAL_HOME: String = sys.env.getOrElse("SPATIAL_HOME", {
    error("SPATIAL_HOME was not set!")
    error("Set top directory of spatial using: ")
    error("export SPATIAL_HOME=/path/to/spatial")
    sys.exit()
  })

  def area(dir: JString): Map[JString, scala.Double] = {
    val output = Seq("python", s"$SPATIAL_HOME/bin/scrape.py", s"${Config.cwd}/gen/$dir").!!
    val pairs = output.split("\n").map(_.split(","))
    val map = pairs.flatMap {
      case Array(k, v) => Some(k -> v.toDouble)
      case _ => None
    }.toMap
    map
  }

  val pw = new PrintWriter(new File("characterization.csv"))

  def storeArea(name: JString, area: Map[JString, scala.Double]): Unit = {
    pw.synchronized {
      area.foreach { case (comp, v) => pw.println(name + ',' + comp +',' + v) }
      pw.flush()
    }
  }

  val NUM_PAR_SYNTH: scala.Int = 2
  val stagingArgs = scala.Array("--synth")

  def main(args: scala.Array[JString]) {
    val programs: Seq[NamedSpatialProg] = gens.flatMap(_.expand)

    println("Number of programs: " + programs.length)
    println("Using SPATIAL_HOME: " + SPATIAL_HOME)

    val pool = Executors.newFixedThreadPool(NUM_PAR_SYNTH)
    val workQueue = new LinkedBlockingQueue[String](programs.length)

    class Synthesis(id: Int, queue: BlockingQueue[String]) extends Runnable {
      var isAlive = true

      def run(): Unit = {
        while(isAlive) {
          val name = queue.take()
          try {
            if (!name.isEmpty) {
              Console.println(s"#$id Synthesizing $name...")
              val parsed = area(name)
              storeArea(name, parsed)
              if (parsed.isEmpty) Console.println(s"#$id $name: FAIL")
              else Console.println(s"#$id $name: DONE")
            }
            else {
              println(s"#$id received kill signal")
              isAlive = false
            }
          }
          catch { case e: Throwable => e.printStackTrace() }
        }
      }
    }

    val workers = List.tabulate(NUM_PAR_SYNTH){id => new Synthesis(id, workQueue) }
    workers.foreach{worker => pool.submit(worker) }

    // Set i to previously generated programs
    var i = 0
    programs.take(i).foreach{x => workQueue.put(x._1) }

    programs.drop(i).foreach{x => //programs.take(2).flatMap{x => //
      val name = x._1
      initConfig(stagingArgs)
      Config.name = name
      Config.genDir = s"${Config.cwd}/gen/$name"
      Config.logDir = s"${Config.cwd}/logs/$name"
      Config.verbosity = -2
      Config.showWarn = false
      resetState()
      //_IR.useBasicBlocks = true // experimental for faster scheduling
      try {
        compileProgram(x._2)
        Console.println(s"Compiling #$i: $name: done")
        workQueue.put(name)
      }
      catch {case e:Throwable =>
        Console.println(s"Compiling #$i: $name: fail")
        Config.verbosity = 4
        withLog(Config.logDir,"exception.log") {
          log(e.getMessage)
          log(e.getCause)
          e.getStackTrace.foreach{line => log("  " + line) }
        }
      }
      i += 1
    }

    // Poison work queue
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

    pool.shutdown()
    pool.awaitTermination(14L, TimeUnit.DAYS)
    Console.println("COMPLETED")
    pw.close()
  }

}
