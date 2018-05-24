package spatial.codegen.scalagen

import argon.core._
import spatial.aliases._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._
import java.nio.file._

trait ScalaGenTrace extends ScalaGenControl with ScalaGenMemories {

  lazy val tracesPath = src"${config.genDir}${config.sep}traces"

  var emitedCurrOffset = false
  def emitCurrOffset = {
    if (!emitedCurrOffset) {
      emit(src"var currentOffset = 0")
      emitedCurrOffset = true
    }
  }

  def getTraceFile(lhs:Any) = {
    src"""new java.io.File("${tracesPath}${config.sep}${lhs}.trace")"""
  }

  def getTrace(lhs:Any) = {
    src"""traces.getOrElseUpdate("${lhs}", new java.io.PrintWriter(${getTraceFile(lhs)}))"""
  }

  def emitTrace(lhs:Any, dram:Exp[_], signal:Any) = {
    emit(src"""${getTrace(lhs)}.write($signal + ${dram}_offset + "\n")""")
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case Hwblock(func,isForever) =>
      if (spatialConfig.enableTrace) {
        emit(src"val traces = scala.collection.mutable.Map[String, java.io.PrintWriter]()")
        val dir = new java.io.File(tracesPath)
        if (dir.exists) {
          dir.listFiles.foreach(_.delete)
        } else {
          Files.createDirectories(Paths.get(tracesPath))
        }
      }
      super.emitNode(lhs, rhs)
      if (spatialConfig.enableTrace) {
        emit(src"traces.foreach{ case (name, trace) => trace.close()}")
        emit(src"traces.clear")
      }

    case op@DRAMNew(dims,zero) =>
      super.emitNode(lhs, rhs)
      val elementsPerBurst = spatialConfig.target.burstSize / op.bT.length
      val dramSizeWord = src"""(${dims.map(quote).mkString("*")} + $elementsPerBurst)"""
      val bytesPerWord = op.bT.length / 8 + (if (op.bT.length % 8 != 0) 1 else 0)
      val dramSizeBytes = src"""$dramSizeWord * $bytesPerWord"""
      emitCurrOffset
      emit(src"""val ${lhs}_offset = currentOffset""")
      emit(src"""currentOffset += $dramSizeBytes""")

    case e@FringeDenseLoad(dram,cmdStream,dataStream) =>
      if (spatialConfig.enableTrace) {
        open(src"$cmdStream.foreach{cmd => ")
          emitTrace(lhs, dram, "cmd.offset")
        close("}")
      }
      super.emitNode(lhs, rhs)

    case e@FringeDenseStore(dram,cmdStream,dataStream,ackStream) =>
      if (spatialConfig.enableTrace) {
        open(src"$cmdStream.foreach{cmd => ")
          emitTrace(lhs, dram, "cmd.offset")
        close("}")
      }
      super.emitNode(lhs, rhs)

    case e@FringeSparseLoad(dram,addrStream,dataStream) =>
      if (spatialConfig.enableTrace) {
        open(src"$addrStream.foreach{addr => ")
          emitTrace(lhs, dram, "addr")
        close("}")
      }
      super.emitNode(lhs, rhs)

    case e@FringeSparseStore(dram,cmdStream,ackStream) =>
      if (spatialConfig.enableTrace) {
        open(src"$cmdStream.foreach{cmd => ")
          emitTrace(lhs, dram, "cmd._2")
        close("}")
      }
      super.emitNode(lhs, rhs)

    case _ => super.emitNode(lhs, rhs)
  }
}
