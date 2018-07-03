package spatial.dse

import java.util.concurrent.{Executors, LinkedBlockingQueue, TimeUnit}

import argon.core._
import spatial.BufferedProcess
import spatial.aliases._
import spatial.metadata._

trait HyperMapperDSE { this: DSE =>

  def hyperMapperDSE(space: Seq[Domain[_]], program: Block[_], file: String = config.name + "_data.csv"): Unit = {
    val N = space.size
    val T = spatialConfig.threads
    val dir = if (config.resDir.startsWith("/")) config.resDir + "/" else config.cwd + s"/${config.resDir}/"
    val filename = dir + file

    new java.io.File(dir).mkdirs()

    report("Space Statistics: ")
    report("-------------------------")
    report(s"  # of parameters: $N")
    report("")
    report(s"Using $T threads")
    report(s"Writing results to file $filename")

    val workQueue = new LinkedBlockingQueue[Seq[DesignPoint]](20000)  // Max capacity specified here
    val resultQueue = new LinkedBlockingQueue[Array[String]](20000)
    val requestQueue  = new LinkedBlockingQueue[DSERequest](10)
    val doneQueue     = new LinkedBlockingQueue[Boolean](5) // TODO: Could be better

    val workerIds = (0 until T).toList

    val pool = Executors.newFixedThreadPool(T)
    val commPool = Executors.newFixedThreadPool(2)

    val jsonFile = config.name + ".json"
    val workDir = config.cwd + "/dse_hm"

    println("Creating Hypermapper config JSON file")
    withLog(workDir, jsonFile){
      msg(s"{")
      msg(s"""  "application_name": "${config.name}",
             |  "models": {
             |    "model": "random_forest",
             |    "number_of_trees": 20
             |  },
             |  "max_number_of_predictions": 1000000,
             |  "max_number_AL_iterations": 5,
             |  "number_of_cpus": 6,
             |  "number_of_repetitions": 1,
             |  "hypermapper_mode": {
             |    "mode": "interactive"
             |  },
             |  "optimization_objectives": ["ALMs", "Cycles"],
             |  "feasible_output": {
             |    "name": "Valid",
             |    "true_value": "true",
             |    "false_value": "false",
             |    "enable_feasible_predictor": true
             |  },
             |  "timestamp": "Timestamp",
             |  "max_runs_in_one_AL_iteration": 100,
             |  "run_directory": "$dir",
             |  "output_data_file": "${config.name}_output_dse_samples.csv",
             |  "output_pareto_file": "${config.name}_output_pareto.csv",
             |  "bootstrap_sampling": {
             |    "bootstrap_type": "random sampling",
             |    "number_of_samples": 10000
             |  },
             |  "output_image": {
             |    "output_image_pdf_file": "${config.name}_output_pareto.pdf",
             |    "optimization_objectives_labels_image_pdf": ["Logic Utilization (%)", "Cycles (log)"],
             |    "image_xlog": false,
             |    "image_ylog": false,
             |    "objective_1_max": 262400
             |  },
             |  "input_parameters": {""".stripMargin)
      space.zipWithIndex.foreach{case (domain, i) =>
        msg(s"""    "${domain.name}": {
             |      "parameter_type" : "${domain.tp}",
             |      "values" : [${domain.optionsString}],
             |      "parameter_default" : ${domain.valueString},
             |      "prior" : ${domain.prior}
             |    }${if (i == space.length-1) "" else ","}""".stripMargin)
      }
      msg("  }")
      msg("}")
    }

    val workers = workerIds.map{id =>
      val threadState = new State
      state.copyTo(threadState)
      DSEThread(
        threadId  = id,
        space     = space,
        accel     = top,
        program   = program,
        localMems = localMems,
        workQueue = workQueue,
        outQueue  = resultQueue
      )(threadState)
    }

    // Initializiation may not be threadsafe - only creates 1 area model shared across all workers
    println("Initializing models...")
    workers.foreach{worker => worker.init() }
    println("Starting up workers...")
    val HEADER = space.map(_.name).mkString(",") + "," + workers.head.areaHeading.mkString(",") + ",Cycles,Valid,Timestamp"

    val hm = BufferedProcess("python", spatialConfig.HYPERMAPPER + "/scripts/hypermapper.py", workDir + "/" + jsonFile)
    println("Starting up HyperMapper...")
    println(s"python ${spatialConfig.HYPERMAPPER}/scripts/hypermapper.py $workDir/$jsonFile")
    val (hmOutput, hmInput) = hm.run(Some(workDir))

    val receiver = HyperMapperReceiver(
      input      = hmOutput,
      workOut    = workQueue,
      requestOut = requestQueue,
      doneOut    = doneQueue,
      space      = space,
      HEADER     = HEADER,
      THREADS    = T,
      DIR        = workDir
    )
    val sender = HyperMapperSender(
      output    = hmInput,
      requestIn = requestQueue,
      resultIn  = resultQueue,
      doneOut   = doneQueue,
      HEADER    = HEADER
    )

    workers.foreach{worker => pool.submit(worker) }
    val startTime = System.currentTimeMillis()
    workers.foreach{worker => worker.START = startTime }
    commPool.submit(receiver)
    commPool.submit(sender)

    val done = doneQueue.take()
    if (done) {
      println("Waiting for workers to complete...")
      pool.shutdown()
      pool.awaitTermination(10L, TimeUnit.HOURS)
      commPool.shutdown()
      commPool.awaitTermination(10L, TimeUnit.HOURS)

      val endTime = System.currentTimeMillis()
      val totalTime = (endTime - startTime)/1000.0

      println(s"Completed space search in $totalTime seconds.")
    }
    else {
      println("Connected process terminated early!")
      pool.shutdownNow()
      commPool.shutdownNow()
      pool.awaitTermination(1L, TimeUnit.MINUTES)
      commPool.awaitTermination(1L, TimeUnit.MINUTES)
      sys.exit(-1) // Bail for now
    }

    if (PROFILING) {
      val bndTime = workers.map(_.bndTime).sum
      val memTime = workers.map(_.memTime).sum
      val conTime = workers.map(_.conTime).sum
      val areaTime = workers.map(_.areaTime).sum
      val cyclTime = workers.map(_.cyclTime).sum
      val total = bndTime + memTime + conTime + areaTime + cyclTime
      println("Profiling results: ")
      println(s"Combined runtime: $total")
      println(s"Scalar analysis:     $bndTime"  + " (%.3f)".format(100*bndTime.toDouble/total) + "%")
      println(s"Memory analysis:     $memTime"  + " (%.3f)".format(100*memTime.toDouble/total) + "%")
      println(s"Contention analysis: $conTime"  + " (%.3f)".format(100*conTime.toDouble/total) + "%")
      println(s"Area analysis:       $areaTime" + " (%.3f)".format(100*areaTime.toDouble/total) + "%")
      println(s"Runtime analysis:    $cyclTime" + " (%.3f)".format(100*cyclTime.toDouble/total) + "%")
    }

    sys.exit(0) // Bail for now
  }

}
