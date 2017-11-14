package spatial.dse

import java.io.PrintStream
import java.util.concurrent.{Executors, LinkedBlockingQueue, TimeUnit}

import argon.core._
import spatial.Subproc
import spatial.aliases._
import spatial.metadata._

trait HyperMapperDSE { this: DSE =>

  def hyperMapperDSE(space: Seq[Domain[Int]], program: Block[_], file: String = config.name + "_data.csv"): Unit = {
    val N = space.size
    val T = spatialConfig.threads
    val dir =  config.cwd + "/results/"
    val filename = dir + file

    new java.io.File(dir).mkdirs()

    report("Space Statistics: ")
    report("-------------------------")
    report(s"  # of parameters: $N")
    report("")
    report(s"Using $T threads")
    report(s"Writing results to file $filename")

    val workQueue = new LinkedBlockingQueue[Seq[Int]](5000)  // Max capacity specified here
    val fileQueue = new LinkedBlockingQueue[String](5000)

    val workerIds = (0 until T - 1).toList

    val pool = Executors.newFixedThreadPool(T)
    val workers = workerIds.map{id =>
      HyperMapperThread(
        threadId  = id,
        origState = state,
        space     = space,
        accel     = top,
        program   = program,
        localMems = localMems,
        workQueue = workQueue,
        outQueue  = fileQueue
      )
    }

    report("Initializing models...")
    val pcsFile = config.name + ".pcs"
    val HEADER = space.map(_.name).mkString(",") + "," + workers.head.areaHeading.mkString(",") + ", Cycles, Valid"

    withLog("dse", pcsFile){
      space.foreach{domain =>
        msg(s"""${domain.name} ${domain.tp} {${domain.options.mkString(", ")}}""", 100)
      }
    }
    val hm = Subproc("hypermapper", pcsFile) { input =>
      val lines = input.split("\n")
      val command = lines.head
      val header  = lines(1).split(",").map(_.trim)
      val points  = lines.drop(2)
      val order   = space.map{d => header.indexOf(d.name) }

      command match {
        case "Request" =>
          points.drop(1).foreach {request =>
            val values = request.split(",").map(_.trim.toInt)
            workQueue.put(order.map{i => values(i) })
          }
          Some(HEADER + "\n" + points.indices.map { _ => fileQueue.take() }.mkString("\n"))

        case "Pareto" =>
          val data = new PrintStream(config.name + "_data.csv")
          data.println(HEADER)
          points.foreach{pt => data.println(pt) }
          data.close()
          None
      }
    }

    // Initializiation may not be threadsafe - only creates 1 area model shared across all workers
    workers.foreach{worker => worker.init() }
    workers.foreach{worker => pool.submit(worker) }

    val startTime = System.currentTimeMillis
    hm.block(Some("dse"))

    println("Ending work queue.")

    // Poison the work queue (make sure to use enough to kill them all!)
    workerIds.foreach{_ => workQueue.put(Seq.empty[Int]) }

    println("Waiting for workers to complete...")
    pool.awaitTermination(10L, TimeUnit.HOURS)

    val endTime = System.currentTimeMillis()
    val totalTime = (endTime - startTime)/1000.0

    println(s"Completed space search in $totalTime seconds.")
  }

}
