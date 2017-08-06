package spatial.models.characterization

import spatial._
import argon.core.Config
import argon.util.Report._
import java.io.{File, PrintWriter}

import sys.process._
import java.util.concurrent.{BlockingQueue, Executors, LinkedBlockingQueue, TimeUnit}

trait AllBenchmarks
    extends Benchmarks with SpatialCompiler
    with FIFOs
    with Primitives
    with RegFiles
    with Regs
    with SRAMs
    with Fringe

object Characterization extends AllBenchmarks {
  lazy val SPATIAL_HOME: String = sys.env.getOrElse("SPATIAL_HOME", {
    error("SPATIAL_HOME was not set!")
    error("Set top directory of spatial using: ")
    error("export SPATIAL_HOME=/path/to/spatial")
    sys.exit()
  })

  def area(dir: JString, synth: Boolean): (Map[JString, scala.Double], String) = {
    val nosynth = if (synth) Nil else Seq("--nomake")
    val output = (Seq("python", s"$SPATIAL_HOME/bin/scrape.py", s"${Config.cwd}/gen/$dir") ++ nosynth).!!
    val pairs = output.split("\n").map(_.split(","))
    var category = ""
    val map = pairs.flatMap {
      case line @ Array(k, v) =>
        try {
          val label = if (k.contains("O5") || k.contains("O6")) category.trim + "." else {
            category = k
            ""
          }
          Some(label + k -> v.toDouble)
        }
        catch {case _: Throwable =>
          //log.println(s"Ignoring line: " + line.mkString(","))
          None
        }
      case line =>
        //log.println(s"Ignoring line: " + line.mkString(","))
        None
    }.toMap
    (map, output)
  }

  val pw = new PrintWriter(new File("characterization.csv"))
  val fl = new PrintWriter(new File("failures.log"))

  def storeArea(name: JString, area: Map[JString, scala.Double]): Unit = {
    pw.synchronized {
      area.foreach { case (comp, v) => pw.println(name + ',' + comp +',' + v) }
      pw.flush()
    }
  }

  def noteFailure(name: JString): Unit = {
    fl.synchronized {
      Console.println(s"$name: FAIL")
      fl.println(name)
      fl.flush()
    }
  }

  class Synthesis(id: Int, queue: BlockingQueue[String], synth: Boolean) extends Runnable {
    var isAlive = true

    def run(): Unit = {
      Console.println(s"Thread #$id started")

      while(isAlive) {
        val name = queue.take()

        try {
          if (!name.isEmpty) {
            if (synth) Console.println(s"#$id Synthesizing ${Config.cwd}/gen/$name...")
            else       Console.println(s"#$id Scraping ${Config.cwd}/gen/$name...")

            val (parsed, _) = area(name, synth)
            storeArea(name, parsed)
            if (parsed.isEmpty) noteFailure(name)
            else Console.println(s"#$id $name: DONE")
            //log.close()
          }
          else {
            println(s"Thread #$id received kill signal")
            isAlive = false
          }
        }
        catch { case e: Throwable =>
          //val file = new File(s"${Config.cwd}/gen/$name/")
          //file.mkdirs()
          //val log = new PrintWriter(s"${Config.cwd}/gen/$name/exception.log")
          //e.printStackTrace()

          noteFailure(name)
          //log.close()
        }
      }

      Console.println(s"Thread #$id ended")
    }
  }

  val stagingArgs = scala.Array("--synth")

  def getYN(prompt: String): Boolean = {
    var answered = false
    var answer = false
    while (!answered) {
      Console.print(prompt + " [y/n]: ")
      val a = scala.io.StdIn.readLine()
      if (a.toLowerCase() == "y") {
        answer = true
        answered = true
      }
      else if (a.toLowerCase() == "n") {
        answer = false
        answered = true
      }
      if (!answered) Console.println("(Please respond either y or n)")
    }
    answer
  }

  def main(args: scala.Array[JString]) {
    val benchmarks = gens.flatMap(_.expand)
    println("Number of benchmarks: " + benchmarks.length)

    def useDefaultSettings = getYN("Use default settings for this machine")

    val localMachine = java.net.InetAddress.getLocalHost
    val (threads, start, end) = localMachine.getHostName match {
      case "london"   if useDefaultSettings => (100, 0, 2116)
      case "tucson"   if useDefaultSettings => (25, 2116, 2789)
      case _          =>
        Console.print("Threads: ")
        val par = scala.io.StdIn.readLine().toInt
        Console.print("Start: ")
        val start = scala.io.StdIn.readLine().toInt
        Console.print("End: ")
        val end = scala.io.StdIn.readLine().toInt
        (par, start, end)
    }
    val programs = benchmarks.slice(start, end)
    val RUN_SPATIAL = getYN("Run Spatial compiler")
    val RUN_SYNTH = getYN("Run synthesis")

    initConfig(stagingArgs)

    Console.print(s"Run directory [${Config.cwd}]: ")
    val cwdOpt = scala.io.StdIn.readLine()
    if (cwdOpt != "") Config.cwd = cwdOpt

    println("Number of programs: " + programs.length)
    println("Using SPATIAL_HOME: " + SPATIAL_HOME)
    println("Using CWD: " + Config.cwd)
    Console.print("Previously generated programs [0]: ")
    var i: Int = try { scala.io.StdIn.readLine().toInt } catch {case _:Throwable => 0 }

    val pool = Executors.newFixedThreadPool(threads)
    val workQueue = new LinkedBlockingQueue[String](programs.length)

    val workers = List.tabulate(threads){id => new Synthesis(id, workQueue, RUN_SYNTH) }
    workers.foreach{worker => pool.submit(worker) }

    if (RUN_SPATIAL) {
      // Set i to previously generated programs
      programs.take(i).foreach { x => workQueue.put(x._1) }
      programs.drop(i).foreach { x =>
        val name = x._1
        Config.name = name
        Config.genDir = s"${Config.cwd}/gen/$name"
        Config.logDir = s"${Config.cwd}/logs/$name"
        Config.verbosity = -2
        Config.showWarn = false
        resetState()
        try {
          compileProgram(x._2)
          Console.println(s"Compiling #$i: $name: done")
          workQueue.put(name)
        }
        catch {
          case e: Throwable =>
            noteFailure(name + " COMPILATION")
            Config.verbosity = 4
            withLog(Config.logDir, "exception.log") {
              log(e.getMessage)
              log(e.getCause)
              e.getStackTrace.foreach { line => log("  " + line) }
            }
        }
        i += 1
      }
    }
    else {
      programs.foreach {x => workQueue.put(x._1) }
    }

    // Poison work queue
    (0 until threads).foreach{_ => workQueue.put("") }

    pool.shutdown()
    pool.awaitTermination(14L, TimeUnit.DAYS)
    Console.println("COMPLETED")
    pw.close()
    fl.close()
  }

}
