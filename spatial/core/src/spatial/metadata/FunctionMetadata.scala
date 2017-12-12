package spatial.metadata

import argon.core._
import forge._
import spatial.aliases._
import spatial.utils._

case class FunctionCalls(calls: Seq[Trace]) extends Metadata[FunctionCalls] {
  def mirror(f:Tx) = this
  override def ignoreOnTransform: Boolean = true
}
@data object callsTo {
  def apply(func: Exp[_]): Seq[Trace] = metadata[FunctionCalls](func).map(_.calls).getOrElse(Nil)
  def update(func: Exp[_], calls: Seq[Trace]): Unit = metadata.add(func, FunctionCalls(calls))
  def add(func: Exp[_], call: Trace): Unit = { callsTo(func) = call +: callsTo(func) }
}

case class FunctionInstances(n: Int) extends Metadata[FunctionInstances] {
  def mirror(f:Tx) = this
}
@data object funcInstances {
  def apply(func: Exp[_]): Int = metadata[FunctionInstances](func).map(_.n).getOrElse(1)
  def update(func: Exp[_], n: Int): Unit = metadata.add(func, FunctionInstances(n))
}

case class FunctionDispatch(map: Map[Seq[Ctrl],Int]) extends Metadata[FunctionDispatch] {
  def mirror(f:Tx) = this
}
@data object funcDispatch {
  def getOrCreate(call: Exp[_]): Map[Seq[Ctrl],Int] = {
    metadata[FunctionDispatch](call).map(_.map).getOrElse(Map.empty)
  }

  def apply(call: Trace): Int = metadata[FunctionDispatch](call.node).map(_.map).map(_.apply(call.trace)).getOrElse{
    throw new Exception(s"Function call ${str(call.node)}, ${call.trace} had no dispatch information")
  }
  def update(call: Trace, id: Int): Unit = {
    val prev = getOrCreate(call.node)
    val modified = prev + (call.trace -> id)
    metadata.add(call.node, FunctionDispatch(modified))
  }
}

case class FunctionModule(isHw: Boolean) extends Metadata[FunctionModule] { def mirror(f:Tx) = this }

@data object isHWModule {
  def apply(func: Exp[_]): Boolean = metadata[FunctionModule](func).exists(_.isHw)
  def update(func: Exp[_], isHw: Boolean): Unit = metadata.add(func, FunctionModule(isHw))
}