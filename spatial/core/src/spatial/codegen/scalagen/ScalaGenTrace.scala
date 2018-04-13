package spatial.codegen.scalagen

import argon.core._
import spatial.aliases._
import spatial.metadata._
import spatial.nodes._
import spatial.utils._
import java.nio.file._

trait ScalaGenTrace extends ScalaGenControl with ScalaGenMemories {

  lazy val tracesPath = s"${config.genDir}${config.sep}traces"

  def getTraceFile(lhs:Any) = {
    s"""new java.io.File("${tracesPath}${config.sep}${lhs}.trace")"""
  }

  def getTrace(lhs:Any) = {
    s"""traces.getOrElseUpdate("${lhs}", new java.io.PrintWriter(${getTraceFile(lhs)}))"""
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case Hwblock(func,isForever) =>
      if (spatialConfig.enableTrace) {
        emit(s"val traces = scala.collection.mutable.Map[String, java.io.PrintWriter]()")
        Files.createDirectories(Paths.get(tracesPath))
      }
      super.emitNode(lhs, rhs)
      if (spatialConfig.enableTrace) {
        emit(s"traces.foreach{ case (name, trace) => trace.close()}")
        emit(s"traces.clear")
      }

    case e@FringeDenseLoad(dram,cmdStream,dataStream) =>
      if (spatialConfig.enableTrace) {
        open(src"$cmdStream.foreach{cmd => ")
          emit(s"""${getTrace(lhs)}.write(cmd.offset + "\\n")""")
        close("}")
      }
      super.emitNode(lhs, rhs)

    case e@FringeDenseStore(dram,cmdStream,dataStream,ackStream) =>
      if (spatialConfig.enableTrace) {
        open(src"$cmdStream.foreach{cmd => ")
          emit(s"""${getTrace(lhs)}.write(cmd.offset + "\\n")""")
        close("}")
      }
      super.emitNode(lhs, rhs)

    case e@FringeSparseLoad(dram,addrStream,dataStream) =>
      if (spatialConfig.enableTrace) {
        open(src"$addrStream.foreach{addr => ")
          emit(s"""${getTrace(lhs)}.write(addr + "\\n")""")
        close("}")
      }
      super.emitNode(lhs, rhs)

    case e@FringeSparseStore(dram,cmdStream,ackStream) =>
      if (spatialConfig.enableTrace) {
        open(src"$cmdStream.foreach{cmd => ")
          emit(s"""${getTrace(s"${lhs}_addr")}.write(cmd._2 + "\\n")""")
          emit(s"""${getTrace(s"${lhs}_data")}.write(cmd._1 + "\\n")""")
        close("}")
      }
      super.emitNode(lhs, rhs)

    case _ => super.emitNode(lhs, rhs)
  }
}
