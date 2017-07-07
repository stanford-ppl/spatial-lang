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
    with IArray {

  override protected def interpretNode(lhs: Sym[_], rhs: Op[_]): Unit = {

    super.interpretNode(lhs, rhs)

    if (SpatialConfig.debug) {
      println()
      println(lhs, rhs)
      debug
    }

  }

}
