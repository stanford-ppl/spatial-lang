package spatial.interpreter

import argon.core._
import argon.nodes._
import spatial.nodes._
import argon.interpreter.{Interpreter => AInterpreter}
import java.util.concurrent.{ LinkedBlockingQueue => Queue }
import java.util.concurrent.TimeUnit
import spatial.targets.Bus

object Streams {
  var streamsIn = Map[Bus, Queue[Any]]()
  var streamsOut = Map[Bus, Queue[Any]]()

  def addStreamIn(bus: Bus) =
    streamsIn += ((bus, new Queue[Any]()))

  def addStreamOut(bus: Bus) =
    streamsOut += ((bus, new Queue[Any]()))

  def readStream(q: Queue[Any]): Any = {
    val v = q.poll(1000, TimeUnit.MILLISECONDS)
    if (v == null)
      throw new Exception("attempt to read empty stream> " + q)
    v
  }
}

trait Streams extends AInterpreter {


  override def matchNode(lhs: Sym[_])  = super.matchNode(lhs).orElse {

    case StreamInNew(bus) =>
      if (!Streams.streamsIn.contains(bus))
        Streams.addStreamIn(bus)

      Streams.streamsIn(bus)

    case StreamOutNew(bus) =>
      if (!Streams.streamsOut.contains(bus))
        Streams.addStreamOut(bus)

      Streams.streamsOut(bus)
      

    case StreamRead(a: Sym[_], EBoolean(en)) =>
      if (en) {
        val q = eval[Queue[Any]](a)
        val v = Streams.readStream(q)
        v
      }


    case StreamWrite(a, EAny(b), EBoolean(en)) =>
      if (en) {

        val q = eval[Queue[Any]](a)

        q.put(b)

      }

    case ParStreamRead(strm, ens) =>
      val q = eval[Queue[Any]](strm)
      val vs = ens.map(eval[Boolean]).map(x => {
        if (x)
          Streams.readStream(q)
        else
          null
      })
      vs
      

    case ParStreamWrite(strm, data, ens) =>
      val q = eval[Queue[Any]](strm)       
      ens.indices.map(i => {
        if (eval[Boolean](ens(i)))
          q.put(eval[Any](data(i)))        
      })
      
  }

}


