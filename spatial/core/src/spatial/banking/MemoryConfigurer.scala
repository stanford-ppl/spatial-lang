package spatial.banking

import argon.core._
import spatial.aliases._
import spatial.metadata._
import spatial.utils._

case class ModBanking(N: Int, B: Int, alpha: Seq[Int])

case class MemoryInstance(
  reads:    Set[UnrolledAccess],      // All reads within this group
  writes:   Set[UnrolledAccess],      // All writes within this group
  metapipe: Option[Ctrl],             // Controller if at least some accesses require n-buffering
  banking:  Seq[ModBanking],          // Banking information
  depth:    Int,                      // Depth of n-buffer
  ports:    Map[UnrolledAccess,Int],  // Ports
  isAccum:  Boolean                   // Flag whether this instance is an accumulator
)

abstract class MemoryConfigurer(val mem: Exp[_]) {
  protected val dims: Seq[Int] = constDimsOf(mem)

  def testMultipleReaders: Boolean = false
  def testMultipleWriters: Boolean = false
  def testConcurrentReaders: Boolean = isFIFO(mem) || isFILO(mem)
  def testConcurrentWriters: Boolean = isReg(mem) || isFIFO(mem) || isFILO(mem)
  def testMetaPipelinedReaders: Boolean = isFIFO(mem) || isFILO(mem)
  def testMetaPipelinedWriters: Boolean = true
  def testStreamingPars: Boolean = true

  def allowMultipleReaders(readers: Access*): Boolean = true
  def allowMultipleWriters(writers: Access*): Boolean = true
  def allowConcurrentReaders(readers: Access*): Boolean = readers.map(_.node).count(isReadModify) == 1
  def allowConcurrentWriters(writers: Access*): Boolean = writers.map(_.node).count(isAccessWithoutAddress) == 1
  def allowMetaPipelinedReaders(readers: Access*): Boolean = readers.map(_.node).count(isReadModify) == 1
  def allowMetaPipelinedWriters(writers: Access*): Boolean = writers.length == 1


  def configure(): Unit = {
    val writers = writersOf(mem)
    val readers = readersOf(mem)

    dbg("")
    dbg("")
    dbg("-----------------------------------")
    dbg(u"Inferring instances for on-chip memory $mem (${mem.ctx})")
    dbg(c"${str(mem)}")

    /** Do a bunch of contract checks first to make sure the user isn't asking us to do something incorrect **/

    if (writers.isEmpty && !isOffChipMemory(mem) && !isLUT(mem) && initialDataOf(mem).isEmpty) {
      warn(mem.ctx, u"${mem.tp} $mem defined here has no writers.")
      warn(mem.ctx)
    }
    if (readers.isEmpty && !isOffChipMemory(mem)) {
      warn(mem.ctx, u"${mem.tp} $mem defined here has no readers.")
      warn(mem.ctx)
    }
    if (testMultipleReaders && hasMultipleReaders(mem)) {
      error(mem.ctxOrElse(ctx), u"${mem.tp} defined here has multiple readers. ")
      error("This is disallowed for this memory type.")
      error(mem.ctx)
      readers.foreach { read =>
        error(read.node.ctxOrElse(ctx), u"  Read defined here", noError = true)
        error(read.node.ctxOrElse(ctx))
      }
    }
    if (testMultipleWriters && hasMultipleWriters(mem)) {
      error(mem.ctxOrElse(ctx), u"${mem.tp} defined here has multiple writers: ")
      error("This is disallowed for this memory type.")
      error(mem.ctx)
      writers.foreach { write =>
        error(write.node.ctxOrElse(ctx), u"  Write defined here", noError = true)
        error(write.node.ctxOrElse(ctx))
      }
    }
    if (testConcurrentReaders) {
      val sets = getConcurrentReaders(mem) { (a, b) => allowConcurrentReaders(a, b) }
      if (sets.nonEmpty) {
        sets.foreach { case (ctrl, accs) =>
          error(mem.ctxOrElse(ctx), u"${mem.tp} defined here has illegal concurrent readers: ")
          error(mem.ctx)
          error(ctrl.node.ctx, "in the loop defined here: ", noError = true)
          error(ctrl.node.ctx)
          accs.foreach { a =>
            error(a.node.ctxOrElse(ctx), u"  Illegal concurrent read occurs here", noError = true)
            error(a.node.ctxOrElse(ctx))
          }
        }
      }
    }
    if (testConcurrentWriters) {
      val sets = getConcurrentWriters(mem) { (a, b) => allowConcurrentWriters(a, b) }
      if (sets.nonEmpty) {
        sets.foreach { case (ctrl, accs) =>
          error(mem.ctxOrElse(ctx), u"${mem.tp} defined here has illegal concurrent writers: ")
          error(mem.ctx)
          error(ctrl.node.ctx, "in the loop defined here: ", noError = true)
          error(ctrl.node.ctx)
          accs.foreach { a =>
            error(a.node.ctxOrElse(ctx), u"  Illegal concurrent write occurs here", noError = true)
            error(a.node.ctxOrElse(ctx))
          }
        }
      }
    }
    if (testMetaPipelinedReaders) {
      val sets = getMetaPipelinedReaders(mem) { (a, b) => allowMetaPipelinedReaders(a, b) }
      sets.foreach { case (ctrl, accs) =>
        error(mem.ctxOrElse(ctx), u"${mem.tp} defined here has illegal coarse-grain pipelined readers: ")
        error(mem.ctx)
        error(ctrl.node.ctx, "in the loop defined here: ", noError = true)
        error(ctrl.node.ctx)
        accs.foreach { a =>
          error(a.node.ctxOrElse(ctx), u"  Illegal pipelined read occurs here", noError = true)
          error(a.node.ctxOrElse(ctx))
        }
      }
    }
    // TODO: Move this to account for killing writes
    if (testMetaPipelinedWriters && !isExtraBufferable(mem)) {
      val sets = getMetaPipelinedWriters(mem) { (a, b) => allowMetaPipelinedWriters(a, b) }
      sets.foreach { case (ctrl, accs) =>
        error(mem.ctx, u"Memory $mem was inferred to be an N-Buffer with writes at multiple ports.")
        error("This behavior is disallowed by default, as it is usually not correct.")
        //error(u"To enable this behavior, declare the memory using:")
        //error(u"""  val ${mem.name.getOrElse("sram")} = $obj.buffer[${mem.tp.typeArguments.head}](dimensions)""")
        //error("Otherwise, disable outer loop pipelining using the Sequential controller tag.")
        error(ctrl.node.ctx, "These writes occur in the loop defined here", noError = true)
        error(ctrl.node.ctx)
        accs.foreach { a =>
          error(a.node.ctxOrElse(ctx), u"  Illegal pipelined write occurs here", noError = true)
          error(a.node.ctxOrElse(ctx))
        }
      }
    }

    if (testStreamingPars) {

    }

    val instances = bank(readers, writers)
    finalize(instances)
  }

  /**
    * Returns an approximation of the cost for the given banking strategy.
    */
  def cost(banking: Seq[ModBanking], depth: Int): Int = {
    val totalBanks = banking.map(_.N).product
    depth * totalBanks
  }

  /**
    * Returns the parallelization corresponding to each unaddressed access.
    * Includes a check for illegal outer loop parallelization relative to unaddressed memory.
    */
  def getUnaddressedPars(accesses: Seq[Access]): Seq[Int] = {
    var illegalParallelAccesses: Seq[Access] = Nil
    val accessFactors = accesses.map{access =>
      val factors = unrollFactorsOf(access.node) diff unrollFactorsOf(mem)
      val isInnerLoopOnly = factors.drop(1).forall{x => x.isEmpty || x.forall{case Exact(c) => c == 1; case _ => false}}

      if (!isInnerLoopOnly && !isUnordered(mem)) { illegalParallelAccesses +:= access }

      factors.flatten.map{_.toInt}.product
    }
    if (illegalParallelAccesses.nonEmpty) {
      // TODO: Find/report controllers here as well
      error(mem.ctx, u"Memory $mem had " + plural(illegalParallelAccesses.length,"an access", "accesses") +
        " with illegal outer loop parallelization.")
      error(mem.ctx)
      error("This is disallowed by default because it violates ordering relative to sequential execution.")
      illegalParallelAccesses.foreach{access =>
        error(access.node.ctx, "Illegal parallelized access defined here", noError = true)
        error(access.node.ctx)
      }
    }
    accessFactors
  }

  def bank(readers: Seq[Access], writers: Seq[Access]): Seq[MemoryInstance]

  def finalize(instances: Seq[MemoryInstance]): Unit
}