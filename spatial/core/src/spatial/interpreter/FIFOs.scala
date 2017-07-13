package spatial.interpreter

import argon.core._
import spatial.nodes._
import argon.interpreter.{Interpreter => AInterpreter}

trait FIFOs extends AInterpreter {

  case class FIFO(size: Int, v: collection.mutable.Queue[Any]) {
    def enq(x: Any) = {
      if (v.size == size) 
        throw new Exception("Queue reached size limit of " + size)
      v.enqueue(x)
    }
    def deq() =
      v.dequeue()
  }

  override def matchNode(lhs: Sym[_])  = super.matchNode(lhs).orElse {

    case FIFONew(EInt(size)) =>
      variables.get(lhs).getOrElse(FIFO(size, new collection.mutable.Queue[Any]()))

    case ParFIFOEnq(fifo, iters, bound) =>
      println(iters.map(eval[Any]))

  }

}


