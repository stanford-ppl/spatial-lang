package spatial.interpreter

import sys.process._
import argon.interpreter.{Interpreter => AInterpreter}
import argon.nodes._
import spatial.nodes._
import argon.core._
import spatial.SpatialConfig

trait Interpreter
    extends AInterpreter
    with Controllers
    with FileIOs
    with Debuggings
    with HostTransfers
    with Regs
    with Strings
    with FixPts
    with FltPts
    with Arrays
    with Streams
    with Structs
    with SRAMs
    with Booleans
    with Counters
{

  override protected def interpretNode(lhs: Sym[_], rhs: Op[_]): Unit = {
    if (Config.debug) {
      println()
      println()
      println(lhs.ctx)
      val line = lhs.ctx.lineContent.getOrElse("")
      /*
      val colored_ar = ((s"echo $s" #| "pygmentize -l scala ").!!).split("\n")
      val colored =
        if (colored_ar.isEmpty)
          ""
        else
          colored_ar(0)
      println(colored)
      */
      val methodName = lhs.ctx.methodName
      val index = line.indexOfSlice(methodName)
      val highlighted =
        if (index == -1)
          line
        else
          line.take(index) + Console.CYAN_B + methodName + Console.RESET + line.drop(index + methodName.length)
      println(highlighted)
      println(s"${Console.CYAN}$rhs${Console.RESET} -> $lhs")
    }

    super.interpretNode(lhs, rhs)

    if (Config.debug) {
      debug
      println()
      if (Streams.streamsIn.size > 0 || Streams.streamsOut.size > 0) {
        println(s"[${Console.BLUE}input streams size${Console.RESET}]")
        Streams.streamsIn.foreach { case (k, s) => println(k + ": " + s.size) }
        Streams.streamsOut.foreach { case (k, s) => println(k + ": " + s.size) } 
      }
    }
  }

}
