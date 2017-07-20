package spatial.dse

import java.util.concurrent.{Executors, LinkedBlockingQueue}

import argon.core._
import argon.traversal.CompilerPass
import org.virtualized.SourceContext
import spatial.analysis._
import spatial.aliases._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._
import spatial.SpatialConfig

trait DSE extends CompilerPass with SpaceGenerator {
  override val name = "Design Space Exploration"
  final val PROFILING = true
  final val BLOCK_SIZE = 500

  abstract class SearchMode
  case object BruteForce extends SearchMode
  case object Randomized extends SearchMode

  // lazy val scalarAnalyzer = new ScalarAnalyzer{var IR = dse.IR }
  // lazy val memoryAnalyzer = new MemoryAnalyzer{var IR = dse.IR; def localMems = dse.localMems }
  def restricts: Set[Restrict]
  def tileSizes: Set[Param[Index]]
  def parFactors: Set[Param[Index]]
  def localMems: Seq[Exp[_]]
  def metapipes: Seq[Exp[_]]
  def top: Exp[_]

  override protected def process[S: Type](block: Block[S]): Block[S] = {
    if (SpatialConfig.enableDSE) {
      report("Tile sizes: ")
      tileSizes.foreach{t => report(u"  $t (${t.ctx})")}
      report("Parallelization factors:")
      parFactors.foreach{p => report(u"  $p (${p.ctx})")}
      report("Metapipelining toggles:")
      metapipes.foreach{m => report(u"  $m (${m.ctx})")}

      val intParams = (tileSizes ++ parFactors).toSeq
      val intSpace = createIntSpace(intParams, restricts)
      val ctrlSpace = createCtrlSpace(metapipes)
      val space = intSpace ++ ctrlSpace

      if (PRUNE) {
        report("Found the following space restrictions: ")
        restricts.foreach{r => report(s"  $r") }
        report("")
        report("Pruned space: ")
      }
      else {
        report("Space: ")
      }
      (intParams ++ metapipes).zip(space).foreach{case (p,d) => report(u"  $p: $d (${p.ctx})") }

      val restrictions: Set[Restrict] = if (PRUNE) restricts.filter{_.deps.size > 1} else Set.empty

      bruteForceDSE(space, restrictions, block)
    }
    dbg("Freezing parameters")
    tileSizes.foreach{t => t.makeFinal() }
    parFactors.foreach{p => p.makeFinal() }
    block
  }

  def bruteForceDSE(space: Seq[Domain[_]], restrictions: Set[Restrict], program: Block[_]): Unit = {
    val N = space.size
    val P = space.map{d => BigInt(d.len) }.product
    val T = 4 //SpatialConfig.threads
    val dir =  Config.cwd + "/results/"
    val filename = dir + Config.name + "_data.csv"

    new java.io.File(dir).mkdirs()

    report("Space Statistics: ")
    report("-------------------------")
    report(s"  # of parameters: $N")
    report(s"  # of points:     $P")
    report("")
    report(s"Using $T threads with block size of $BLOCK_SIZE")
    report(s"Writing results to file $filename")

    val workQueue = new LinkedBlockingQueue[(BigInt, Int)](5000)  // Max capacity specified here
    val fileQueue = new LinkedBlockingQueue[Array[String]](5000)
    val respQueue = new LinkedBlockingQueue[Int](10)

    val workerIds = (0 until T - 1).toList

    val pool = Executors.newFixedThreadPool(T)
    val workers = workerIds.map{id =>
      DSEThread(
        threadId  = id,
        origState = state,
        space     = space,
        restricts = restrictions,
        accel     = top,
        program   = program,
        localMems = localMems,
        workQueue = workQueue,
        outQueue  = fileQueue,
        doneQueue = respQueue
      )
    }
    val writer = DSEWriterThread(
      threadId  = T,
      spaceSize = P,
      filename  = filename,
      workQueue = fileQueue,
      doneQueue = respQueue
    )

    report("Initializing models...")

    // Initializiation may not be threadsafe - only creates 1 area model shared across all workers
    workers.foreach{worker => worker.init() }

    report("And aaaawaaaay we go!")

    workers.foreach{worker => pool.submit(worker) }
    pool.submit(writer)

    // Submit all the work to be done
    // Work queue blocks this thread when it's full (since we'll definitely be faster than the worker threads)
    val startTime = System.currentTimeMillis
    var i = BigInt(0)
    while (i < P) {
      val len: Int = if (P - i < BLOCK_SIZE) (P - i).toInt else BLOCK_SIZE
      if (len > 0) workQueue.put((i,len))
      i += BLOCK_SIZE
    }

    println("Ending work queue.")

    // Poison the work queue (make sure to use enough to kill them all!)
    workerIds.foreach { id => workQueue.put((BigInt(-1),0)) }

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

    if (PROFILING) {
      val bndTime = workers.map(_.bndTime).sum / totalTime
      val memTime = workers.map(_.memTime).sum / totalTime
      val conTime = workers.map(_.conTime).sum / totalTime
      val areaTime = workers.map(_.areaTime).sum / totalTime
      val cyclTime = workers.map(_.cyclTime).sum / totalTime
      println("Profile results: ")
      println("  Scalar Analysis: %.2f".format(bndTime*100) + "%")
      println("  Memory Analysis: %.2f".format(memTime*100) + "%")
      println("  Contn. Analysis: %.2f".format(conTime*100) + "%")
      println("    Area Analysis: %.2f".format(areaTime*100) + "%")
      println("   Cycle Analysis: %.2f".format(cyclTime*100) + "%")
    }
  }

}
