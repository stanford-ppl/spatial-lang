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

  object EFIFO {
    def unapply(x: Exp[_]) = Some(eval[FIFO](x))
  }

  override def matchNode(lhs: Sym[_])  = super.matchNode(lhs).orElse {

    case FIFONew(EInt(size)) =>
      variables.get(lhs).getOrElse(FIFO(size, new collection.mutable.Queue[Any]()))

    case ParFIFOEnq(EFIFO(fifo), SeqE(data), SeqEB(ens)) =>
      ens.zip(data).foreach { case (en, v) =>
        if (en)
          fifo.enq(v)
      }

    case FIFODeq(EFIFO(fifo), EBoolean(en)) =>
      if (en)
        fifo.deq()

    case FIFOEmpty(EFIFO(fifo)) =>
      fifo.v.isEmpty
  }

}


