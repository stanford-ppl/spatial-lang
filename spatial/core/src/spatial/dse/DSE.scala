package spatial.dse

import java.io.PrintWriter
import java.util.concurrent.{BlockingQueue, Executors, LinkedBlockingQueue}

import argon.core._
import argon.traversal.CompilerPass
import org.virtualized.SourceContext
import spatial.aliases._
import spatial.metadata._

import scala.collection.mutable.ArrayBuffer

trait DSE extends CompilerPass with SpaceGenerator {
  override val name = "Design Space Exploration"
  final val PROFILING = true
  final val BLOCK_SIZE = 500

  /*abstract class SearchMode
  case object BruteForceSearch extends SearchMode
  case object HeuristicSearch  extends SearchMode
  final val searchMode: SearchMode = HeuristicSearch*/

  // lazy val scalarAnalyzer = new ScalarAnalyzer{var IR = dse.IR }
  // lazy val memoryAnalyzer = new MemoryAnalyzer{var IR = dse.IR; def localMems = dse.localMems }
  def restricts: Set[Restrict]
  def tileSizes: Set[Param[Index]]
  def parFactors: Set[Param[Index]]
  def localMems: Seq[Exp[_]]
  def metapipes: Seq[Exp[_]]
  def top: Exp[_]

  override protected def process[S: Type](block: Block[S]): Block[S] = {
    if (spatialConfig.enableDSE) {
      report("Tile sizes: ")
      tileSizes.foreach{t => report(u"  $t (${t.ctx})")}
      report("Parallelization factors:")
      parFactors.foreach{p => report(u"  $p (${p.ctx})")}
      report("Metapipelining toggles:")
      metapipes.foreach{m => report(u"  $m (${m.ctx})")}

      val intParams = (tileSizes ++ parFactors).toSeq
      val intSpace = createIntSpace(intParams, restricts)
      val ctrlSpace = createCtrlSpace(metapipes)
      val params = intParams ++ metapipes
      val space = intSpace ++ ctrlSpace

      report("Space: ")
      params.zip(space).foreach{case (p,d) => report(u"  $p: $d (${p.ctx})") }

      if (spatialConfig.bruteForceDSE) bruteForceDSE(params, space, block)
      else if (spatialConfig.heuristicDSE) heuristicDSE(params, space, restricts, block)
    }
    dbg("Freezing parameters")
    tileSizes.foreach{t => t.makeFinal() }
    parFactors.foreach{p => p.makeFinal() }
    block
  }

  def heuristicDSE(params: Seq[Exp[_]], space: Seq[Domain[_]], restrictions: Set[Restrict], program: Block[_]): Unit = {
    val EXPERIMENT = spatialConfig.experimentDSE
    report("Intial Space Statistics: ")
    report("-------------------------")
    report(s"  # of parameters: ${space.size}")
    report(s"  # of points:     ${space.map(d => BigInt(d.len)).product}")
    report("")

    report("Found the following space restrictions: ")
    restrictions.foreach{r => report(s"  $r") }

    val prunedSpace = space.zip(params).map{
      case (domain, p: Param[_]) =>
        val relevant = restrictions.filter(_.dependsOnlyOn(p))
        domain.filter{state => relevant.forall(_.evaluate()(state)) }

      case (domain, _) => domain
    }
    val indexedSpace = prunedSpace.zipWithIndex
    val N = prunedSpace.length
    val dims = prunedSpace.map{d => BigInt(d.len) }
    val prods = List.tabulate(N){i => dims.slice(i+1,N).product }
    val NPts = dims.product

    report("")
    report("Pruned space: ")
    params.zip(prunedSpace).foreach{case (p,d) => report(u"  $p: $d (${p.ctx})") }

    val restricts = restrictions.filter(_.deps.size > 1)
    def isLegalSpace(): Boolean = restricts.forall(_.evaluate())

    if (NPts < Int.MaxValue) {
      val legalPoints = ArrayBuffer[BigInt]()

      val legalStart = System.currentTimeMillis
      println(s"Enumerating ALL legal points...")
      val startTime = System.currentTimeMillis
      var nextNotify = 0.0; val notifyStep = 20000
      for (i <- 0 until NPts.toInt) {
        indexedSpace.foreach{case (domain,d) => domain.set( ((i / prods(d)) % dims(d)).toInt ) }
        if (isLegalSpace()) legalPoints += i

        if (i > nextNotify) {
          val time = System.currentTimeMillis - startTime
          println("%.4f".format(100*(i/NPts.toFloat)) + s"% ($i / $NPts) Complete after ${time/1000} seconds (${legalPoints.size} / $i valid so far)")
          nextNotify += notifyStep
        }
      }
      val legalCalcTime = System.currentTimeMillis - legalStart
      val legalSize = legalPoints.length
      println(s"Legal space size is $legalSize (elapsed time: ${legalCalcTime/1000} seconds)")
      println("")


      if (EXPERIMENT) {
        val times = new PrintWriter("times.log")

        val sizes = List(500, 1000, 1500, 2000, 2500, 3000, 3500, 4000, 4500, 5000, 5500, 6000, 6500, 7000, 7500, 8000, 8500, 9000, 9500, 10000)
        sizes.filter(_ <= legalSize).foreach{size =>
          (0 until 10).foreach{i =>

            val points = scala.util.Random.shuffle(legalPoints).take(size)
            val filename = s"${config.name}_size_${size}_exp_$i.csv"

            val startTime = System.currentTimeMillis()

            threadBasedDSE(points.length, params, prunedSpace, program, file = filename) { queue =>
              points.sliding(BLOCK_SIZE, BLOCK_SIZE).foreach { block =>
                queue.put(block)
              }
            }

            val endTime = System.currentTimeMillis()
            times.println(s"$filename: ${endTime - startTime}")
          }
        }
        times.close()
      }
      else {
        val points = scala.util.Random.shuffle(legalPoints).take(75000)

        threadBasedDSE(points.length, params, prunedSpace, program) { queue =>
          points.sliding(BLOCK_SIZE, BLOCK_SIZE).foreach { block =>
            queue.put(block)
          }
        }
      }
    }
    else {
      error("Space size is greater than Int.MaxValue. Don't know what to do here yet...")
    }
  }

  // P: Total space size
  def threadBasedDSE(P: BigInt, params: Seq[Exp[_]], space: Seq[Domain[_]], program: Block[_], file: String = config.name+"_data.csv")(pointGen: BlockingQueue[Seq[BigInt]] => Unit): Unit = {
    val names = params.map{p => p.name.getOrElse(p.toString) }
    val N = space.size
    val T = spatialConfig.threads
    val dir =  config.cwd + "/results/"
    val filename = dir + file

    new java.io.File(dir).mkdirs()

    report("Space Statistics: ")
    report("-------------------------")
    report(s"  # of parameters: $N")
    report(s"  # of points:     $P")
    report("")
    report(s"Using $T threads with block size of $BLOCK_SIZE")
    report(s"Writing results to file $filename")

    val workQueue = new LinkedBlockingQueue[Seq[BigInt]](5000)  // Max capacity specified here
    val fileQueue = new LinkedBlockingQueue[Array[String]](5000)
    val respQueue = new LinkedBlockingQueue[Int](10)

    val workerIds = (0 until T - 1).toList

    val pool = Executors.newFixedThreadPool(T)
    val workers = workerIds.map{id =>
      DSEThread(
        threadId  = id,
        origState = state,
        params    = params,
        space     = space,
        accel     = top,
        program   = program,
        localMems = localMems,
        workQueue = workQueue,
        outQueue  = fileQueue,
        doneQueue = respQueue
      )
    }
    report("Initializing models...")

    // Initializiation may not be threadsafe - only creates 1 area model shared across all workers
    workers.foreach{worker => worker.init() }

    val superHeader = List.tabulate(names.length){i => if (i == 0) "INPUTS" else "" }.mkString(",") + "," +
      List.tabulate(workers.head.areaHeading.length){i => if (i == 0) "OUTPUTS" else "" }.mkString(",") + ", ,"
    val header = names.mkString(",") + "," + workers.head.areaHeading.mkString(",") + ", Cycles, Valid"

    val writer = DSEWriterThread(
      threadId  = T,
      spaceSize = P,
      filename  = filename,
      header    = superHeader + "\n" + header,
      workQueue = fileQueue,
      doneQueue = respQueue
    )

    report("And aaaawaaaay we go!")

    workers.foreach{worker => pool.submit(worker) }
    pool.submit(writer)

    // Submit all the work to be done
    // Work queue blocks this thread when it's full (since we'll definitely be faster than the worker threads)
    val startTime = System.currentTimeMillis

    pointGen(workQueue)
    /*var i = BigInt(0)
    while (i < P) {
      val len: Int = if (P - i < BLOCK_SIZE) (P - i).toInt else BLOCK_SIZE
      if (len > 0) workQueue.put((i,len))
      i += BLOCK_SIZE
    }*/

    println("Ending work queue.")

    // Poison the work queue (make sure to use enough to kill them all!)
    workerIds.foreach{_ => workQueue.put(Seq.empty[BigInt]) }

    println("Waiting for workers to complete...")

    // Wait for all the workers to die (this is a fun metaphor)
    workerIds.foreach { id =>
      val resp = respQueue.take()
      println(s"  Received end signal from $resp")
    }

    println("Waiting for file writing to complete...")

    // Poison the file queue too and wait for the file writer to die
    fileQueue.put(Array.empty[String])
    respQueue.take()

    val endTime = System.currentTimeMillis()
    val totalTime = (endTime - startTime)/1000.0

    println(s"Completed space search in $totalTime seconds.")
  }

  def bruteForceDSE(params: Seq[Exp[_]], space: Seq[Domain[_]], program: Block[_]): Unit = {
    val P = space.map{d => BigInt(d.len) }.product

    threadBasedDSE(P, params, space, program){queue =>
      var i = BigInt(0)
      while (i < P) {
        val len: Int = if (P - i < BLOCK_SIZE) (P - i).toInt else BLOCK_SIZE
        if (len > 0) queue.put(i until i + len)
        i += BLOCK_SIZE
      }
    }
  }

}
