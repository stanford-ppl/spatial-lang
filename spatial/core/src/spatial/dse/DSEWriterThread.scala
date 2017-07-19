package spatial.dse

import java.io.PrintStream
import java.util.concurrent.{BlockingQueue, TimeUnit}

import argon.core.Config
import argon.util.Report._

case class DSEWriterThread(
  threadId:  Int,
  spaceSize: BigInt,
  filename:  String,
  workQueue: BlockingQueue[Array[String]],
  doneQueue: BlockingQueue[Int]
) extends Runnable {

  private var isAlive: Boolean = true
  private var hasTerminated: Boolean = false
  def requestStop(): Unit = { isAlive = false }

  def run(): Unit = {
    val data = new PrintStream(filename)

    val P = BigDecimal(spaceSize)
    var N = BigDecimal(0)
    var nextNotify = BigDecimal(0); val notifyStep = 5000
    val startTime = System.currentTimeMillis()

    while(isAlive) {
      val array = workQueue.poll(30000L, TimeUnit.MILLISECONDS)
      if (array.nonEmpty) {
        array.foreach{line => data.println(line) }
        data.flush()

        N += array.length
        if (N > nextNotify) {
          val time = System.currentTimeMillis - startTime
          println("  %.4f".format(100*(N/P).toFloat) + s"% ($N / $P) Complete after ${time/1000} seconds")
          nextNotify += notifyStep
        }
      }
      else requestStop()  // Somebody poisoned the work queue!
    }

    data.close()
    doneQueue.put(threadId)
    hasTerminated = true
  }
}
