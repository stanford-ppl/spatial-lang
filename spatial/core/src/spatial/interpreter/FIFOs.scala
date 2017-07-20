package spatial.interpreter

import argon.core._
import spatial.nodes._
import argon.interpreter.{Interpreter => AInterpreter}

trait FIFOs extends AInterpreter {

  class IFIFO(val size: Int, val v: collection.mutable.Queue[Any]) {
    def enq(x: Any) = {
      if (v.size == size) 
        throw new Exception("Queue reached size limit of " + size)
      v.enqueue(x)
    }
    def deq() =
      v.dequeue()

    override def toString() = {
      val vs = AInterpreter.stringify(v)
      s"FIFO($size, $vs)"
    }
      

  }

  object EFIFO {
    def unapply(x: Exp[_]) = Some(eval[IFIFO](x))
  }

  override def matchNode(lhs: Sym[_])  = super.matchNode(lhs).orElse {

    case FIFONew(EInt(size)) =>
      variables.get(lhs).getOrElse(new IFIFO(size, new collection.mutable.Queue[Any]()))

    case ParFIFOEnq(EFIFO(fifo), SeqE(data), SeqEB(ens)) =>
      ens.zip(data).foreach { case (en, v) =>
        if (en)
          fifo.enq(v)
      }

    case FIFODeq(EFIFO(fifo), EBoolean(en)) =>
      if (en)
        if (!fifo.v.isEmpty)
          fifo.deq()
        else
          null

    case FIFOPeek(EFIFO(fifo)) =>
        if (!fifo.v.isEmpty)
          fifo.v.head
        else
          null
      
    case FIFOEmpty(EFIFO(fifo)) =>
      fifo.v.isEmpty
  }

}


