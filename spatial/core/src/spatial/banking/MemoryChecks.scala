package spatial.banking

import argon.core._
import spatial.aliases._
import spatial.metadata._
import spatial.utils._


trait MemoryChecks { this: MemoryConfigurer =>

  protected def testMultipleReaders: Boolean = false
  protected def testMultipleWriters: Boolean = false
  protected def testConcurrentReaders: Boolean = isFIFO(mem) || isFILO(mem)
  protected def testConcurrentWriters: Boolean = isReg(mem) || isFIFO(mem) || isFILO(mem)
  protected def testMetaPipelinedReaders: Boolean = isFIFO(mem) || isFILO(mem)
  protected def testMetaPipelinedWriters: Boolean = true
  protected def testStreamingPars: Boolean = !isUnordered(mem)

  protected def allowMultipleReaders(readers: Access*): Boolean = true
  protected def allowMultipleWriters(writers: Access*): Boolean = true
  protected def allowConcurrentReaders(readers: Access*): Boolean = readers.map(_.node).count(isReadModify) == 1
  protected def allowConcurrentWriters(writers: Access*): Boolean = writers.map(_.node).count(isAccessWithoutAddress) == 1
  protected def allowMetaPipelinedReaders(readers: Access*): Boolean = readers.map(_.node).count(isReadModify) == 1
  protected def allowMetaPipelinedWriters(writers: Access*): Boolean = writers.length == 1


  /**
    * Performs a series of checks to make sure the memory is being used in a sane way.
    */
  def checkAccesses(readers: Seq[Access], writers: Seq[Access]): Unit = {
    val accesses = readers ++ writers

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
      val streamingAccesses = accesses.filter(isAccessWithoutAddress)
      val illegalParallelAccesses = streamingAccesses.filter{access =>
        val factors = unrollFactorsOf(access.node) diff unrollFactorsOf(mem)
        factors.flatten.drop(1).exists{c => c.toInt > 1}
      }
      if (illegalParallelAccesses.nonEmpty) {
        // TODO: Find/report controllers here as well
        error(mem.ctx, u"Memory $mem had " + plural(illegalParallelAccesses.length,"a streaming access", "streaming accesses") +
          " with illegal outer loop parallelization.")
        error(mem.ctx)
        error("This is disallowed by default because it violates ordering relative to sequential execution.")
        illegalParallelAccesses.foreach{access =>
          error(access.node.ctx, "Illegal parallelized access defined here", noError = true)
          error(access.node.ctx)
        }
      }
    }
  }

}
