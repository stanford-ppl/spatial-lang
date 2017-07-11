package spatial.interpreter

import argon.interpreter.{Interpreter => AInterpreter}
import argon.nodes._
import spatial.nodes._
import argon.core._
import spatial.SpatialConfig

object Interpreter {

  var closed = false

}

trait Interpreter
    extends AInterpreter
    with Controller
    with FileIO
    with Debugging
    with HostTransfer
    with Reg
    with IString
    with FixPt
    with FltPt
    with IArray
    with IStream
    with IStruct
    with ISRAM
    with IBoolean
{

  override def eval[A](x: Any) =
    if (Interpreter.closed)
      (null.asInstanceOf[A])
    else
      super.eval[A](x)

  override protected def interpretNode(lhs: Sym[_], rhs: Op[_]): Unit = {
    if (!Interpreter.closed) {
      if (SpatialConfig.debug) {      
        println()
        println(lhs, rhs)
      }

      super.interpretNode(lhs, rhs)

      if (SpatialConfig.debug) {
        debug
        println()
        if (IStream.streamsIn.size > 0) {
          println("[input streams size]")
          IStream.streamsIn.foreach { case (k, s) => println(k + ": " + s.size) }
        }
        if (IStream.streamsOut.size > 0) {
          println("[ouput streams size]")
          IStream.streamsOut.foreach { case (k, s) => println(k + ": " + s.size) }      }
      }
    }
  }

}
