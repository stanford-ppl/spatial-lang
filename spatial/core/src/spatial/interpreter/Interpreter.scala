package spatial.interpreter

import argon.interpreter.{Interpreter => AInterpreter}
import argon.nodes._
import spatial.nodes._
import argon.core._
import spatial.SpatialConfig

trait Interpreter
    extends AInterpreter
    with Controller
    with FileIO
    with Debugging
    with HostTransfer
    with Reg
    with IString
    with FixPt
    with IArray
    with IStream
{

  override protected def interpretNode(lhs: Sym[_], rhs: Op[_]): Unit = {

    super.interpretNode(lhs, rhs)

    if (SpatialConfig.debug) {
      println()
      println(lhs, rhs)
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
