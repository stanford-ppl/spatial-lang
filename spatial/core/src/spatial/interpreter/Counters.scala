package spatial.interpreter

import argon.core._
import argon.nodes._
import spatial.nodes._
import argon.interpreter.{Interpreter => AInterpreter}


trait Counters extends AInterpreter {


  override def matchNode(lhs: Sym[_])  = super.matchNode(lhs).orElse {
    case CounterNew(EInt(from), EInt(to), EInt(by), param) =>
      (from, to, by, param)
      //      System.exit(0)

    case CounterChainNew(lc) =>
      lc

  }

}


