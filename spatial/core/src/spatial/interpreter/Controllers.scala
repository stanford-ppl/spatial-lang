package spatial.interpreter

import spatial.SpatialConfig
import argon.core._
import spatial.nodes._
import argon.interpreter.{Interpreter => AInterpreter}

trait Controllers extends AInterpreter {

  override def matchNode(lhs: Sym[_])  = super.matchNode(lhs).orElse {
    case Forever() =>
      Stream.continually(())

    case Switch(body, selects, cases) =>
      interpretBlock(body)
      val i = selects.map(eval[Boolean]).indexOf(true)
      interpretBlock(eval[Block[_]](cases(i)))

    case SwitchCase(b) =>
      b

    case UnrolledForeach(en, cchain, func, iters, valids) =>
      null

    case UnitPipe(ens, func) =>
      val en = ens.forall(eval[Boolean])
      if (en) {
        interpretBlock(func)
      }

    case Hwblock(block, isForever) =>
      if (isForever)
        while (!Config.forceExit()) 
          interpretBlock(block)        
      else
          interpretBlock(block)        
  }

}
