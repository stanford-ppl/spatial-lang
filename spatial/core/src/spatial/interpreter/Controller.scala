package spatial.interpreter

import spatial.SpatialConfig
import argon.core._
import spatial.nodes._
import argon.interpreter.{Interpreter => AInterpreter}

trait Controller extends AInterpreter {

  override def matchNode  = super.matchNode.orElse {
    case Forever() =>
      Stream.continually(())

    case Hwblock(block, isForever) =>
      if (isForever)
        while (SpatialConfig.loopInterpreter) {
          if (SpatialConfig.debug) {
            println("Press a key to continue the loop (q to quit)")
            SpatialConfig.loopInterpreter = io.StdIn.readLine() != "q"
          }
          if (SpatialConfig.loopInterpreter)
            interpretBlock(block)
        }
      else
          interpretBlock(block)        
  }

}
